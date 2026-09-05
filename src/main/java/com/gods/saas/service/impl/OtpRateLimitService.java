package com.gods.saas.service.impl;

import com.gods.saas.domain.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OtpRateLimitService {

    private static final int MAX_PER_HOUR = 3;
    private static final int MAX_PER_DAY = 5;

    private final OtpCodeRepository otpCodeRepository;

    public void assertCanSend(Long tenantId, String phone, LocalDateTime now) {
        long hourly = otpCodeRepository.countByTenantIdAndPhoneAndCreatedAtGreaterThanEqual(
                tenantId,
                phone,
                now.minusHours(1)
        );
        if (hourly >= MAX_PER_HOUR) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Limite alcanzado: maximo 3 codigos por hora."
            );
        }

        long daily = otpCodeRepository.countByTenantIdAndPhoneAndCreatedAtGreaterThanEqual(
                tenantId,
                phone,
                now.minusDays(1)
        );
        if (daily >= MAX_PER_DAY) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Limite alcanzado: maximo 5 codigos por dia."
            );
        }
    }
}