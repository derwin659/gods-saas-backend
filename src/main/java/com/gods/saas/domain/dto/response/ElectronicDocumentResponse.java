package com.gods.saas.domain.dto.response;

import com.gods.saas.invoicing.ElectronicDocumentStatus;
import com.gods.saas.invoicing.ElectronicDocumentType;
import java.time.LocalDateTime;

public record ElectronicDocumentResponse(
        Long id, Long saleId, Long branchId,
        ElectronicDocumentType documentType,
        ElectronicDocumentStatus status,
        String series, String sequence,
        String providerStatus, String sunatResponseCode,
        String sunatDescription, String errorMessage,
        String documentUrl, int attemptCount,
        LocalDateTime lastAttemptAt, LocalDateTime acceptedAt,
        LocalDateTime createdAt
) {}
