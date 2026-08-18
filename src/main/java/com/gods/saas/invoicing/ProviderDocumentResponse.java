package com.gods.saas.invoicing;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderDocumentResponse(
        ElectronicDocumentStatus status,
        String providerStatus,
        String series,
        String sequence,
        String sunatResponseCode,
        String sunatDescription,
        String errors,
        String pdfBase64,
        String xmlBase64,
        String cdrBase64,
        String documentUrl,
        JsonNode rawResponse
) {}
