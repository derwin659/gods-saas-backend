package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String nombre;
    private String role; // ADMIN | BARBER | CAJA
}
