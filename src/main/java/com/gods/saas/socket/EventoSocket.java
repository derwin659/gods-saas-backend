package com.gods.saas.socket;

import com.gods.saas.utils.TipoEventoSocket;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoSocket<T> {

    private TipoEventoSocket tipo;
    private String sesionId;
    private T payload;
    private LocalDateTime timestamp;

    public static <T> EventoSocket<T> of(
            TipoEventoSocket tipo,
            String sesionId,
            T payload
    ) {
        return EventoSocket.<T>builder()
                .tipo(tipo)
                .sesionId(sesionId)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

