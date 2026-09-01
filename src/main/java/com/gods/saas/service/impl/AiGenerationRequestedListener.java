package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AiGenerationRequestedListener {

    private final SesionIAService sesionIAService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AiGenerationRequestedEvent event) {
        sesionIAService.ejecutarGeneracionImagen(
                event.jobId(),
                event.sessionId(),
                event.request()
        );
    }
}