package com.reviewsales.job;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.auth.CurrentOwner;
import com.reviewsales.common.ApiResponse;
import com.reviewsales.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Job")
@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "작업 상태 (COMPLETED/FAILED 까지 폴링)")
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<JobDtos.JobResponse> get(@CurrentOwner Long ownerId, @PathVariable Long jobId) {
        return ApiResponse.ok(jobService.get(ownerId, jobId));
    }

    @Operation(summary = "매장 작업 이력")
    @GetMapping("/stores/{storeId}/jobs")
    public ApiResponse<PageResponse<JobDtos.JobResponse>> list(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                               @RequestParam(required = false) JobType type,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(jobService.list(ownerId, storeId, type, page, size));
    }
}
