package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OwnerHomeDashboardResponse {
    private BigDecimal todaySales;
    private Integer todayAppointments;
    private Integer activeBarbers;
    private Integer newClients;
    private BigDecimal averageTicket;
    private BigDecimal todayExpenses;
    private List<UpcomingAppointmentItemResponse> upcomingAppointments;
}