package com.reviewsales.sales;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    boolean existsByMenuId(Long menuId);

    @Modifying
    @Query("delete from SalesRecord s where s.storeId = :storeId and s.soldAt >= :from and s.soldAt < :to")
    int deleteInRange(@Param("storeId") Long storeId, @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    @Query(value = """
            select cast(s.sold_at as date) as day, sum(s.quantity) as quantity, sum(s.amount) as amount
            from sales_record s
            where s.store_id = :storeId and s.sold_at >= :from and s.sold_at < :to
            group by cast(s.sold_at as date)
            order by day
            """, nativeQuery = true)
    List<Object[]> sumByDay(@Param("storeId") Long storeId, @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to);

    @Query(value = """
            select s.menu_id, coalesce(m.pos_name, s.menu_name) as name,
                   sum(s.quantity) as quantity, sum(s.amount) as amount
            from sales_record s
            left join menu m on m.id = s.menu_id
            where s.store_id = :storeId and s.sold_at >= :from and s.sold_at < :to
            group by s.menu_id, coalesce(m.pos_name, s.menu_name)
            order by amount desc
            """, nativeQuery = true)
    List<Object[]> sumByMenu(@Param("storeId") Long storeId, @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);
}
