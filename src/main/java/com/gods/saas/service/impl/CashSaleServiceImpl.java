package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.domain.enums.CashRegisterStatus;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.CashSaleService;
import com.gods.saas.service.impl.impl.LoyaltyService;
import com.gods.saas.service.impl.impl.SaleService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final TenantTimeService tenantTimeService;
    private final CustomerCutHistoryService customerCutHistoryService;
    private final LoyaltyService loyaltyService;
    private final BranchRepository branchRepository;
    private final AppUserRepository userRepository;

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
        saleRequest.setCutType(request.getCutType());
        saleRequest.setCutDetail(request.getCutDetail());
        saleRequest.setCutObservations(request.getCutObservations());
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
        ZoneId zoneId = tenantTimeService.getZone(tenantId);

        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        return saleRepository
                .findByTenant_IdAndBranch_IdAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThanOrderByFechaCreacionDesc(
                        tenantId,
                        branchId,
                        from,
                        to
                )
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

        restoreProductStockFromSale(tenantId, branchId, userId, sale);

        if (sale.getCustomer() != null) {
            AppUser actor = sale.getUser();
            loyaltyService.revertSalePoints(sale.getTenant(), sale.getCustomer(), actor, sale);
        }

        customerCutHistoryService.deleteBySale(tenantId, saleId);

        if (sale.getAppointment() != null) {
            sale.getAppointment().setEstado("CONFIRMADO");
            appointmentRepository.save(sale.getAppointment());
        }

        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            saleItemRepository.deleteAll(sale.getItems());
            sale.getItems().clear();
        }

        saleRepository.delete(sale);
    }

    private void restoreProductStockFromSale(Long tenantId, Long branchId, Long userId, Sale sale) {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            return;
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sede no encontrada"));

        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sede no pertenece al tenant");
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!user.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El usuario no pertenece al tenant");
        }

        LocalDateTime movementTime = tenantTimeService.now(tenantId);

        for (SaleItem item : sale.getItems()) {
            if (item.getProduct() == null) {
                continue;
            }

            Product product = productRepository.findByIdAndTenant_Id(
                            item.getProduct().getId(),
                            tenantId
                    )
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado al restaurar stock"));

            int stockAnterior = product.getStockActual() == null ? 0 : product.getStockActual();
            int cantidad = item.getCantidad() == null ? 0 : item.getCantidad();
            int stockNuevo = stockAnterior + cantidad;

            product.setStockActual(stockNuevo);
            productRepository.save(product);

            StockMovement movement = StockMovement.builder()
                    .tenant(sale.getTenant())
                    .branch(branch)
                    .product(product)
                    .sale(sale)
                    .user(user)
                    .tipoMovimiento("DEVOLUCION")
                    .cantidad(cantidad)
                    .stockAnterior(stockAnterior)
                    .stockNuevo(stockNuevo)
                    .costoUnitario(item.getCostoUnitario())
                    .precioUnitario(item.getPrecioUnitario())
                    .observacion("Restitución automática por eliminación de venta #" + sale.getId())
                    .fechaCreacion(movementTime)
                    .build();

            stockMovementRepository.save(movement);
        }
    }
}