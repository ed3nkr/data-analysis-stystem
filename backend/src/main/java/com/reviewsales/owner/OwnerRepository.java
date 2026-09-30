package com.reviewsales.owner;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
