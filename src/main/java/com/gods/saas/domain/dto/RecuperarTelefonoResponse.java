package com.gods.saas.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecuperarTelefonoResponse {

    private boolean procesoIniciado;
    private String telefonoAntiguo;
    private String nuevoTelefonoPendiente;
    private String otpEnviadoA;
    private String mensaje;
}

