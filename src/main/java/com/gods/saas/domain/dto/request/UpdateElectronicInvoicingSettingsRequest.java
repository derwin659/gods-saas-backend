package com.gods.saas.domain.dto.request;

import java.math.BigDecimal;

public record UpdateElectronicInvoicingSettingsRequest(
        String fiscalRuc,
        String legalName,
        String commercialName,
        String fiscalAddress,
        String ubigeo,
        String salesPointCode,
        String annexCode,
        String invoiceSeries,
        String receiptSeries,
        String credentialAlias,
        BigDecimal igvRate,
        boolean enabled
) {}
