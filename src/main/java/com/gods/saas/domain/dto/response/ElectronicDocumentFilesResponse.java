package com.gods.saas.domain.dto.response;

public record ElectronicDocumentFilesResponse(
        Long documentId,
        String series,
        String sequence,
        String pdfBase64,
        String xmlBase64,
        String cdrBase64,
        String documentUrl
) {}
