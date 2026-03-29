package com.gods.saas.domain.dto.response;

import com.gods.saas.domain.model.CustomerCutHistory;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCutHistoryResponse {

    private Long id;
    private Long tenantId;
    private Long branchId;
    private String branchName;
    private Long customerId;
    private String customerName;
    private Long barberUserId;
    private String barberUserName;
    private Long appointmentId;
    private Long saleId;
    private String cutName;
    private String cutDescription;
    private String observations;
    private LocalDateTime fechaCorte;

    public static CustomerCutHistoryResponse fromEntity(CustomerCutHistory history) {
        String customerName = null;
        if (history.getCustomer() != null) {
            String nombres = history.getCustomer().getNombres() == null ? "" : history.getCustomer().getNombres().trim();
            String apellidos = history.getCustomer().getApellidos() == null ? "" : history.getCustomer().getApellidos().trim();
            customerName = (nombres + " " + apellidos).trim();
            if (customerName.isBlank()) {
                customerName = nombres.isBlank() ? null : nombres;
            }
        }

        return CustomerCutHistoryResponse.builder()
                .id(history.getId())
                .tenantId(history.getTenant() != null ? history.getTenant().getId() : null)
                .branchId(history.getBranch() != null ? history.getBranch().getId() : null)
                .branchName(history.getBranch() != null ? history.getBranch().getNombre() : null)
                .customerId(history.getCustomer() != null ? history.getCustomer().getId() : null)
                .customerName(customerName)
                .barberUserId(history.getBarberUser() != null ? history.getBarberUser().getId() : null)
                .barberUserName(history.getBarberUser() != null ? history.getBarberUser().getNombre() : null)
                .appointmentId(history.getAppointment() != null ? history.getAppointment().getId() : null)
                .saleId(history.getSale() != null ? history.getSale().getId() : null)
                .cutName(history.getCutName())
                .cutDescription(history.getCutDescription())
                .observations(history.getObservations())
                .fechaCorte(history.getFechaCorte())
                .build();
    }
}
