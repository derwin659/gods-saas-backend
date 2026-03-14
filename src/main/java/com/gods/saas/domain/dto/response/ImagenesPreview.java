package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImagenesPreview {

    private String frontal;

    private String lateral;

    private String trasera;
}

