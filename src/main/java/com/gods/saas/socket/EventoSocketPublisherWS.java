package com.gods.saas.socket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Primary
@Component
@RequiredArgsConstructor
public class EventoSocketPublisherWS implements EventoSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publicar(EventoSocket<?> evento) {

        String destino = "/topic/pantalla/" + evento.getSesionId();

        messagingTemplate.convertAndSend(destino, evento);
    }
}

