package com.gods.saas.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class CrearUsuarioRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String rol;      // BARBER, ADMIN, OWNER, CASHIER
    private Long branchId;
    private String password;
    private Boolean preserveProfessionalProfile;
    private Boolean professionalProfileEnabled;
    private Boolean canSell;
    private List<Long> professionalBranchIds;
}

