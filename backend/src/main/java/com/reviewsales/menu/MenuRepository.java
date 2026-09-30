package com.reviewsales.menu;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByStoreIdOrderByIdAsc(Long storeId);

    Optional<Menu> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByStoreIdAndNormalizedName(Long storeId, String normalizedName);

    long countByStoreId(Long storeId);
}
