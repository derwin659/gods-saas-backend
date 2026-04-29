package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateSaleFromAppointmentRequest;
import com.gods.saas.domain.dto.request.CreateSaleRequest;
import com.gods.saas.domain.dto.request.SaleItemRequest;
import com.gods.saas.domain.dto.response.CreateSaleFromAppointmentResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.ServiceEntity;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BarberSaleService {

    private final AppointmentRepository appointmentRepository;
    private final SaleService saleService;
    private final CustomerCutHistoryService customerCutHistoryService;

    @Transactional
    public CreateSaleFromAppointmentResponse createSaleFromAppointment(
            Long tenantId,
            Long branchId,
            Long userId,
            CreateSaleFromAppointmentRequest request
    ) {
        if (request.getAppointmentId() == null) {
            throw new RuntimeException("appointmentId es obligatorio");
        }

        boolean hasMixedPayments = request.getPayments() != null && !request.getPayments().isEmpty();
        if (!hasMixedPayments && (request.getMetodoPago() == null || request.getMetodoPago().isBlank())) {
            throw new RuntimeException("metodoPago es obligatorio");
        }

        String metodoPago = hasMixedPayments ? "MIXED" : request.getMetodoPago().trim().toUpperCase();

        if (!hasMixedPayments
                && !"CASH".equals(metodoPago)
                && !"CARD".equals(metodoPago)
                && !"YAPE".equals(metodoPago)
                && !"PLIN".equals(metodoPago)
                && !"TRANSFER".equals(metodoPago)) {
            throw new RuntimeException("metodoPago no válido");
        }

        Appointment appointment = appointmentRepository
                .findByIdAndTenant_IdAndBranch_IdAndUser_Id(
                        request.getAppointmentId(),
                        tenantId,
                        branchId,
                        userId
                )
                .orElseThrow(() -> new RuntimeException("No se encontró la cita"));

        String estadoActual = appointment.getEstado() == null
                ? ""
                : appointment.getEstado().trim().toUpperCase();

        if ("ATENDIDO".equals(estadoActual)
                || "COMPLETADO".equals(estadoActual)
                || "FINALIZADO".equals(estadoActual)) {
            throw new RuntimeException("La cita ya fue atendida");
        }

        ServiceEntity service = appointment.getService();
        if (service == null) {
            throw new RuntimeException("La cita no tiene servicio asociado");
        }

        if (service.getId() == null) {
            throw new RuntimeException("El servicio de la cita no es válido");
        }

        if (service.getPrecio() == null) {
            throw new RuntimeException("El servicio no tiene precio configurado");
        }

        BigDecimal serviceTotal = BigDecimal.valueOf(service.getPrecio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tipAmount = request.getTipAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : request.getTipAmount().setScale(2, RoundingMode.HALF_UP);
        if (tipAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("La propina no puede ser negativa");
        }

        BigDecimal total = serviceTotal.add(tipAmount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashReceived = null;
        BigDecimal change = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (!hasMixedPayments && "CASH".equals(metodoPago)) {
            if (request.getCashReceived() == null) {
                throw new RuntimeException("Para pago en efectivo debes enviar cashReceived");
            }

            cashReceived = BigDecimal.valueOf(request.getCashReceived())
                    .setScale(2, RoundingMode.HALF_UP);

            if (cashReceived.compareTo(total) < 0) {
                throw new RuntimeException("El monto recibido no puede ser menor al total");
            }

            change = cashReceived.subtract(total).setScale(2, RoundingMode.HALF_UP);
        }

        CreateSaleRequest saleRequest = new CreateSaleRequest();
        saleRequest.setTenantId(tenantId);
        saleRequest.setBranchId(branchId);
        saleRequest.setCustomerId(
                appointment.getCustomer() != null ? appointment.getCustomer().getId() : null
        );
        saleRequest.setUserId(userId);
        saleRequest.setAppointmentId(appointment.getId());
        saleRequest.setMetodoPago(metodoPago);
        saleRequest.setDiscount(BigDecimal.ZERO);
        saleRequest.setCashReceived(cashReceived);
        saleRequest.setTipAmount(tipAmount);
        saleRequest.setTipBarberUserId(userId);
        saleRequest.setPayments(request.getPayments());
        saleRequest.setCutType(request.getCutType());
        saleRequest.setCutDetail(request.getCutDetail());
        saleRequest.setCutObservations(request.getCutObservations());

        SaleItemRequest item = new SaleItemRequest();
        item.setServiceId(service.getId());
        item.setBarberUserId(userId);
        item.setCantidad(1);
        item.setPrecioUnitario(service.getPrecio());

        saleRequest.setItems(List.of(item));
        saleRequest.setCutType(request.getCutType());
        saleRequest.setCutDetail(request.getCutDetail());
        saleRequest.setCutObservations(request.getCutObservations());

        SaleResponse saleResponse = saleService.crearVenta(saleRequest);

        appointment.setEstado("COMPLETADO");

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String notes = request.getNotes().trim();
            appointment.setNotas(notes);
            customerCutHistoryService.syncNotesFromSale(tenantId, saleResponse.getSaleId(), notes);
        }

        appointmentRepository.save(appointment);

        Customer customer = appointment.getCustomer();

        int pointsEarned = saleResponse.getPuntosGanados() != null ? saleResponse.getPuntosGanados() : 0;
        int customerPointsBalance = saleResponse.getPuntosDisponibles() != null ? saleResponse.getPuntosDisponibles() : 0;

        String clienteNombre = "Sin cliente";
        if (customer != null) {
            String nombre = customer.getNombres() != null ? customer.getNombres().trim() : "";
            String apellido = customer.getApellidos() != null ? customer.getApellidos().trim() : "";
            String fullName = (nombre + " " + apellido).trim();
            if (!fullName.isBlank()) {
                clienteNombre = fullName;
            }
        }

        CreateSaleFromAppointmentResponse response = new CreateSaleFromAppointmentResponse();
        response.setSuccess(true);
        response.setMessage("Venta registrada correctamente");
        response.setSaleId(saleResponse.getSaleId());
        response.setAppointmentId(appointment.getId());
        response.setCliente(clienteNombre);
        response.setServicio(service.getNombre() != null ? service.getNombre() : "Servicio");
        response.setMetodoPago(metodoPago);
        response.setTotal(saleResponse.getTotal() != null ? saleResponse.getTotal().doubleValue() : total.doubleValue());
        response.setCashReceived(saleResponse.getCashReceived() != null ? saleResponse.getCashReceived().doubleValue() : (cashReceived != null ? cashReceived.doubleValue() : null));
        response.setChange(saleResponse.getChangeAmount() != null ? saleResponse.getChangeAmount().doubleValue() : change.doubleValue());
        response.setPointsEarned(pointsEarned);
        response.setCustomerPointsBalance(customerPointsBalance);
        response.setFechaHora(LocalDateTime.now().toString());

        return response;
    }
}
