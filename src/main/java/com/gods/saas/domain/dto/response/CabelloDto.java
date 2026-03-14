package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabelloDto {

    private String densidad;           // baja | media | alta
    private OnduladoDto ondulado;
}
