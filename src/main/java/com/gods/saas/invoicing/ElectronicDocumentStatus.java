package com.gods.saas.invoicing;

public enum ElectronicDocumentStatus {
    DRAFT, PENDING, PROCESSING, ACCEPTED, ACCEPTED_WITH_OBSERVATIONS,
    REJECTED, VOID_PENDING, VOIDED, ERROR;

    public static ElectronicDocumentStatus fromMifact(String value) {
        if (value == null || value.isBlank()) return ERROR;
        return switch (value.trim()) {
            case "101" -> PROCESSING;
            case "102" -> ACCEPTED;
            case "103" -> ACCEPTED_WITH_OBSERVATIONS;
            case "104" -> REJECTED;
            case "105" -> VOIDED;
            case "108" -> VOID_PENDING;
            default -> ERROR;
        };
    }
}
