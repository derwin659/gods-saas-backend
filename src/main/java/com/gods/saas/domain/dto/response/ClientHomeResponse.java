package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.dto.ClienteResponse;
import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClientHomeResponse {

    private TenantMini tenant;
    private ClienteResponse customer;
    private PointsSummary points;

    // para crecer luego
    private NextAppointmentResponse nextAppointment; // null por ahora
    private List<LastVisitResponse> lastVisits;      // [] por ahora
    private BenefitsResponse benefits;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TenantMini {
        private Long id;
        private String nombre;
        private String logoUrl;
        private String ciudad;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class PointsSummary {
        private Integer disponibles;
        private Integer metaCorteGratis; // ej: 200 (config fijo por ahora)
        private Integer faltan;
        private Double progreso;// 0..1
        private Integer acumulados;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class NextAppointmentResponse {
        private Long appointmentId;
        private String fecha;     // ISO "2026-03-05"
        private String horaInicio;// "17:30"
        private String servicio;  // "Degradado"
        private String barbero;   // "Juan"
        private String estado;    // "PENDIENTE"
        private String horaFin;
        private String branch;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LastVisitResponse {
        private String fecha;   // "2026-03-02"
        private String servicio;
        private Integer puntos;
        private Double total;
        private Long appointmentId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BenefitsResponse {
        private String nivel;
        private Integer cantidadCanjes;
        private Integer puntosMes;
        private Integer racha;
    }
}
