package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.SeleccionClienteRequest;
import com.gods.saas.domain.dto.response.CorteRecomendadoDto;
import com.gods.saas.domain.dto.response.FormaRostroDto;
import com.gods.saas.domain.dto.response.ImagenGeneradaDTO;
import jakarta.persistence.Column;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Data
public class SesionIaPantallaDTO {

    private String sesionId;

    // 🔥 ESTE CAMPO ES CLAVE
    private String estado;

    private FormaRostroDto formaRostro;

    private String mensaje;
    // Ej: "Estos estilos favorecen a tu tipo de rostro"

    private List<CorteRecomendadoDto> cortesRecomendados;

    private boolean onduladoApto;

    private SeleccionClienteRequest seleccionCliente;

    private List<String> tintesSugeridos;

    // Se llena cuando exista IA generativa
    private ImagenGeneradaDTO imagenes;

}

