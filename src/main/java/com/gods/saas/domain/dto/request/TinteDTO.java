package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class TinteDTO {

    private boolean aplicar;

    /**
     * Ej: "platino"
     * Puede ser null si aplicar = false
     */
    private String color;

    // getters y setters
}

