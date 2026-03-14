package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PantallaSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void enviarEventoPantalla(String pantallaId, Object evento) {
        messagingTemplate.convertAndSend(
                "/topic/tv/" + pantallaId,
                evento
        );
    }
}

