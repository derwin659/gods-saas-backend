package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class OnduladoDTO {

    private boolean aplicar;

    /**
     * Ej: "ondulado_suave"
     * Puede ser null si aplicar = false
     */
    private String tipo;

    // getters y setters
}

