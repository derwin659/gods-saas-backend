package com.gods.saas.domain.dto.response;

public record ElectronicInvoicingAccessResponse(
        boolean available,
        boolean configured,
        boolean enabled,
        String message
) {}
