package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.service.impl.impl.OwnerAgendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerAgendaServiceImpl implements OwnerAgendaService {

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    @Override
    public List<OwnerAgendaResponse> getAgendaDelDia(Long tenantId, Long branchId, LocalDate fecha) {
        final List<Appointment> appointments =
                appointmentRepository.findOwnerAgendaByTenantBranchAndFecha(
                        tenantId,
                        branchId,
                        fecha
                );

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return appointments.stream()
                .map(a -> {
                    BigDecimal precioServicio = a.getTotalAmount() != null
                            ? a.getTotalAmount()
                            : BigDecimal.ZERO;

                    BigDecimal montoPagoInicial = a.getDepositAmount() != null
                            ? a.getDepositAmount()
                            : BigDecimal.ZERO;

                    BigDecimal saldoPendiente = a.getRemainingAmount() != null
                            ? a.getRemainingAmount()
                            : precioServicio.subtract(montoPagoInicial);

                    if (saldoPendiente.compareTo(BigDecimal.ZERO) < 0) {
                        saldoPendiente = BigDecimal.ZERO;
                    }

                    BigDecimal originalAmount = a.getOriginalAmount() != null
                            ? a.getOriginalAmount()
                            : precioServicio;

                    BigDecimal discountAmount = a.getDiscountAmount() != null
                            ? a.getDiscountAmount()
                            : BigDecimal.ZERO;

                    BigDecimal totalAmount = a.getTotalAmount() != null
                            ? a.getTotalAmount()
                            : originalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

                    Boolean requierePagoInicial = Boolean.TRUE.equals(a.getDepositRequired());

                    String depositStatus = a.getDepositStatus() != null && !a.getDepositStatus().isBlank()
                            ? a.getDepositStatus()
                            : (requierePagoInicial ? "PENDING_VALIDATION" : "NOT_REQUIRED");

                    Boolean pagoInicialValidado = "PAID".equalsIgnoreCase(depositStatus)
                            || a.getDepositValidatedAt() != null;

                    String estadoPagoInicial = mapDepositStatusToFrontend(depositStatus, requierePagoInicial);

                    return OwnerAgendaResponse.builder()
                            .appointmentId(a.getId())
                            .customerId(a.getCustomer() != null ? a.getCustomer().getId() : null)
                            .serviceId(a.getService() != null ? a.getService().getId() : null)
                            .barberUserId(a.getUser() != null ? a.getUser().getId() : null)
                            .branchId(a.getBranch() != null ? a.getBranch().getId() : null)

                            .fecha(a.getFecha() != null ? a.getFecha().toString() : null)
                            .hora(a.getHoraInicio() != null ? a.getHoraInicio().format(timeFormatter) : "")
                            .horaFin(a.getHoraFin() != null ? a.getHoraFin().format(timeFormatter) : "")

                            .cliente(a.getCustomer() != null ? a.getCustomer().getNombres() : "Cliente")
                            .telefono(a.getCustomer() != null ? a.getCustomer().getTelefono() : null)
                            .servicio(a.getService() != null ? a.getService().getNombre() : "Servicio")
                            .barbero(a.getUser() != null ? a.getUser().getNombre() : "Sin asignar")
                            .estado(a.getEstado() != null ? a.getEstado() : "RESERVADO")

                            // Promoción / importes
                            .promotionTitle(a.getPromotionTitle())
                            .originalAmount(originalAmount)
                            .discountAmount(discountAmount)
                            .totalAmount(totalAmount)

                            // Pago inicial
                            .requierePagoInicial(requierePagoInicial)
                            .montoPagoInicial(montoPagoInicial)
                            .precioServicio(precioServicio)
                            .saldoPendiente(saldoPendiente)
                            .metodoPagoInicial(resolveDepositMethod(a))
                            .numeroOperacionPagoInicial(a.getDepositOperationCode())
                            .comprobantePagoInicialUrl(a.getDepositEvidenceUrl())
                            .estadoPagoInicial(estadoPagoInicial)
                            .pagoInicialValidado(pagoInicialValidado)

                            .build();
                })
                .toList();
    }

    private String resolveDepositMethod(Appointment appointment) {
        if (appointment.getDepositMethodName() != null && !appointment.getDepositMethodName().isBlank()) {
            return appointment.getDepositMethodName();
        }

        if (appointment.getDepositMethodCode() != null && !appointment.getDepositMethodCode().isBlank()) {
            return appointment.getDepositMethodCode();
        }

        if (appointment.getDepositPaymentMethod() != null) {
            return appointment.getDepositPaymentMethod().getDisplayName();
        }

        return null;
    }

    private String mapDepositStatusToFrontend(String depositStatus, boolean requierePagoInicial) {
        if (!requierePagoInicial) {
            return "NO_REQUIERE";
        }

        if (depositStatus == null || depositStatus.isBlank()) {
            return "PENDIENTE_VALIDACION";
        }

        return switch (depositStatus.toUpperCase()) {
            case "NOT_REQUIRED" -> "NO_REQUIERE";
            case "PENDING_VALIDATION" -> "PENDIENTE_VALIDACION";
            case "PAID" -> "VALIDADO";
            case "REJECTED" -> "RECHAZADO";
            default -> depositStatus;
        };
    }
}