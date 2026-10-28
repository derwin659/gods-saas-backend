package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.*;
import javax.net.ssl.SSLException;

/** Log only controlled metadata. Never exception messages, response bodies or stack traces. */
public final class WhatsappAssistantFailureDiagnostics {
    private static final Set<String> SAFE_CODES = Set.of("insufficient_quota", "invalid_api_key",
            "model_not_found", "rate_limit_exceeded", "permission_denied", "invalid_request_error",
            "unsupported_parameter", "invalid_value", "access_denied");
    private WhatsappAssistantFailureDiagnostics() {}

    public static String summarize(Throwable failure) {
        List<String> causes = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        String category = "UNCLASSIFIED";
        String status = "none";
        String code = "none";
        for (Throwable current = failure; current != null && seen.add(current) && causes.size() < 10; current = current.getCause()) {
            causes.add(current.getClass().getSimpleName());
            if (current instanceof RestClientResponseException response) {
                status = Integer.toString(response.getStatusCode().value());
                category = "HTTP";
                try {
                    var error = new ObjectMapper().readTree(response.getResponseBodyAsByteArray()).path("error");
                    String candidate = error.path("code").asText();
                    if (SAFE_CODES.contains(candidate)) code = candidate;
                    else if (SAFE_CODES.contains(error.path("type").asText())) code = error.path("type").asText();
                    else code = "unclassified";
                } catch (Exception ignored) { code = "unclassified"; }
            }
            if (current instanceof JsonProcessingException) category = "RESPONSE_PARSE";
            if (current instanceof SQLException) category = "DATABASE";
            if (current instanceof SocketTimeoutException || current instanceof java.net.http.HttpTimeoutException) category = "TIMEOUT";
            if (current instanceof UnknownHostException) category = "DNS";
            if (current instanceof SSLException) category = "TLS";
        }
        return "category=" + category + ", causes=" + String.join(">", causes) + ", httpStatus=" + status + ", providerCode=" + code;
    }
}
