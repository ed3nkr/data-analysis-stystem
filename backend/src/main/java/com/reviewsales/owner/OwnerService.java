package com.reviewsales.owner;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

@Service
public class OwnerService {

    private final OwnerRepository ownerRepository;

    public OwnerService(OwnerRepository ownerRepository) {
        this.ownerRepository = ownerRepository;
    }

    /** (provider, providerUserId) 로 owner 를 찾고, 없으면 만든다. */
    @Transactional
    public Owner findOrCreate(AuthProvider provider, String providerUserId, String email) {
        return ownerRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(owner -> {
                    owner.updateEmail(email);
                    return owner;
                })
                .orElseGet(() -> {
                    try {
                        return ownerRepository.saveAndFlush(new Owner(provider, providerUserId, email));
                    } catch (DataIntegrityViolationException e) {
                        // 동시 로그인으로 먼저 생성된 경우
                        return ownerRepository.findByProviderAndProviderUserId(provider, providerUserId)
                                .orElseThrow(() -> e);
                    }
                });
    }

    @Transactional(readOnly = true)
    public Owner get(Long ownerId) {
        return ownerRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
    }
}
