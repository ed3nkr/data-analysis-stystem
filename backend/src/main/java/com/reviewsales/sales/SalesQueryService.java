package com.reviewsales.sales;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.store.StoreAccess;

@Service
public class SalesQueryService {

    private final StoreAccess storeAccess;
    private final SalesRecordRepository repository;

    public SalesQueryService(StoreAccess storeAccess, SalesRecordRepository repository) {
        this.storeAccess = storeAccess;
        this.repository = repository;
    }

    /** from~to (양끝 포함) 기간의 일별 또는 메뉴별 수량·금액 합계. 기간이 없으면 전체. */
    @Transactional(readOnly = true)
    public SalesDtos.Summary summary(Long ownerId, Long storeId, LocalDate from, LocalDate to,
                                     SalesDtos.GroupBy groupBy) {
        storeAccess.getOwned(storeId, ownerId);
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(9999, 12, 30);
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "to 는 from 보다 빠를 수 없습니다.");
        }
        var fromTs = start.atStartOfDay();
        var toTs = end.plusDays(1).atStartOfDay();

        List<?> items;
        long totalQty = 0;
        long totalAmount = 0;
        if (groupBy == SalesDtos.GroupBy.MENU) {
            List<SalesDtos.MenuSummary> list = repository.sumByMenu(storeId, fromTs, toTs).stream()
                    .map(r -> new SalesDtos.MenuSummary(r[0] == null ? null : ((Number) r[0]).longValue(),
                            (String) r[1], r[0] != null, ((Number) r[2]).longValue(), ((Number) r[3]).longValue()))
                    .toList();
            for (var m : list) {
                totalQty += m.quantity();
                totalAmount += m.amount();
            }
            items = list;
        } else {
            List<SalesDtos.DaySummary> list = repository.sumByDay(storeId, fromTs, toTs).stream()
                    .map(r -> new SalesDtos.DaySummary(toLocalDate(r[0]), ((Number) r[1]).longValue(),
                            ((Number) r[2]).longValue()))
                    .toList();
            for (var d : list) {
                totalQty += d.quantity();
                totalAmount += d.amount();
            }
            items = list;
        }
        return new SalesDtos.Summary(from, to, groupBy, totalQty, totalAmount, items);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) {
            return d;
        }
        return ((Date) o).toLocalDate();
    }
}
