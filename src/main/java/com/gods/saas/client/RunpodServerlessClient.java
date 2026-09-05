package com.gods.saas.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.config.RunpodProperties;
import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RunpodServerlessClient {

    private final RestTemplate restTemplate;
    private final RunpodProperties properties;
    private final ObjectMapper objectMapper;

    public Result runSync(GenerarImagenRequest request, Long tenantId) {
        if (properties.getEndpointId() == null || properties.getEndpointId().isBlank()) {
            throw new IllegalStateException("RUNPOD_ENDPOINT_ID es obligatorio en modo SERVERLESS");
        }
        if (tenantId == null) {
            throw new IllegalStateException("El tenant es obligatorio en modo SERVERLESS");
        }

        String url = String.format("%s/%s/runsync?wait=%d",
                properties.getServerlessApiBaseUrl(),
                properties.getEndpointId(),
                properties.getServerlessWaitMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, Object> input = objectMapper.convertValue(request, Map.class);
        input.put("storage", Map.of("tenantId", tenantId));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of(
                        "input", input,
                        "policy", Map.of(
                                "executionTimeout", properties.getServerlessExecutionTimeoutMillis(),
                                "ttl", properties.getServerlessTtlMillis()
                        )
                ),
                headers
        );

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) throw new IllegalStateException("RunPod Serverless devolvió una respuesta vacía");

        String status = String.valueOf(body.getOrDefault("status", ""));
        String jobId = body.get("id") == null ? null : String.valueOf(body.get("id"));
        if (!"COMPLETED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("RunPod Serverless no completó el trabajo " + jobId
                    + " (estado=" + status + ", error=" + body.get("error") + ")");
        }

        Object output = body.get("output");
        if (output == null) throw new IllegalStateException("RunPod Serverless no devolvió output para " + jobId);

        Object unwrapped = unwrapOutput(output);
        if (unwrapped instanceof Map<?, ?> outputMap
                && Boolean.FALSE.equals(outputMap.get("success"))) {
            throw new IllegalStateException("El worker rechazó el trabajo " + jobId
                    + ": " + outputMap.get("message"));
        }

        GenerarImagenResponse generation = objectMapper.convertValue(unwrapped, GenerarImagenResponse.class);
        return new Result(jobId, generation);
    }

    private Object unwrapOutput(Object output) {
        if (output instanceof Map<?, ?> map && map.containsKey("output")) return map.get("output");
        return output;
    }

    public record Result(String providerJobId, GenerarImagenResponse response) {
    }
}