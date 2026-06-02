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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSaleServiceImpl implements CashSaleService {
    private static final BigDecimal DEFAULT_POINTS_PER_CURRENCY_UNIT = BigDecimal.valueOf(5);
    private static final String POINTS_PER_CURRENCY_UNIT_KEY = "loyaltyPointsPerCurrencyUnit";

    @Value("${app.mobile.download-url:https://play.google.com/store/apps/details?id=com.gods.barberia}")
    private String mobileAppDownloadUrl;

    @Value("${app.public.base-url:https://www.supergodsapp.com}")
    private String publicBaseUrl;

    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleService saleService;
    private final CustomerRepository customerRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final ProductBranchStockRepository productBranchStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final TenantTimeService tenantTimeService;
    private final CustomerCutHistoryService customerCutHistoryService;
    private final LoyaltyService loyaltyService;
    private final BranchRepository branchRepository;
    private final AppUserRepository userRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

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

        String creatorRole = resolveCashSaleCreatorRole(tenantId, userId, request);
        saleRequest.setCreatedByRole(creatorRole);
        saleRequest.setPaymentValidationStatus(resolveCashSaleValidationStatus(
                request.getPaymentValidationStatus(),
                creatorRole
        ));

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
                .filter(this::isApprovedSale)
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
                .filter(this::isApprovedSale)
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
                .filter(this::isApprovedSale)
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
    @Transactional(readOnly = true)
    public List<SaleResponse> getPendingValidationSales(Long tenantId, Long branchId) {
        return saleRepository.findPendingValidationSales(tenantId, branchId)
                .stream()
                .map(sale -> mapResponse(sale, calculatePendingPointsPreview(sale)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SaleResponse approveSalePayment(Long tenantId, Long branchId, Long userId, Long saleId) {
        requireOwnerForSensitiveSaleAction(tenantId, userId);

        Sale sale = saleRepository.findByIdAndTenant_IdAndBranch_Id(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (!"PENDING_VALIDATION".equals(resolveValidationStatus(sale))) {
            throw new RuntimeException("Esta venta ya no está pendiente de validación.");
        }

        AppUser validator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario validador no encontrado"));

        sale.setPaymentValidationStatus("APPROVED");
        sale.setValidatedByUser(validator);
        sale.setValidatedAt(tenantTimeService.now(tenantId));
        sale.setRejectionReason(null);

        Sale saved = saleRepository.save(sale);

        int puntosGanados = grantPointsAfterApproval(saved, validator);

        return mapResponse(saved, puntosGanados);
    }

    @Override
    @Transactional
    public SaleResponse rejectSalePayment(Long tenantId, Long branchId, Long userId, Long saleId, String reason) {
        requireOwnerForSensitiveSaleAction(tenantId, userId);

        Sale sale = saleRepository.findByIdAndTenant_IdAndBranch_Id(saleId, tenantId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (!"PENDING_VALIDATION".equals(resolveValidationStatus(sale))) {
            throw new RuntimeException("Esta venta ya no está pendiente de validación.");
        }

        AppUser validator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario validador no encontrado"));

        sale.setPaymentValidationStatus("REJECTED");
        sale.setValidatedByUser(validator);
        sale.setValidatedAt(tenantTimeService.now(tenantId));
        sale.setRejectionReason(reason == null || reason.trim().isEmpty()
                ? "Pago rechazado por el dueño/administrador."
                : reason.trim());

        Sale saved = saleRepository.save(sale);
        return mapResponse(saved, 0);
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


    private String resolveCashSaleCreatorRole(Long tenantId, Long userId, CreateCashSaleRequest request) {
        // Fuente de verdad: user_tenant_roles.
        if (hasTenantRole(userId, tenantId, RoleType.BARBER)) {
            return "BARBER";
        }

        if (hasTenantRole(userId, tenantId, RoleType.ADMIN)) {
            return "ADMIN";
        }
        

        if (hasTenantRole(userId, tenantId, RoleType.OWNER)) {
            return "OWNER";
        }

        // Fallback para compatibilidad si aun no existe registro en user_tenant_roles.
        AppUser creator = userId == null
                ? null
                : userRepository.findById(userId).orElse(null);

        String userRole = creator != null && creator.getRol() != null
                ? creator.getRol().trim().toUpperCase()
                : "";

        if (isBarberRole(userRole)) {
            return "BARBER";
        }

        String requestedRole = request != null && request.getCreatedByRole() != null
                ? request.getCreatedByRole().trim().toUpperCase()
                : "";

        if (isBarberRole(requestedRole)) {
            return "BARBER";
        }

        if ("ADMIN".equals(requestedRole)) {
            return "ADMIN";
        }

        if ("CASHIER".equals(requestedRole) || "CAJERO".equals(requestedRole)) {
            return "CASHIER";
        }

        return "OWNER";
    }

    private String resolveCashSaleValidationStatus(String requestedStatus, String creatorRole) {
        String role = creatorRole == null ? "" : creatorRole.trim().toUpperCase();
        String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();

        if ("BARBER".equals(role)) {
            return "PENDING_VALIDATION";
        }

        return switch (status) {
            case "PENDING_VALIDATION", "PENDING", "PENDIENTE" -> "PENDING_VALIDATION";
            case "REJECTED", "RECHAZADO" -> "REJECTED";
            default -> "APPROVED";
        };
    }

    private boolean hasTenantRole(Long userId, Long tenantId, RoleType role) {
        if (userId == null || tenantId == null || role == null) {
            return false;
        }

        return userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                userId,
                tenantId,
                List.of(role)
        );
    }

    private boolean isBarberRole(String role) {
        if (role == null) {
            return false;
        }

        String cleanRole = role.trim().toUpperCase();
        return "BARBER".equals(cleanRole)
                || "PROFESSIONAL".equals(cleanRole)
                || "PROFESIONAL".equals(cleanRole);
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
        boolean pendingValidation = "PENDING_VALIDATION".equals(resolveValidationStatus(sale));
        int pointsValue = puntosGanados == null ? 0 : puntosGanados;
        String customerWhatsappMessage = pendingValidation
                ? null
                : buildPostSaleWhatsappMessage(sale, pointsValue);
        String customerWhatsappUrl = buildCustomerWhatsappUrl(sale, customerWhatsappMessage);

        return SaleResponse.builder()
                .saleId(sale.getId())
                .cashRegisterId(sale.getCashRegister() != null ? sale.getCashRegister().getId() : null)
                .customerId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .customerName(buildCustomerFullName(sale.getCustomer()))
                .customerPhone(resolveCustomerPhone(sale.getCustomer()))
                .customerWhatsappMessage(customerWhatsappMessage)
                .customerWhatsappUrl(customerWhatsappUrl)
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
                .puntosGanados(pendingValidation ? 0 : pointsValue)
                .puntosPendientes(pendingValidation ? pointsValue : 0)
                .puntosDisponibles(resolveCustomerPointsBalance(sale))
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
                .paymentValidationStatus(resolveValidationStatus(sale))
                .validatedByUserId(sale.getValidatedByUser() != null ? sale.getValidatedByUser().getId() : null)
                .validatedByUserName(sale.getValidatedByUser() != null ? sale.getValidatedByUser().getNombre() : null)
                .validatedAt(sale.getValidatedAt())
                .rejectionReason(sale.getRejectionReason())
                .createdByRole(sale.getCreatedByRole())
                .build();
    }


    private String buildPostSaleWhatsappMessage(Sale sale, int pointsEarned) {
        if (sale == null || sale.getCustomer() == null) {
            return null;
        }

        String tenantName = sale.getTenant() != null ? cleanText(sale.getTenant().getNombre()) : null;
        if (tenantName == null) {
            tenantName = "Super Gods";
        }

        String customerName = buildCustomerFirstName(sale.getCustomer());
        int balance = resolveCustomerPointsBalance(sale);
        String bookingUrl = buildBookingUrl(sale);

        StringBuilder message = new StringBuilder();
        message.append("Hola ");
        message.append(customerName == null ? "cliente" : customerName);
        message.append(", gracias por tu visita a ");
        message.append(tenantName);
        message.append(".");

        if (pointsEarned > 0) {
            message.append("\n\nGanaste +");
            message.append(pointsEarned);
            message.append(" puntos por tu compra.");
        }

        if (balance > 0) {
            message.append("\nAhora tienes ");
            message.append(balance);
            message.append(" puntos disponibles.");
        }

        message.append("\n\nDescarga la app movil de Super Gods para ver tus puntos, premios y reservas:");
        message.append("\n");
        message.append(resolveMobileAppDownloadUrl());

        if (bookingUrl != null) {
            message.append("\n\nReserva tu proxima cita aqui:");
            message.append("\n");
            message.append(bookingUrl);
        }

        message.append("\n\nTe esperamos pronto.");
        return message.toString();
    }

    private String buildCustomerWhatsappUrl(Sale sale, String message) {
        if (sale == null || sale.getCustomer() == null || message == null || message.isBlank()) {
            return null;
        }

        String phone = normalizeWhatsappPhone(sale.getCustomer().getTelefono(), sale.getTenant());
        if (phone == null) {
            return null;
        }

        return "https://wa.me/" + phone + "?text="
                + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }

    private String normalizeWhatsappPhone(String rawPhone, Tenant tenant) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }

        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }

        if (digits.length() >= 11) {
            return digits;
        }

        String countryCode = tenant == null ? null : cleanText(tenant.getPais());
        String prefix = whatsappCountryPrefix(countryCode);
        if (prefix == null) {
            return digits;
        }

        if (digits.startsWith(prefix)) {
            return digits;
        }

        return prefix + digits;
    }

    private String whatsappCountryPrefix(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }

        return switch (countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "PE", "PERU" -> "51";
            case "CO", "COLOMBIA" -> "57";
            case "MX", "MEXICO" -> "52";
            case "CL", "CHILE" -> "56";
            case "AR", "ARGENTINA" -> "54";
            case "BO", "BOLIVIA" -> "591";
            case "BR", "BRASIL", "BRAZIL" -> "55";
            case "UY", "URUGUAY" -> "598";
            case "PY", "PARAGUAY" -> "595";
            case "CR", "COSTA RICA" -> "506";
            case "DO", "REPUBLICA DOMINICANA", "DOMINICAN REPUBLIC" -> "1";
            case "GT", "GUATEMALA" -> "502";
            case "US", "USA", "UNITED STATES" -> "1";
            default -> null;
        };
    }

    private String buildBookingUrl(Sale sale) {
        if (sale == null || sale.getTenant() == null) {
            return null;
        }

        String code = cleanText(sale.getTenant().getCodigo());
        if (code == null) {
            return null;
        }

        return resolvePublicBaseUrl() + "/reservar/" + code;
    }

    private String resolveMobileAppDownloadUrl() {
        String url = cleanText(mobileAppDownloadUrl);
        return url == null ? "https://play.google.com/store/apps/details?id=com.gods.barberia" : url;
    }

    private String resolvePublicBaseUrl() {
        String url = cleanText(publicBaseUrl);
        if (url == null) {
            return "https://www.supergodsapp.com";
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String resolveCustomerPhone(Customer customer) {
        return customer == null ? null : cleanText(customer.getTelefono());
    }

    private String buildCustomerFirstName(Customer customer) {
        String fullName = buildCustomerFullName(customer);
        if (fullName == null) {
            return null;
        }

        String[] parts = fullName.split("\\s+");
        return parts.length == 0 ? fullName : parts[0];
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }

        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }


    private int calculatePendingPointsPreview(Sale sale) {
        if (sale == null || sale.getCustomer() == null) {
            return 0;
        }

        BigDecimal servicePointsBase = calculateServicePointsBase(sale);
        if (servicePointsBase.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        // Preview simple: mismo criterio configurado que se usa al aprobar.
        // No guarda nada en BD. Solo sirve para mostrar:
        // puntos actuales + puntos que ganara si se aprueba.
        return servicePointsBase
                .multiply(resolvePointsPerCurrencyUnit(sale.getTenant()))
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
    }

    private BigDecimal resolvePointsPerCurrencyUnit(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return DEFAULT_POINTS_PER_CURRENCY_UNIT;
        }

        return tenantSettingsRepository.findByTenant_Id(tenant.getId())
                .map(settings -> {
                    Map<String, Object> config = settings.getScheduleConfig();
                    if (config == null) {
                        return DEFAULT_POINTS_PER_CURRENCY_UNIT;
                    }

                    Object raw = config.get(POINTS_PER_CURRENCY_UNIT_KEY);
                    if (raw == null) {
                        return DEFAULT_POINTS_PER_CURRENCY_UNIT;
                    }

                    try {
                        BigDecimal value = new BigDecimal(raw.toString());
                        return value.compareTo(BigDecimal.ZERO) < 0
                                ? BigDecimal.ZERO
                                : value;
                    } catch (Exception ignored) {
                        return DEFAULT_POINTS_PER_CURRENCY_UNIT;
                    }
                })
                .orElse(DEFAULT_POINTS_PER_CURRENCY_UNIT);
    }

    private int resolveCustomerPointsBalance(Sale sale) {
        if (sale == null || sale.getTenant() == null || sale.getCustomer() == null) {
            return 0;
        }

        return loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(sale.getTenant().getId(), sale.getCustomer().getId())
                .map(account -> account.getPuntosDisponibles() == null ? 0 : account.getPuntosDisponibles())
                .orElse(0);
    }

    private int grantPointsAfterApproval(Sale sale, AppUser validator) {
        if (sale == null || sale.getCustomer() == null) {
            return 0;
        }

        BigDecimal servicePointsBase = calculateServicePointsBase(sale);
        if (servicePointsBase.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        return loyaltyService.grantSalePoints(
                sale.getTenant(),
                sale.getCustomer(),
                validator,
                sale,
                servicePointsBase.doubleValue()
        );
    }

    private BigDecimal calculateServicePointsBase(Sale sale) {
        if (sale == null || sale.getItems() == null || sale.getItems().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return sale.getItems().stream()
                .filter(item -> item.getService() != null)
                .map(SaleItem::getSubtotal)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isApprovedSale(Sale sale) {
        return "APPROVED".equals(resolveValidationStatus(sale));
    }

    private String resolveValidationStatus(Sale sale) {
        if (sale == null || sale.getPaymentValidationStatus() == null || sale.getPaymentValidationStatus().isBlank()) {
            return "APPROVED";
        }
        return sale.getPaymentValidationStatus();
    }

    private String buildCustomerFullName(Customer customer) {
        if (customer == null) {
            return null;
        }

        String nombres = customer.getNombres() == null ? "" : customer.getNombres().trim();
        String apellidos = customer.getApellidos() == null ? "" : customer.getApellidos().trim();

        String fullName = (nombres + " " + apellidos).trim();
        return fullName.isEmpty() ? null : fullName;
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
        boolean canValidate = userTenantRoleRepository.existsByUserIdAndTenantIdAndRoleIn(
                userId,
                tenantId,
                List.of(RoleType.OWNER, RoleType.ADMIN)
        );

        if (!canValidate) {
            throw new org.springframework.security.access.AccessDeniedException("Solo el dueno o administrador puede validar ventas.");
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

            ProductBranchStock branchStock = getOrCreateBranchStock(sale.getTenant(), branch, product);

            int stockAnterior = branchStock.getStockActual() == null ? 0 : branchStock.getStockActual();
            int cantidad = item.getCantidad() == null ? 0 : item.getCantidad();
            int stockNuevo = stockAnterior + cantidad;

            branchStock.setStockActual(stockNuevo);
            productBranchStockRepository.save(branchStock);

            // Campo legacy para compatibilidad con pantallas antiguas. El stock real viene de product_branch_stock.
            product.setStockActual(stockNuevo);
            product.setStockMinimo(branchStock.getStockMinimo());
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

    private ProductBranchStock getOrCreateBranchStock(Tenant tenant, Branch branch, Product product) {
        return productBranchStockRepository
                .findByTenant_IdAndBranch_IdAndProduct_Id(tenant.getId(), branch.getId(), product.getId())
                .orElseGet(() -> productBranchStockRepository.save(
                        ProductBranchStock.builder()
                                .tenant(tenant)
                                .branch(branch)
                                .product(product)
                                .stockActual(product.getStockActual() == null ? 0 : product.getStockActual())
                                .stockMinimo(product.getStockMinimo() == null ? 0 : product.getStockMinimo())
                                .activo(Boolean.TRUE.equals(product.getActivo()))
                                .fechaCreacion(tenantTimeService.now(tenant.getId()))
                                .fechaActualizacion(tenantTimeService.now(tenant.getId()))
                                .build()
                ));
    }
}
