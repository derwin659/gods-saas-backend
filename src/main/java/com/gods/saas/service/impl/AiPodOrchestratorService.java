package com.gods.saas.service.impl;

import com.gods.saas.client.RunpodClient;
import com.gods.saas.config.IaIlustrativaProperties;
import com.gods.saas.config.RunpodProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPodOrchestratorService {

    private final RunpodClient runpodClient;
    private final RunpodProperties runpodProperties;
    private final IaIlustrativaProperties iaIlustrativaProperties;
    private final RestTemplate restTemplate;

    private final Object lock = new Object();
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    @Getter
    private volatile Instant lastUsedAt = Instant.now();

    public void ensurePodReady() {
        markUsage();

        if (isHealthOk()) {
            return;
        }

        synchronized (lock) {
            if (isHealthOk()) {
                return;
            }

            if (!starting.get()) {
                starting.set(true);
                try {
                    log.info("FastAPI no responde health. Iniciando pod RunPod...");
                    runpodClient.startPod();
                } finally {
                    starting.set(false);
                }
            }

            waitUntilHealthy();
        }
    }

    public void onRequestStart() {
        activeRequests.incrementAndGet();
        markUsage();
    }

    public void onRequestEnd() {
        activeRequests.updateAndGet(v -> Math.max(0, v - 1));
        markUsage();
    }

    public void markUsage() {
        lastUsedAt = Instant.now();
    }

    public boolean isHealthOk() {
        try {
            String url = iaIlustrativaProperties.getUrl() + iaIlustrativaProperties.getHealthPath();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public void waitUntilHealthy() {
        long timeoutMillis = runpodProperties.getHealthTimeoutSeconds() * 1000L;
        long start = System.currentTimeMillis();

        while ((System.currentTimeMillis() - start) < timeoutMillis) {
            if (isHealthOk()) {
                log.info("FastAPI health OK");
                return;
            }

            try {
                Thread.sleep(runpodProperties.getHealthPollIntervalMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrumpido esperando que la IA ilustrativa esté lista");
            }
        }

        throw new RuntimeException("La IA ilustrativa no respondió health dentro del tiempo esperado");
    }

    @Scheduled(fixedDelay = 120000)
    public void stopIfIdle() {
        if (!runpodProperties.isEnabled()) {
            return;
        }

        if (activeRequests.get() > 0) {
            return;
        }

        long idleMillis = Instant.now().toEpochMilli() - lastUsedAt.toEpochMilli();
        long limitMillis = runpodProperties.getInactivityMinutes() * 60_000L;

        if (idleMillis >= limitMillis) {
            try {
                log.info("Pod inactivo por {} ms. Deteniendo RunPod...", idleMillis);
                runpodClient.stopPod();
                lastUsedAt = Instant.now();
            } catch (Exception e) {
                log.error("No se pudo detener el pod por inactividad", e);
            }
        }
    }
}