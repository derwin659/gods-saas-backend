package com.gods.saas.domain.repository;

import com.gods.saas.domain.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findTopByPhoneAndTenantIdAndUsedIsFalseOrderByCreatedAtDesc(
            String phone,
            Long tenantId
    );

    Optional<OtpCode> findTopByTenantIdAndPhoneAndUsedIsFalseOrderByCreatedAtDesc(Long tenantId, String phone);


}
