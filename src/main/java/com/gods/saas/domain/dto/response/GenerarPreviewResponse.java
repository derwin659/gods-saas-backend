package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerarPreviewResponse {

    private String estado; // OK | BLOQUEADO | ERROR

    private String mensaje;

    private ImagenesPreview imagenes;
}

