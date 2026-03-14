package com.gods.saas.domain.model;

import com.gods.saas.utils.EstadoConexionPantalla;
import com.gods.saas.utils.EstadoPantalla;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "pantalla_ia")
public class Pantalla {

    @Id
    @Column(length = 50)
    private String id; // TV-LOS-INKAS-01

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conexion", nullable = false)
    private EstadoConexionPantalla estadoConexion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pantalla", nullable = false)
    private EstadoPantalla estadoPantalla;

    @Column(name = "sesion_actual_id")
    private String sesionActualId;
}

