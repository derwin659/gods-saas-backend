package com.gods.saas.service.impl;

import com.gods.saas.client.IaIlustrativaClient;
import com.gods.saas.client.RunpodServerlessClient;
import com.gods.saas.config.RunpodProperties;
import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class IllustrativeGenerationService {

    private final RunpodProperties properties;
    private final RunpodServerlessClient serverlessClient;
    private final IaIlustrativaClient podClient;
    private final AiPodOrchestratorService podOrchestrator;
    private final AiIllustrativeAssetService assetService;

    public Result generate(GenerarImagenRequest request, Long tenantId) {
        if ("SERVERLESS".equalsIgnoreCase(properties.getMode())) {
            AiIllustrativeAssetService.PreparedRequest prepared = assetService.prepare(tenantId, request);
            boolean confirmed = true;
            try {
                RunpodServerlessClient.Result result = serverlessClient.runSync(prepared.request(), tenantId);
                return new Result(result.response(), result.providerJobId(), "RUNPOD_SERVERLESS");
            } catch (RunpodServerlessClient.UnconfirmedJobException error) {
                confirmed = false;
                log.warn("RunPod sin resultado confirmado: job={}, sesion={}, recursos pendientes={}", error.getJobId(), request.getSesionId(), prepared.publicIds());
                throw error;
            } finally {
                if (confirmed) assetService.cleanup(prepared);
            }
        }

        podOrchestrator.ensurePodReady();
        podOrchestrator.onRequestStart();
        try {
            return new Result(podClient.generarImagen(request), null, "RUNPOD_POD");
        } finally {
            podOrchestrator.onRequestEnd();
        }
    }

    public record Result(GenerarImagenResponse response, String providerJobId, String provider) {
    }
}