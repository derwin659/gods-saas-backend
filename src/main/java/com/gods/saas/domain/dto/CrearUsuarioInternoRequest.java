package com.gods.saas.domain.dto;

import lombok.Data;

    @Data
    public class CrearUsuarioInternoRequest {
        private String nombre;
        private String apellido;
        private String email;
        private String phone;
        private String rol; // BARBER, ADMIN, OWNER, CASHIER
        private Long tenantId;
        private Long branchId;
        private String password;
    }


