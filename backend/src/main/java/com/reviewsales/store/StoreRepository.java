package com.reviewsales.store;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByOwnerIdOrderByIdAsc(Long ownerId);

    List<Store> findByPlaceIdIsNotNull();
}
