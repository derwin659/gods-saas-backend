package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class BootstrapOwnerRequest {
    // datos tenant
    private String tenantNombre;
    private String ownerName;
    private String pais;
    private String ciudad;
    private String plan; // "PRO", etc

    // datos branch inicial
    private String branchNombre;    // "Sede Principal"
    private String branchDireccion; // opcional

    // datos del owner
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String password;
}
