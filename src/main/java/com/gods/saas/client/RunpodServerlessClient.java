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
import java.util.Set;
import org.springframework.web.client.RestClientException;
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

        String url = String.format("%s/%s/run",
                properties.getServerlessApiBaseUrl(),
                properties.getEndpointId());

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

        long deadline = System.nanoTime()
                + Math.max(1, properties.getServerlessWaitMillis()) * 1_000_000L;
        Map<String, Object> body;
        try {
            body = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
        } catch (RestClientException error) {
            // A timeout may occur AFTER RunPod accepts the job. Never resubmit here.
            throw new UnconfirmedJobException(null, error);
        }
        String jobId = body == null || body.get("id") == null
                ? null : String.valueOf(body.get("id"));
        if (body == null || jobId == null || jobId.isBlank()) {
            throw new UnconfirmedJobException(jobId, null);
        }
        while (true) {
            String status = String.valueOf(body.getOrDefault("status", ""));
            if ("COMPLETED".equalsIgnoreCase(status)) break;
            if (Set.of("FAILED", "CANCELLED", "TIMED_OUT").contains(status.toUpperCase())) {
                throw new IllegalStateException("RunPod terminó el trabajo " + jobId
                        + " con estado " + status);
            }
            if (!Set.of("IN_QUEUE", "IN_PROGRESS").contains(status.toUpperCase())
                    || System.nanoTime() >= deadline) {
                throw new UnconfirmedJobException(jobId, null);
            }
            try {
                long remaining = Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
                Thread.sleep(Math.min(Math.max(1, properties.getHealthPollIntervalMillis()), remaining));
                body = restTemplate.exchange(
                        properties.getServerlessApiBaseUrl() + "/" + properties.getEndpointId()
                                + "/status/" + jobId,
                        HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
                if (body == null) throw new UnconfirmedJobException(jobId, null);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new UnconfirmedJobException(jobId, error);
            } catch (RestClientException error) {
                // Status requests are safe to retry; POST /run is not.
                if (System.nanoTime() >= deadline) {
                    throw new UnconfirmedJobException(jobId, error);
                }
            }
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

    public static class UnconfirmedJobException extends RuntimeException {
        private final String jobId;

        public UnconfirmedJobException(String jobId, Throwable cause) {
            super("No se pudo confirmar el resultado de RunPod. No reintentar la generación; verificar trabajo "
                    + (jobId == null ? "sin identificador confirmado" : jobId), cause);
            this.jobId = jobId;
        }

        public String getJobId() { return jobId; }
    }

    public record Result(String providerJobId, GenerarImagenResponse response) {
    }
}