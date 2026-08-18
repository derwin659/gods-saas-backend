package com.gods.saas.invoicing;

import com.fasterxml.jackson.databind.JsonNode;

public interface ElectronicInvoiceProvider {
    ProviderDocumentResponse issue(JsonNode payload);
    ProviderDocumentResponse getStatus(JsonNode payload);
    ProviderDocumentResponse getDocument(JsonNode payload);
    ProviderDocumentResponse voidDocument(JsonNode payload);
}
