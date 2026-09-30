package com.reviewsales.sales;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.reviewsales.auth.CurrentOwner;
import com.reviewsales.common.ApiResponse;
import com.reviewsales.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Sales")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/sales")
public class SalesController {

    private final SalesUploadService uploadService;
    private final SalesQueryService queryService;

    public SalesController(SalesUploadService uploadService, SalesQueryService queryService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
    }

    @Operation(summary = "매출 CSV 업로드 (UTF-8/EUC-KR, 최대 10MB)")
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SalesDtos.UploadResult> upload(
            @CurrentOwner Long ownerId, @PathVariable Long storeId,
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "{\"soldAt\":\"열이름\",\"menuName\":\"열이름\",\"quantity\":\"열이름\",\"amount\":\"열이름\"}")
            @RequestParam(value = "columnMapping", required = false) String columnMapping,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace) {
        return ApiResponse.ok(uploadService.upload(ownerId, storeId, file, columnMapping, replace));
    }

    @Operation(summary = "매출 업로드 이력")
    @GetMapping("/uploads")
    public ApiResponse<PageResponse<SalesDtos.UploadHistory>> history(@CurrentOwner Long ownerId,
                                                                      @PathVariable Long storeId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(uploadService.history(ownerId, storeId, page, size));
    }

    @Operation(summary = "업로드 오류 행 목록")
    @GetMapping("/uploads/{uploadId}/errors")
    public ApiResponse<SalesDtos.UploadErrors> errors(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                      @PathVariable Long uploadId) {
        return ApiResponse.ok(uploadService.errors(ownerId, storeId, uploadId));
    }

    @Operation(summary = "매출 요약 (groupBy=DAY|MENU)")
    @GetMapping("/summary")
    public ApiResponse<SalesDtos.Summary> summary(
            @CurrentOwner Long ownerId, @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") SalesDtos.GroupBy groupBy) {
        return ApiResponse.ok(queryService.summary(ownerId, storeId, from, to, groupBy));
    }
}
