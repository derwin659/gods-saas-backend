package com.gods.saas.domain.dto.request;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class GenerarImagenRequest {

    private String sesionId;

    /**
     * Imágenes en BASE64 (SIN prefijo data:image)
     * Keys esperadas:
     *  - frontal
     *  - lateral
     *  - trasera
     */
    private Imagenes imagenes;

    private CorteDTO corte;
    private TinteDTO tinte;
    private OnduladoDTO ondulado;

    /**
     * Ejemplo: ["frontal", "lateral", "trasera"]
     */
    private List<String> vistas;

    // getters y setters
}
