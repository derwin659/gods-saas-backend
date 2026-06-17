package com.gods.saas.domain.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCurrentResponse {

    private Long subId;
    private Long tenantId;

    private String plan;
    private String publicPlan;
    private String estado;
    private boolean trial;

    private Double precioMensual;
    private String billingCycle;
    private String currency;
    private List<SubscriptionPlanPriceResponse> planPrices;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaRenovacion;
    private LocalDateTime fechaFin;

    private Integer diasGracia;
    private String observaciones;

    private Integer maxBranches;
    private Integer usedBranches;

    private Integer maxBarbers;
    private Integer usedBarbers;

    private Integer maxAdmins;
    private Integer usedAdmins;

    private boolean aiEnabled;
    private String aiLevel;
    private Integer aiVisualCreditsBalance;
    private boolean loyaltyEnabled;
    private boolean promotionsEnabled;
    private String billingChannel;
    private Integer maxMonthlyBookings;
    private Integer usedMonthlyBookings;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    private Boolean canOperate;
    private Boolean expired;
}

