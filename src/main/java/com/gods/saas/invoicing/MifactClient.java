package com.gods.saas.invoicing;

import com.fasterxml.jackson.databind.JsonNode;
import com.gods.saas.config.MifactProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class MifactClient implements ElectronicInvoiceProvider {
    private final MifactProperties properties;
    private final RestTemplate restTemplate;

    public MifactClient(MifactProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    @Override public ProviderDocumentResponse issue(JsonNode payload) { return post("/SendInvoice", payload); }
    @Override public ProviderDocumentResponse getStatus(JsonNode payload) { return post("/GetEstatusInvoice", payload); }
    @Override public ProviderDocumentResponse getDocument(JsonNode payload) { return post("/GetInvoice", payload); }
    @Override public ProviderDocumentResponse voidDocument(JsonNode payload) { return post("/LowInvoice", payload); }

    private ProviderDocumentResponse post(String path, JsonNode payload) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("La integracion Mifact esta deshabilitada");
        }
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("El payload Mifact es obligatorio");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode response = restTemplate.postForObject(
                normalizedBaseUrl() + path,
                new HttpEntity<>(payload, headers),
                JsonNode.class);
        if (response == null) throw new IllegalStateException("Mifact respondio sin contenido");
        return map(response);
    }

    private String normalizedBaseUrl() {
        String value = properties.getBaseUrl();
        if (value == null || value.isBlank() || !value.startsWith("https://")) {
            throw new IllegalStateException("La URL de Mifact debe usar HTTPS");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private ProviderDocumentResponse map(JsonNode response) {
        String providerStatus = text(response, "estado_documento");
        return new ProviderDocumentResponse(
                ElectronicDocumentStatus.fromMifact(providerStatus), providerStatus,
                text(response, "serie_cpe"), text(response, "correlativo_cpe"),
                text(response, "sunat_responsecode"),
                firstText(response, "sunat_description", "sunat_note"),
                text(response, "errors"), text(response, "pdf_bytes"),
                text(response, "xml_enviado"), text(response, "cdr_sunat"),
                text(response, "url"), response);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
