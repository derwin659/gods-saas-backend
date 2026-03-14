package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class CorteDTO {

    /**
     * Ej: "id Fade"
     */
    private String nombre;

    /**
     * Ej: "MID_FADE"
     */
    private String tipo;

    // getters y setters
}

