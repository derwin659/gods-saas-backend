package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.*;
import com.gods.saas.domain.dto.response.SaleItemResponse;
import com.gods.saas.domain.dto.response.SalePaymentResponse;
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
import java.math.RoundingMode;
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
    private final SalePaymentRepository salePaymentRepository;
    private final TenantTimeService tenantTimeService;
    private final CustomerCutHistoryService customerCutHistoryService;
    private final LoyaltyService loyaltyService;
    private final BranchRepository branchRepository;
    private final AppUserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @Override
    public SaleResponse createCashSale(Long tenantId, Long branchId, Long userId, CreateCashSaleRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un item.");
        }

        LocalDateTime effectiveSaleDate = resolveSaleDate(tenantId, request);

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

        BigDecimal requestDiscount = safe(request.getDiscount());
        BigDecimal appointmentDiscount = appointment != null
                ? safe(appointment.getDiscountAmount())
                : BigDecimal.ZERO;

        saleRequest.setDiscount(requestDiscount.add(appointmentDiscount));
        saleRequest.setCashReceived(safe(request.getCashReceived()));

        // IMPORTANTE: copiar propina y pagos mixtos al servicio real de ventas.
        // Antes estos campos llegaban desde Flutter al CreateCashSaleRequest,
        // pero se perdían aquí y SaleServiceImpl guardaba tipAmount = 0.
        saleRequest.setTipAmount(safe(request.getTipAmount()));
        saleRequest.setTipBarberUserId(request.getTipBarberUserId());
        saleRequest.setPayments(request.getPayments());

        saleRequest.setCutType(request.getCutType());
        saleRequest.setCutDetail(request.getCutDetail());
        saleRequest.setCutObservations(request.getCutObservations());
        saleRequest.setItems(
                request.getItems().stream().map(this::toSaleItemRequest).toList()
        );

        SaleResponse response = saleService.crearVenta(saleRequest);
        Sale savedSale = saleRepository.findByIdAndTenant_Id(response.getSaleId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Venta creada no encontrada"));

        savedSale.setSaleDate(effectiveSaleDate);
        savedSale = saleRepository.save(savedSale);


        if (appointment != null) {
            appointment.setEstado("COMPLETADO");
            appointmentRepository.save(appointment);
        }

        return mapResponse(savedSale, response.getPuntosGanados());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getTodaySales(Long tenantId, Long branchId) {
        ZoneId zoneId = tenantTimeService.getZone(tenantId);

        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        return saleRepository
                .findCashSalesByBusinessDateRange(
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
                .findCashSalesByBusinessDateRange(
                        tenantId, branchId, fromDateTime, toDateTime
                )
                .stream()
                .map(sale -> mapResponse(sale, 0))
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByCashRegister(Long tenantId, Long branchId, Long cashRegisterId) {
        CashRegister cashRegister = cashRegisterRepository.findByIdAndTenant_Id(cashRegisterId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Caja no encontrada"));

        if (cashRegister.getBranch() == null || !cashRegister.getBranch().getId().equals(branchId)) {
            throw new IllegalStateException("La caja no pertenece a esta sede.");
        }

        return saleRepository
                .findCashSalesByCashRegister(tenantId, branchId, cashRegisterId)
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
        requireOwnerForSensitiveSaleAction(tenantId, userId);
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
            sale.setMetodoPago(normalizeMethod(request.getMetodoPago()));
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

        // Compatibilidad hacia atrás:
        // - Flutter o versiones antiguas pueden seguir enviando solo metodoPago/cashReceived/changeAmount.
        // - Si payments viene null, NO tocamos la tabla sale_payment.
        // - Si payments viene con datos, reemplazamos los pagos de la venta.
        if (request.getPayments() != null) {
            replaceSalePaymentsFromUpdateRequest(sale, request);
        }

        Sale saved = saleRepository.save(sale);
        return mapResponse(saved, 0);
    }

    private void replaceSalePaymentsFromUpdateRequest(Sale sale, UpdateSaleRequest request) {
        BigDecimal total = safe(request.getTotal() != null ? request.getTotal() : sale.getTotal())
                .setScale(2, RoundingMode.HALF_UP);

        List<SalePaymentRequest> rawPayments = request.getPayments();

        sale.getPayments().clear();

        BigDecimal paidTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int validPayments = 0;
        String firstMethod = null;

        if (rawPayments != null) {
            for (SalePaymentRequest paymentRequest : rawPayments) {
                if (paymentRequest == null) continue;

                String method = normalizeMethod(paymentRequest.getMethod());
                BigDecimal amount = safe(paymentRequest.getAmount()).setScale(2, RoundingMode.HALF_UP);

                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
                if ("FREE".equals(method) && total.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalArgumentException("Una venta con total mayor a cero no puede pagarse como gratis.");
                }

                SalePayment payment = SalePayment.builder()
                        .method(method)
                        .amount(amount)
                        .build();

                sale.addPayment(payment);

                paidTotal = paidTotal.add(amount).setScale(2, RoundingMode.HALF_UP);
                validPayments++;
                if (firstMethod == null) {
                    firstMethod = method;
                }
            }
        }

        if (validPayments == 0 && total.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Debes registrar al menos un método de pago.");
        }

        if (paidTotal.compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    "La suma de pagos debe ser igual al total de la venta. Total: "
                            + total + ", pagado: " + paidTotal
            );
        }

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            sale.setMetodoPago("FREE");
            sale.setCashReceived(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            sale.setChangeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return;
        }

        sale.setMetodoPago(validPayments > 1 ? "MIXED" : firstMethod);

        BigDecimal cashAmount = sale.getPayments().stream()
                .filter(payment -> isCashMethod(payment.getMethod()))
                .map(SalePayment::getAmount)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashReceived = safe(request.getCashReceived()).setScale(2, RoundingMode.HALF_UP);
        if (cashAmount.compareTo(BigDecimal.ZERO) > 0 && cashReceived.compareTo(cashAmount) < 0) {
            throw new IllegalArgumentException("El efectivo recibido no puede ser menor al monto pagado en efectivo.");
        }

        if (cashAmount.compareTo(BigDecimal.ZERO) == 0) {
            sale.setCashReceived(total);
            sale.setChangeAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        } else {
            BigDecimal change = cashReceived.subtract(cashAmount).setScale(2, RoundingMode.HALF_UP);
            if (change.compareTo(BigDecimal.ZERO) < 0) {
                change = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            sale.setCashReceived(cashReceived);
            sale.setChangeAmount(change);
        }
    }

    private boolean isCashMethod(String method) {
        String code = normalizeMethod(method);
        return "CASH".equals(code) || "EFECTIVO".equals(code);
    }

    private LocalDateTime resolveSaleDate(Long tenantId, CreateCashSaleRequest request) {
        LocalDateTime now = tenantTimeService.now(tenantId);

        if (request.getSaleDate() == null) {
            return now;
        }

        LocalDateTime saleDate = request.getSaleDate();

        if (saleDate.isAfter(now.plusMinutes(5))) {
            throw new IllegalArgumentException("La fecha de venta no puede ser futura.");
        }

        LocalDate minAllowedDate = now.toLocalDate().minusDays(30);
        if (saleDate.toLocalDate().isBefore(minAllowedDate)) {
            throw new IllegalArgumentException("Solo puedes registrar ventas de hasta 30 días atrás.");
        }

        return saleDate;
    }


    private LocalDateTime resolveBusinessDate(Sale sale) {
        return sale.getSaleDate() != null ? sale.getSaleDate() : sale.getFechaCreacion();
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
                .tipAmount(safe(sale.getTipAmount()))
                .tipBarberUserId(sale.getTipBarberUser() != null ? sale.getTipBarberUser().getId() : null)
                .tipBarberUserName(sale.getTipBarberUser() != null ? sale.getTipBarberUser().getNombre() : null)
                .total(safe(sale.getTotal()))
                .cashReceived(safe(sale.getCashReceived()))
                .changeAmount(safe(sale.getChangeAmount()))
                .fechaCreacion(resolveBusinessDate(sale))
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
                .payments(
                        sale.getPayments() == null
                                ? List.of()
                                : sale.getPayments().stream().map(payment ->
                                SalePaymentResponse.builder()
                                        .id(payment.getId())
                                        .method(payment.getMethod())
                                        .amount(safe(payment.getAmount()))
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

        String code = metodoPago.trim().toUpperCase();
        if ("EFECTIVO".equals(code)) return "CASH";
        if ("TARJETA".equals(code)) return "CARD";
        if ("TRANSFERENCIA".equals(code)) return "TRANSFER";
        return code;
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
        requireOwnerForSensitiveSaleAction(tenantId, userId);

        // 1) Cargamos venta con items/productos/cliente/caja/cita.
        Sale sale = saleRepository.findForDeleteWithItems(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        // 2) Inicializamos payments en una segunda query para evitar MultipleBagFetchException.
        saleRepository.findForDeleteWithPayments(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (sale.getCashRegister() == null) {
            throw new RuntimeException("La venta no pertenece a una caja");
        }

        if (sale.getCashRegister().getClosedAt() != null) {
            throw new RuntimeException("Solo se puede eliminar una venta con caja abierta");
        }

        // 3) Si la venta tenía productos, devolvemos el stock.
        restoreProductStockFromSale(tenantId, branchId, userId, sale);

        // 4) Revertimos puntos del cliente si corresponde.
        if (sale.getCustomer() != null) {
            AppUser actor = sale.getUser();
            loyaltyService.revertSalePoints(sale.getTenant(), sale.getCustomer(), actor, sale);
        }

        // 5) Eliminamos historial de corte relacionado.
        customerCutHistoryService.deleteBySale(tenantId, saleId);

        // 6) Si venía de una cita, la regresamos a CONFIRMADO.
        if (sale.getAppointment() != null) {
            sale.getAppointment().setEstado("CONFIRMADO");
            appointmentRepository.save(sale.getAppointment());
        }

        // 7) Primero borrar movimientos de stock que apuntan a esta venta.
        // Esto evita error de FK: stock_movement.sale_id todavía referencia sale.
        stockMovementRepository.deleteBySaleId(saleId);

        // 8) Ya NO hagas sale.getPayments().clear().
        // Sale tiene cascade = ALL y orphanRemoval = true para items y payments,
        // entonces Hibernate puede borrar sale_item y sale_payment con saleRepository.delete(sale).
        saleRepository.delete(sale);
        saleRepository.flush();
    }

    private void requireOwnerForSensitiveSaleAction(Long tenantId, Long userId) {
        boolean isOwner = userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                userId,
                tenantId,
                List.of(RoleType.OWNER)
        );

        if (!isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Solo el dueño puede editar o eliminar ventas.");
        }
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
                    // No enlazar esta devolución a la venta eliminada.
                    // Si queda sale_id, PostgreSQL no permitirá borrar la venta.
                    .sale(null)
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
