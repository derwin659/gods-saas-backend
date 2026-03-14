package com.gods.saas.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "input_image_url")
    private String inputImageUrl;

    @Column(name = "output_frontal_url")
    private String outputFrontalUrl;

    @Column(name = "output_lateral_url")
    private String outputLateralUrl;

    @Column(name = "output_trasera_url")
    private String outputTraseraUrl;

    @Column(name = "modelo_usado")
    private String modeloUsado;

    @Column(name = "porcentaje_match")
    private Integer porcentajeMatch;

    @Column(name = "fecha_generacion")
    private LocalDateTime fechaGeneracion;
}
