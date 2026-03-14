package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class VerifyPhoneResponse {
    private boolean telefonoVerificado;

    public VerifyPhoneResponse(boolean b) {
        this.telefonoVerificado = b;
    }
}
