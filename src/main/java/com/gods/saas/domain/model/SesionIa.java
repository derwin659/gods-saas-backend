package com.gods.saas.domain.model;

import com.gods.saas.utils.EstadoSesion;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Data
public class SesionIa {

    @Id
    @Column(length = 50)
    private String id; // SES-UUID

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "barbero_id", nullable = false)
    private Long barberoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSesion estado;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado_analitico", columnDefinition = "jsonb")
    private String resultadoAnalitico; // JSON IA Analítica

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "imagenes", columnDefinition = "jsonb")
    private String imagenes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seleccion_cliente", columnDefinition = "jsonb")
    private String seleccionCliente; // JSON selección final

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "foto_original", columnDefinition = "jsonb")
    private String fotoOriginal;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = "SES-" + java.util.UUID.randomUUID();
        }
        if (this.creadoEn == null) {
            this.creadoEn = LocalDateTime.now();
        }
    }
}

