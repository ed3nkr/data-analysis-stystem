package com.reviewsales.store;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoreCategory category;

    @Column(name = "place_id")
    private String placeId;

    @Column(name = "place_name")
    private String placeName;

    private String address;

    @Column(name = "last_collected_at")
    private OffsetDateTime lastCollectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Store() {
    }

    public Store(Long ownerId, String name, StoreCategory category) {
        this.ownerId = ownerId;
        this.name = name;
        this.category = category;
        this.createdAt = OffsetDateTime.now();
    }

    public void update(String name, StoreCategory category) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (category != null) {
            this.category = category;
        }
    }

    public void connectPlace(String placeId, String placeName, String address) {
        this.placeId = placeId;
        this.placeName = placeName;
        this.address = address;
    }

    public void markCollected(OffsetDateTime at) {
        this.lastCollectedAt = at;
    }

    public boolean isPlaceConnected() {
        return placeId != null;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public StoreCategory getCategory() {
        return category;
    }

    public String getPlaceId() {
        return placeId;
    }

    public String getPlaceName() {
        return placeName;
    }

    public String getAddress() {
        return address;
    }

    public OffsetDateTime getLastCollectedAt() {
        return lastCollectedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
