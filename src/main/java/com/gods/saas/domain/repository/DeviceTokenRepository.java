package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByTenant_IdAndCustomer_IdAndToken(Long tenantId, Long customerId, String token);

    Optional<DeviceToken> findByTenant_IdAndUser_IdAndToken(Long tenantId, Long userId, String token);

    List<DeviceToken> findByTenant_IdAndCustomer_IdAndActiveTrue(Long tenantId, Long customerId);

    List<DeviceToken> findByTenant_IdAndUser_IdAndActiveTrue(Long tenantId, Long userId);

    List<DeviceToken> findByToken(String token);
}