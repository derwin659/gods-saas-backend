package com.gods.saas.domain.dto.response;

import java.util.Map;

public class GenerarResponseDTO {
    private String sesionId;

    /**
     * URLs públicas listas para renderizar en TV
     * Keys:
     *  - frontal
     *  - lateral
     *  - trasera
     */
    private Map<String, String> imagenes;
}
