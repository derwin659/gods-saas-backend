package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.Appointment;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.Sale;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.CashSaleService;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSaleServiceImpl implements CashSaleService {

    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleService saleService;
    private final CustomerRepository customerRepository;
    private final SaleItemRepository saleItemRepository;

    @Override
    public SaleResponse createCashSale(Long tenantId, Long branchId, Long userId, CreateCashSaleRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un item.");
        }

        cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatus(tenantId, branchId, CashRegisterStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("No hay una caja abierta en esta sede."));

        Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findByIdAndTenant_Id(request.getAppointmentId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada"));

            if (appointment.getBranch() == null || !appointment.getBranch().getId().equals(branchId)) {
                throw new IllegalStateException("La cita no pertenece a esta sede.");
            }

            String estadoActual = appointment.getEstado() == null ? "" : appointment.getEstado().trim().toUpperCase();
            if (estadoActual.equals("COMPLETADO")
                    || estadoActual.equals("ATENDIDO")
                    || estadoActual.equals("FINALIZADO")
                    || estadoActual.equals("CANCELADO")) {
                throw new IllegalStateException("La cita ya no puede ser atendida.");
            }
        }

        CreateSaleRequest saleRequest = new CreateSaleRequest();
        saleRequest.setTenantId(tenantId);
        saleRequest.setBranchId(branchId);
        saleRequest.setCustomerId(request.getCustomerId());
        saleRequest.setUserId(userId);
        saleRequest.setAppointmentId(request.getAppointmentId());
        saleRequest.setMetodoPago(normalizeMethod(request.getMetodoPago()));
        saleRequest.setDiscount(safe(request.getDiscount()));
        saleRequest.setCashReceived(safe(request.getCashReceived()));
        saleRequest.setItems(
                request.getItems().stream().map(this::toSaleItemRequest).toList()
        );

        SaleResponse response = saleService.crearVenta(saleRequest);

        if (appointment != null) {
            appointment.setEstado("COMPLETADO");
            appointmentRepository.save(appointment);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getTodaySales(Long tenantId, Long branchId) {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        return saleRepository
                .findByTenant_IdAndBranch_IdAndFechaCreacionBetweenOrderByFechaCreacionDesc(tenantId, branchId, from, to)
                .stream()
                .map(sale -> mapResponse(sale, 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByRange(Long tenantId, Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        return saleRepository
                .findByTenant_IdAndBranch_IdAndFechaCreacionBetweenOrderByFechaCreacionDesc(
                        tenantId, branchId, fromDateTime, toDateTime
                )
                .stream()
                .map(sale -> mapResponse(sale, 0))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SaleResponse getById(Long tenantId, Long saleId) {
        Sale sale = saleRepository.findByIdAndTenant_Id(saleId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));

        return mapResponse(sale, 0);
    }

    @Override
    @Transactional
    public SaleResponse updateSale(Long tenantId, Long branchId, Long userId, Long saleId, UpdateSaleRequest request) {
        Sale sale = saleRepository.findByIdAndTenant_IdAndBranch_Id(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (sale.getCashRegister() == null) {
            throw new RuntimeException("La venta no pertenece a una caja");
        }

        if (sale.getCashRegister().getClosedAt() != null) {
            throw new RuntimeException("Solo se puede editar una venta con caja abierta");
        }

        if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            sale.setCustomer(customer);
        }

        if (request.getMetodoPago() != null && !request.getMetodoPago().isBlank()) {
            sale.setMetodoPago(request.getMetodoPago().trim());
        }

        if (request.getSubtotal() != null) {
            sale.setSubtotal(request.getSubtotal());
        }

        if (request.getDiscount() != null) {
            sale.setDiscount(request.getDiscount());
        }

        if (request.getTotal() != null) {
            sale.setTotal(request.getTotal());
        }

        if (request.getCashReceived() != null) {
            sale.setCashReceived(request.getCashReceived());
        }

        if (request.getChangeAmount() != null) {
            sale.setChangeAmount(request.getChangeAmount());
        }

        Sale saved = saleRepository.save(sale);
        return mapResponse(saved, 0);
    }

    private SaleItemRequest toSaleItemRequest(CreateCashSaleItemRequest request) {
        SaleItemRequest item = new SaleItemRequest();
        item.setServiceId(request.getServiceId());
        item.setProductId(request.getProductId());
        item.setBarberUserId(request.getBarberUserId());
        item.setCantidad((request.getCantidad() == null || request.getCantidad() <= 0) ? 1 : request.getCantidad());
        item.setPrecioUnitario(request.getPrecioUnitario() != null ? request.getPrecioUnitario().doubleValue() : null);
        return item;
    }

    private SaleResponse mapResponse(Sale sale, Integer puntosGanados) {
        return SaleResponse.builder()
                .saleId(sale.getId())
                .cashRegisterId(sale.getCashRegister() != null ? sale.getCashRegister().getId() : null)
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(sale.getCustomer() != null ? sale.getCustomer().getNombres() : null)
                .appointmentId(sale.getAppointment() != null ? sale.getAppointment().getId() : null)
                .metodoPago(sale.getMetodoPago())
                .subtotal(safe(sale.getSubtotal()))
                .discount(safe(sale.getDiscount()))
                .total(safe(sale.getTotal()))
                .cashReceived(safe(sale.getCashReceived()))
                .changeAmount(safe(sale.getChangeAmount()))
                .fechaCreacion(sale.getFechaCreacion())
                .puntosGanados(puntosGanados == null ? 0 : puntosGanados)

                // 🔥 ESTA ES LA CLAVE
                .barberName(resolveBarberName(sale))

                .items(
                        sale.getItems() == null
                                ? List.of()
                                : sale.getItems().stream().map(item ->
                                SaleItemResponse.builder()
                                        .saleItemId(item.getId())
                                        .serviceId(item.getService() != null ? item.getService().getId() : null)
                                        .serviceName(item.getService() != null ? item.getService().getNombre() : null)
                                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                                        .productName(item.getProduct() != null ? item.getProduct().getNombre() : null)
                                        .barberUserId(item.getBarberUser() != null ? item.getBarberUser().getId() : null)
                                        .barberUserName(item.getBarberUser() != null ? item.getBarberUser().getNombre() : null)
                                        .cantidad(item.getCantidad())
                                        .precioUnitario(safe(item.getPrecioUnitario()))
                                        .subtotal(safe(item.getSubtotal()))
                                        .build()
                        ).collect(Collectors.toList())
                )
                .build();
    }
    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeMethod(String metodoPago) {
        if (metodoPago == null || metodoPago.isBlank()) {
            return "CASH";
        }
        return metodoPago.trim().toUpperCase();
    }

    private String resolveBarberName(Sale sale) {
        if (sale.getUser() != null
                && sale.getUser().getNombre() != null
                && !sale.getUser().getNombre().trim().isEmpty()) {
            return sale.getUser().getNombre().trim();
        }

        if (sale.getItems() != null) {
            return sale.getItems().stream()
                    .filter(item -> item.getBarberUser() != null)
                    .map(item -> item.getBarberUser().getNombre())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }


    @Override
    @Transactional
    public void deleteSale(Long tenantId, Long branchId, Long userId, Long saleId) {
        Sale sale = saleRepository.findByIdAndTenant_IdAndBranch_Id(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (sale.getCashRegister() == null) {
            throw new RuntimeException("La venta no pertenece a una caja");
        }

        if (sale.getCashRegister().getClosedAt() != null) {
            throw new RuntimeException("Solo se puede eliminar una venta con caja abierta");
        }

        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            saleItemRepository.deleteAll(sale.getItems());
        }

        saleRepository.delete(sale);
    }
}
