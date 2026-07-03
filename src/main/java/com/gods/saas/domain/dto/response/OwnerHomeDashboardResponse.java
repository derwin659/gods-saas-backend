package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OwnerHomeDashboardResponse {
    private BigDecimal todaySales;
    private BigDecimal expectedCash;
    private Integer todayAppointments;
    private Integer activeBarbers;
    private Integer newClients;
    private BigDecimal averageTicket;
    private BigDecimal todayExpenses;
    private BigDecimal todayProfessionalPayments;
    private BigDecimal pendingProfessionalPayments;
    private BigDecimal yesterdaySales;
    private BigDecimal previousWeekSales;
    private DashboardLeaderResponse topBarber;
    private DashboardLeaderResponse topService;
    private List<DashboardAlertResponse> alerts;
    private List<UpcomingAppointmentItemResponse> upcomingAppointments;

    private List<BranchDashboardItemResponse> branches;
}