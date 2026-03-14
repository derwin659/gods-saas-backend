package com.gods.saas.domain.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TinteRequest {

    private boolean aplicar;

    private String color;
}

