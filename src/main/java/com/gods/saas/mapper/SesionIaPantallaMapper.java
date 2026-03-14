package com.gods.saas.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.dto.request.SeleccionClienteRequest;
import com.gods.saas.domain.dto.response.CorteRecomendadoDto;
import com.gods.saas.domain.dto.response.FormaRostroDto;
import com.gods.saas.domain.dto.response.ImagenGeneradaDTO;
import com.gods.saas.domain.model.SesionIa;
import com.gods.saas.service.impl.SesionIaPantallaDTO;

import java.util.List;

public class SesionIaPantallaMapper {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static SesionIaPantallaDTO fromSesion(SesionIa sesion) {

        try {
            SesionIaPantallaDTO dto = new SesionIaPantallaDTO();

            dto.setSesionId(sesion.getId());
            dto.setEstado(sesion.getEstado().name());

            // =========================
            // IA ANALÍTICA
            // =========================
            if (sesion.getResultadoAnalitico() == null) {
                dto.setMensaje("Preparando análisis...");
                return dto;
            }

            JsonNode root = mapper.readTree(sesion.getResultadoAnalitico());

            // 🔥 FORMA DEL ROSTRO
            FormaRostroDto forma = mapper.treeToValue(
                    root.get("forma_rostro"),
                    FormaRostroDto.class
            );
            dto.setFormaRostro(forma);

            dto.setMensaje("Estos estilos favorecen a tu tipo de rostro");

            // 🔥 CORTES RECOMENDADOS
            List<CorteRecomendadoDto> cortes =
                    mapper.convertValue(
                            root.get("recomendaciones").get("cortes"),
                            new TypeReference<>() {}
                    );
            dto.setCortesRecomendados(cortes.stream().limit(3).toList());

            // 🔥 ONDULADO APTO
            dto.setOnduladoApto(
                    root.path("cabello")
                            .path("ondulado")
                            .path("apto")
                            .asBoolean(false)
            );

            // 🔥 TINTES
            if (root.path("recomendaciones").has("tintes")) {
                dto.setTintesSugeridos(
                        mapper.convertValue(
                                root.get("recomendaciones").get("tintes"),
                                new TypeReference<>() {}
                        )
                );
            }

            // 🔥 SELECCIÓN DEL CLIENTE
            if (sesion.getSeleccionCliente() != null) {
                dto.setSeleccionCliente(
                        mapper.readValue(
                                sesion.getSeleccionCliente(),
                                SeleccionClienteRequest.class
                        )
                );
            }


            // =========================
            // IA ILUSTRATIVA (CLAVE)
            // =========================
            if (sesion.getImagenes() != null) {
                dto.setImagenes(
                        mapper.readValue(
                                sesion.getImagenes(),
                                ImagenGeneradaDTO.class
                        )
                );
            }



            return dto;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Error mapeando SesionIa a DTO TV. sesionId=" + sesion.getId(),
                    e
            );
        }
    }
}
