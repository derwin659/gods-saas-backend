package com.gods.saas.socket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventoSocketPublisherLog implements EventoSocketPublisher {

    @Override
    public void publicar(EventoSocket<?> evento) {
        log.info("📡 EVENTO SOCKET → {}", evento);
    }
}

