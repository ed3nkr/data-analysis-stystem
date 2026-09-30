package com.reviewsales.review;

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
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Review")
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
public class ReviewController {

    private final ReviewUploadService uploadService;
    private final ReviewQueryService queryService;

    public ReviewController(ReviewUploadService uploadService, ReviewQueryService queryService) {
        this.uploadService = uploadService;
        this.queryService = queryService;
    }

    @Operation(summary = "리뷰 파일 등록 (CSV: writtenAt, content, visitedAt, rating, author)")
    @PostMapping(value = "/reviews/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ReviewDtos.UploadResult> upload(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                       @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(uploadService.upload(ownerId, storeId, file));
    }

    @Operation(summary = "리뷰 원문 목록 (최신순, 분석 결과 필드는 아직 null)")
    @GetMapping("/reviews")
    public ApiResponse<PageResponse<ReviewDtos.ReviewResponse>> list(
            @CurrentOwner Long ownerId, @PathVariable Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queryService.list(ownerId, storeId, from, to, page, size));
    }
}
