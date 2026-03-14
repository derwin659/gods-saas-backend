package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "settings")
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "codigo", unique = true, length = 50)
    private String codigo;

    @Column(name = "owner_name")
    private String ownerName;


    @Column(name = "logo_url", length = 300)
    private String logoUrl;

    private String pais;
    private String ciudad;
    private String plan;
    private Boolean active = true;  // ← ESTE CAMPO ES NECESARIO
    @Column(name = "estado_suscripcion")
    private String estadoSuscripcion;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @OneToOne(mappedBy = "tenant", cascade = CascadeType.ALL)
    private TenantSettings settings;

    // ⬇️ Constructor agregado para permitir new Tenant(id)
    public Tenant(Long id) {
        this.id = id;
    }
}
