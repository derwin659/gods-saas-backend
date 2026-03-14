package com.gods.saas.domain.dto.request;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder

public class Imagenes {
    private String frontal;
    private String lateral;
    private String trasera;
}
