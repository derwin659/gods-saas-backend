package com.gods.saas.domain.dto.response;

import java.math.BigDecimal;

public record ElectronicInvoicingSettingsResponse(
        String fiscalRuc, String legalName, String commercialName,
        String fiscalAddress, String ubigeo, String salesPointCode,
        String annexCode, String invoiceSeries, String receiptSeries,
        long nextInvoiceNumber, long nextReceiptNumber,
        String credentialAlias, BigDecimal igvRate, boolean enabled,
        boolean credentialConfigured
) {}
