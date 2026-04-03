package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GenerarImagenResponse {

    private String sesionId;

    private ImagenesResponse imagenes;
}