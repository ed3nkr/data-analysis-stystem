package com.reviewsales.sales;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sales_record")
public class SalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "menu_name", nullable = false)
    private String menuName;

    /** Asia/Seoul 현지 시각 */
    @Column(name = "sold_at", nullable = false)
    private LocalDateTime soldAt;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amount;

    protected SalesRecord() {
    }

    public SalesRecord(Long storeId, Long jobId, Long menuId, String menuName, LocalDateTime soldAt,
                       int quantity, long amount) {
        this.storeId = storeId;
        this.jobId = jobId;
        this.menuId = menuId;
        this.menuName = menuName;
        this.soldAt = soldAt;
        this.quantity = quantity;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getMenuId() {
        return menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public LocalDateTime getSoldAt() {
        return soldAt;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getAmount() {
        return amount;
    }
}
