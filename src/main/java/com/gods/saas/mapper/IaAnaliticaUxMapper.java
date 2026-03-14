package com.gods.saas.mapper;

import com.gods.saas.domain.dto.response.AnalizarImagenResponse;
import com.gods.saas.domain.dto.response.CorteUx;
import com.gods.saas.domain.dto.response.FormaRostroUx;
import com.gods.saas.domain.dto.response.UxAnalisisResponse;

import java.util.List;

public class IaAnaliticaUxMapper {

    public static UxAnalisisResponse toUx(AnalizarImagenResponse ia) {

        // 1️⃣ Forma de rostro
        FormaRostroUx forma = FormaRostroUx.builder()
                .principal(ia.getFormaRostro().getPrincipal())
                .alternativa(ia.getFormaRostro().getAlternativa())
                .confianza(ia.getFormaRostro().getConfianza())
                .build();

        // 2️⃣ Cortes recomendados (TOP 3, score >= 0.75)
        List<CorteUx> cortes = ia.getRecomendaciones().getCortes().stream()
                .filter(c -> c.getScore() >= 0.75)
                .limit(3)
                .map(c -> CorteUx.builder()
                        .nombre(c.getNombre())
                        .score(c.getScore())
                        .build())
                .toList();

        // 3️⃣ Mensaje UX
        String mensaje = ia.getFormaRostro().getConfianza() < 0.7
                ? "Estos estilos favorecen a tu tipo de rostro"
                : "Este es el estilo ideal para tu rostro";

        return UxAnalisisResponse.builder()
                .formaRostro(forma)
                .mensaje(mensaje)
                .cortesRecomendados(cortes)
                .onduladoApto(ia.getCabello().getOndulado().getApto())
                .tintesSugeridos(
                        ia.getRecomendaciones().getTintes().stream().limit(3).toList()
                )
                .build();
    }
}

