package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.dto.request.Imagenes;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ImagenesResponse{
    private String frontal;
    private String lateral;
    private String trasera;
    private String antes;
    private String anteslateral;

}
