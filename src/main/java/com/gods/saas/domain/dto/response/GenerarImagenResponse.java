package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.dto.request.Imagenes;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class GenerarImagenResponse {
    private String sesionId;

    // key: frontal | lateral | trasera
    // value: URL imagen
    private ImagenesResponse imagenes;
}
