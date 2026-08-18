package com.gods.saas.domain.dto.request;

import com.gods.saas.invoicing.ElectronicDocumentType;

public record IssueElectronicDocumentRequest(
        Long branchId,
        ElectronicDocumentType documentType,
        String receiverDocumentType,
        String receiverDocumentNumber,
        String receiverName,
        String receiverAddress,
        String receiverEmail
) {}
