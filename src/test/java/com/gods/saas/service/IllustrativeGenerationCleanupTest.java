package com.gods.saas.service;

import com.gods.saas.client.*;
import com.gods.saas.config.RunpodProperties;
import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import com.gods.saas.service.impl.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IllustrativeGenerationCleanupTest {
    @Test void pendingWorkKeepsInputImages() {
        var props = new RunpodProperties();
        props.setMode("SERVERLESS");
        var client = mock(RunpodServerlessClient.class);
        var assets = mock(AiIllustrativeAssetService.class);
        var request = new GenerarImagenRequest();
        var prepared = new AiIllustrativeAssetService.PreparedRequest(request, List.of("input-1"));
        when(assets.prepare(1L, request)).thenReturn(prepared);
        when(client.runSync(request, 1L)).thenThrow(new RunpodServerlessClient.UnconfirmedJobException("job-1", null));
        var service = new IllustrativeGenerationService(props, client,
            mock(IaIlustrativaClient.class), mock(AiPodOrchestratorService.class), assets);
        assertThrows(RunpodServerlessClient.UnconfirmedJobException.class, () -> service.generate(request, 1L));
        verify(assets, never()).cleanup(prepared);
    }
    @Test void completedWorkCleansInputImages() {
        var props = new RunpodProperties();
        props.setMode("SERVERLESS");
        var client = mock(RunpodServerlessClient.class);
        var assets = mock(AiIllustrativeAssetService.class);
        var request = new GenerarImagenRequest();
        var prepared = new AiIllustrativeAssetService.PreparedRequest(request, List.of("input-1"));
        when(assets.prepare(1L, request)).thenReturn(prepared);
        when(client.runSync(request, 1L)).thenReturn(
            new RunpodServerlessClient.Result("job-1", new GenerarImagenResponse()));
        var service = new IllustrativeGenerationService(props, client,
            mock(IaIlustrativaClient.class), mock(AiPodOrchestratorService.class), assets);
        service.generate(request, 1L);
        verify(assets).cleanup(prepared);
    }
}