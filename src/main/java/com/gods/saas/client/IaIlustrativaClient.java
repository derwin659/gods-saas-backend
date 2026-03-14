package com.gods.saas.client;

import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import com.gods.saas.domain.dto.response.AnalizarImagenResponse;
import com.gods.saas.domain.dto.response.GenerarImagenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ia-ilustrativa",
        url = "${ia.ilustrativa.url}"
)
public interface IaIlustrativaClient {
    @PostMapping("/generar")
    GenerarImagenResponse generarImagen(
            @RequestBody GenerarImagenRequest request
    );
}
