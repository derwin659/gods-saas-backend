package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaRostroUx {

    private String principal;
    private String alternativa;
    private Double confianza;
}

