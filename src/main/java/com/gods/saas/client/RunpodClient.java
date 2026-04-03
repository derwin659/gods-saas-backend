package com.gods.saas.client;

import com.gods.saas.config.RunpodProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunpodClient {

    private final RestTemplate restTemplate;
    private final RunpodProperties runpodProperties;

    public void startPod() {
        if (!runpodProperties.isEnabled()) {
            return;
        }

        String url = String.format("%s/pods/%s/start",
                runpodProperties.getApiBaseUrl(),
                runpodProperties.getPodId());

        exchangePost(url);
        log.info("RunPod start solicitado para pod {}", runpodProperties.getPodId());
    }

    public void stopPod() {
        if (!runpodProperties.isEnabled()) {
            return;
        }

        String url = String.format("%s/pods/%s/stop",
                runpodProperties.getApiBaseUrl(),
                runpodProperties.getPodId());

        exchangePost(url);
        log.info("RunPod stop solicitado para pod {}", runpodProperties.getPodId());
    }

    public Map<String, Object> getPod() {
        if (!runpodProperties.isEnabled()) {
            return Map.of("enabled", false);
        }

        String url = String.format("%s/pods/%s",
                runpodProperties.getApiBaseUrl(),
                runpodProperties.getPodId());

        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return response.getBody() != null ? response.getBody() : new HashMap<>();
    }

    private void exchangePost(String url) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(runpodProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}