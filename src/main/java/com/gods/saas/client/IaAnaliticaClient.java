package com.gods.saas.client;

import com.gods.saas.domain.dto.request.AnalizarImagenRequest;
import com.gods.saas.domain.dto.response.AnalizarImagenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "ia-analitica",
        url = "${ia.analitica.url}"
)
@Component
public interface IaAnaliticaClient {

    @PostMapping("/analizar")
    AnalizarImagenResponse analizarImagen(
            @RequestBody AnalizarImagenRequest request
    );
}

