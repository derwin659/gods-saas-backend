package com.gods.saas.invoicing;

public enum ElectronicDocumentType {
    INVOICE("01"), RECEIPT("03"), CREDIT_NOTE("07"), DEBIT_NOTE("08");

    private final String sunatCode;

    ElectronicDocumentType(String sunatCode) { this.sunatCode = sunatCode; }

    public String getSunatCode() { return sunatCode; }
}
