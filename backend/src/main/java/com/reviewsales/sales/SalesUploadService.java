package com.reviewsales.sales;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.PageResponse;
import com.reviewsales.common.UploadStorage;
import com.reviewsales.common.csv.CsvText;
import com.reviewsales.job.Job;
import com.reviewsales.job.JobRepository;
import com.reviewsales.job.JobService;
import com.reviewsales.job.JobStatus;
import com.reviewsales.job.JobType;
import com.reviewsales.menu.MenuMatcher;
import com.reviewsales.menu.MenuNameNormalizer;
import com.reviewsales.menu.MenuRepository;
import com.reviewsales.store.StoreAccess;

@Service
public class SalesUploadService {

    private static final Logger log = LoggerFactory.getLogger(SalesUploadService.class);
    static final int MAX_STORED_ERRORS = 1000;

    private final StoreAccess storeAccess;
    private final JobService jobService;
    private final JobRepository jobRepository;
    private final MenuRepository menuRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final UploadStorage uploadStorage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public SalesUploadService(StoreAccess storeAccess, JobService jobService, JobRepository jobRepository,
                              MenuRepository menuRepository, SalesRecordRepository salesRecordRepository,
                              UploadStorage uploadStorage, JdbcTemplate jdbc, TransactionTemplate tx,
                              ObjectMapper objectMapper) {
        this.storeAccess = storeAccess;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.menuRepository = menuRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.uploadStorage = uploadStorage;
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    public SalesDtos.UploadResult upload(Long ownerId, Long storeId, MultipartFile file, String columnMappingJson,
                                         boolean replace) {
        storeAccess.getOwned(storeId, ownerId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        UploadStorage.checkSize(file.getSize());
        Map<String, String> mapping = parseMapping(columnMappingJson);
        byte[] bytes = readBytes(file);

        // 1) job 생성 → 파일 저장
        Job job = jobService.create(storeId, JobType.SALES_UPLOAD);
        Long jobId = job.getId();
        try {
            String path = uploadStorage.save(storeId, jobId, file.getOriginalFilename(), bytes);
            jobService.update(jobId, j -> {
                j.setFilePath(path);
                j.start();
            });

            // 2) 파싱
            SalesCsvParser.Result parsed = SalesCsvParser.parse(CsvText.decode(bytes).text(), mapping);
            if (parsed.rows().isEmpty()) {
                List<SalesCsvParser.RowError> rowErrors = limit(parsed.errors());
                jobService.update(jobId, j -> j.failWithRows(parsed.errors().size(), rowErrors));
                throw new BusinessException(ErrorCode.CSV_EMPTY,
                        parsed.totalRows() == 0 ? "CSV 에 데이터 행이 없습니다." : "유효한 행이 없습니다. 오류 행을 확인하세요.",
                        Map.of("uploadId", jobId, "errorRows", parsed.errors().size()));
            }
            LocalDate start = parsed.rows().stream().map(r -> r.soldAt().toLocalDate()).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate end = parsed.rows().stream().map(r -> r.soldAt().toLocalDate()).max(Comparator.naturalOrder()).orElseThrow();

            // 3) 기간 중복 확인 + 저장 (한 트랜잭션)
            List<String> candidates = tx.execute(s -> store(storeId, jobId, parsed, start, end, replace));
            return new SalesDtos.UploadResult(jobId, start, end, parsed.totalRows(), parsed.rows().size(),
                    parsed.errors().size(), candidates);
        } catch (BusinessException e) {
            if (e.code() != ErrorCode.CSV_EMPTY) {
                jobService.fail(jobId, errorDetailFor(e));
            }
            throw e;
        } catch (RuntimeException e) {
            log.error("매출 업로드 실패 job={}", jobId, e);
            jobService.fail(jobId, List.of(new SalesCsvParser.RowError(null, "INTERNAL_ERROR", null)));
            throw e;
        }
    }

    private List<String> store(Long storeId, Long jobId, SalesCsvParser.Result parsed, LocalDate start,
                               LocalDate end, boolean replace) {
        List<Map<String, Object>> overlaps = findOverlaps(storeId, jobId, start, end);
        if (!overlaps.isEmpty()) {
            if (!replace) {
                throw new BusinessException(ErrorCode.SALES_PERIOD_OVERLAP,
                        ErrorCode.SALES_PERIOD_OVERLAP.defaultMessage(), Map.of("overlaps", overlaps));
            }
            int deleted = salesRecordRepository.deleteInRange(storeId, start.atStartOfDay(),
                    end.plusDays(1).atStartOfDay());
            log.info("replace=true: store={} {}~{} 기존 매출 {}건 삭제", storeId, start, end, deleted);
        }

        MenuMatcher matcher = new MenuMatcher(menuRepository.findByStoreIdOrderByIdAsc(storeId));
        Map<String, String> unmatched = new LinkedHashMap<>(); // 정규화 이름 → 처음 본 원문
        Map<String, Integer> unmatchedCount = new LinkedHashMap<>();
        List<Object[]> batch = new ArrayList<>(parsed.rows().size());
        for (SalesCsvParser.SalesRow r : parsed.rows()) {
            Long menuId = matcher.match(r.menuName());
            if (menuId == null) {
                String key = MenuNameNormalizer.normalize(r.menuName());
                unmatched.putIfAbsent(key, r.menuName());
                unmatchedCount.merge(key, 1, Integer::sum);
            }
            batch.add(new Object[]{storeId, jobId, menuId, r.menuName(), r.soldAt(),
                    r.quantity(), r.amount()});
        }
        jdbc.batchUpdate("insert into sales_record (store_id, job_id, menu_id, menu_name, sold_at, quantity, amount) "
                + "values (?, ?, ?, ?, ?, ?, ?)", batch);

        List<SalesCsvParser.RowError> stored = limit(parsed.errors());
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setPeriod(start, end);
        job.complete(parsed.rows().size(), parsed.errors().size(), stored);

        return unmatchedCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> unmatched.get(e.getKey()))
                .toList();
    }

    private List<Map<String, Object>> findOverlaps(Long storeId, Long jobId, LocalDate start, LocalDate end) {
        return jobRepository.findByStoreIdAndTypeAndStatus(storeId, JobType.SALES_UPLOAD, JobStatus.COMPLETED).stream()
                .filter(j -> !j.getId().equals(jobId) && j.getPeriodStart() != null)
                .filter(j -> !j.getPeriodStart().isAfter(end) && !j.getPeriodEnd().isBefore(start))
                .map(j -> Map.<String, Object>of("uploadId", j.getId(), "periodStart", j.getPeriodStart(),
                        "periodEnd", j.getPeriodEnd()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDtos.UploadHistory> history(Long ownerId, Long storeId, int page, int size) {
        storeAccess.getOwned(storeId, ownerId);
        var jobs = jobRepository.findByStoreIdAndTypeOrderByRequestedAtDesc(storeId, JobType.SALES_UPLOAD,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(jobs, SalesDtos.UploadHistory::of);
    }

    @Transactional(readOnly = true)
    public SalesDtos.UploadErrors errors(Long ownerId, Long storeId, Long uploadId) {
        storeAccess.getOwned(storeId, ownerId);
        Job job = jobRepository.findByIdAndStoreIdAndType(uploadId, storeId, JobType.SALES_UPLOAD)
                .orElseThrow(() -> new BusinessException(ErrorCode.UPLOAD_NOT_FOUND));
        List<SalesCsvParser.RowError> errors = job.getErrorDetail() == null ? List.of()
                : objectMapper.convertValue(job.getErrorDetail(), new TypeReference<List<SalesCsvParser.RowError>>() {
                });
        return new SalesDtos.UploadErrors(uploadId, job.getStatus(), job.getErrorCount(), errors.size(),
                job.getErrorCount() > errors.size(), errors);
    }

    private static List<SalesCsvParser.RowError> limit(List<SalesCsvParser.RowError> errors) {
        return new ArrayList<>(errors.size() > MAX_STORED_ERRORS ? errors.subList(0, MAX_STORED_ERRORS) : errors);
    }

    /** 업로드 job 의 error_detail 은 항상 [{rowNumber, reason, rawLine}] 형태로 둔다. */
    private List<SalesCsvParser.RowError> errorDetailFor(BusinessException e) {
        return List.of(new SalesCsvParser.RowError(null, e.code().name() + ": " + e.getMessage(), null));
    }

    private Map<String, String> parseMapping(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "columnMapping 은 {\"soldAt\":\"열이름\", ...} 형태의 JSON 이어야 합니다.");
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
