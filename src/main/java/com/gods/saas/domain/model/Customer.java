package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(length = 120)
    private String apellidos;

    @Column(length = 50)
    private String telefono;

    @Column(name = "phone_verified")
    private boolean phoneVerified;

    @Column(name = "phone_pendiente")
    private String phonePendiente;

    @Column(name = "phone_pendiente_verificacion")
    private boolean phonePendienteVerificacion;

    @Column(name = "origen_cliente")
    private String origenCliente;

    @Column(length = 150)
    private String email;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(length = 20)
    private String genero;

    @Column(name = "puntos_disponibles")
    private Integer puntosDisponibles;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "migrated", nullable = false)
    private Boolean migrated = false;

    @Column(name = "app_activated", nullable = false)
    private Boolean appActivated = false;

    @Column(name = "app_activated_at")
    private LocalDateTime appActivatedAt;

    @Column(name = "welcome_bonus_granted", nullable = false)
    private Boolean welcomeBonusGranted = false;

    @Column(name = "activation_bonus_granted", nullable = false)
    private Boolean activationBonusGranted = false;

    @Column(name = "source", length = 30)
    private String source;
}