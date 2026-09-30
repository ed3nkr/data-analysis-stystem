package com.reviewsales.review;

import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.PageResponse;
import com.reviewsales.store.StoreAccess;

@Service
public class ReviewQueryService {

    private final StoreAccess storeAccess;
    private final ReviewRepository repository;

    public ReviewQueryService(StoreAccess storeAccess, ReviewRepository repository) {
        this.storeAccess = storeAccess;
        this.repository = repository;
    }

    /** 작성일(writtenAt) 기준 from~to (양끝 포함), 최신순 */
    @Transactional(readOnly = true)
    public PageResponse<ReviewDtos.ReviewResponse> list(Long ownerId, Long storeId, LocalDate from, LocalDate to,
                                                        int page, int size) {
        storeAccess.getOwned(storeId, ownerId);
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(9999, 12, 31);
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "to 는 from 보다 빠를 수 없습니다.");
        }
        var result = repository.findByStoreIdAndWrittenAtBetweenOrderByWrittenAtDescIdDesc(storeId, start, end,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(result, ReviewDtos.ReviewResponse::of);
    }
}
