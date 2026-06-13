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
import com.gods.saas.service.impl.impl.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

@Service
@RequiredArgsConstructor
@Transactional
public class CashSaleServiceImpl implements CashSaleService {
    private static final BigDecimal DEFAULT_POINTS_PER_CURRENCY_UNIT = BigDecimal.valueOf(5);
    private static final String POINTS_PER_CURRENCY_UNIT_KEY = "loyaltyPointsPerCurrencyUnit";
    private static final String WHATSAPP_POST_SALE_MESSAGE_ENABLED_KEY = "whatsappPostSaleMessageEnabled";
    private static final String WHATSAPP_INCLUDE_APP_DOWNLOAD_LINK_KEY = "whatsappIncludeAppDownloadLink";
    private static final String WHATSAPP_INCLUDE_BOOKING_LINK_KEY = "whatsappIncludeBookingLink";
    private static final String WHATSAPP_APP_DOWNLOAD_URL_KEY = "whatsappAppDownloadUrl";
    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();
    private final NotificationService notificationService;

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

        if (!"PENDING_VALIDATION".equals(resolveValidationStatus(savedSale))) {
            String whatsappMessage = buildPostSaleWhatsappMessage(
                    savedSale,
                    response.getPuntosGanados() == null ? 0 : response.getPuntosGanados()
            );

            if (whatsappMessage != null && !whatsappMessage.trim().isEmpty()) {
                notificationService.notifySaleReceipt(savedSale, whatsappMessage);
            }
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
        LocalDate tenantToday = now.toLocalDate();
        LocalDate selectedDate = saleDate.toLocalDate();

        /*
         * Corrección timezone móvil/web:
         * Android y algunos navegadores pueden enviar la fecha de "hoy" con una hora
         * futura o con desfase por zona horaria. Si el día seleccionado es hoy para
         * el tenant, no rechazamos la venta por hora futura; usamos la hora actual
         * del tenant para guardar una fecha válida del mismo día.
         *
         * Ejemplo: tenant Venezuela (America/Caracas) selecciona hoy, pero el móvil
         * envía 2026-06-10T23:59:00 cuando aún son las 10:00. Antes fallaba como
         * fecha futura. Ahora se guarda con la hora actual del tenant.
         */
        if (selectedDate.equals(tenantToday)) {
            return now;
        }

        if (saleDate.isAfter(now.plusMinutes(5))) {
            throw new IllegalArgumentException("La fecha de venta no puede ser futura.");
        }

        LocalDate minAllowedDate = tenantToday.minusDays(30);
        if (selectedDate.isBefore(minAllowedDate)) {
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

        Map<String, Object> whatsappConfig = resolveWhatsappConfig(sale.getTenant());
        if (!readBooleanConfig(whatsappConfig, WHATSAPP_POST_SALE_MESSAGE_ENABLED_KEY, true)) {
            return null;
        }

        String tenantName = sale.getTenant() != null ? cleanText(sale.getTenant().getNombre()) : null;
        if (tenantName == null) {
            tenantName = "Super Gods";
        }

        String customerName = buildCustomerFirstName(sale.getCustomer());
        int balance = resolveCustomerPointsBalance(sale);
        String tenantCode = sale.getTenant() == null ? null : cleanText(sale.getTenant().getCodigo());
        String bookingUrl = buildBookingUrl(sale);
        boolean includeAppLink = readBooleanConfig(whatsappConfig, WHATSAPP_INCLUDE_APP_DOWNLOAD_LINK_KEY, true);
        boolean includeBookingLink = readBooleanConfig(whatsappConfig, WHATSAPP_INCLUDE_BOOKING_LINK_KEY, true);

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

        if (includeAppLink) {
            message.append("\n\nDescarga la app movil de Super Gods para ver tus puntos, premios y reservas:");
            message.append("\n");
            message.append(resolveMobileAppDownloadUrl(whatsappConfig));

            if (tenantCode != null) {
                message.append("\n\nPara ver tus puntos en la app:");
                message.append("\n1. Ingresa como cliente.");
                message.append("\n2. Coloca el codigo del negocio: ");
                message.append(tenantCode);
                message.append("\n3. Revisa tus puntos, premios y proximas reservas.");
            }
        }

        if (includeBookingLink && bookingUrl != null) {
            message.append("\n\nTambien puedes reservar tu proxima cita desde la app o por este link y seguir ganando puntos:");
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
        String cleanPhone = rawPhone == null ? "" : rawPhone.trim();
        if (cleanPhone.isBlank()) {
            return null;
        }

        String region = resolveTenantPhoneRegion(tenant);

        try {
            String parseInput = cleanPhone;
            String digitsOnly = cleanPhone.replaceAll("[^0-9]", "");

            if (cleanPhone.startsWith("00") && digitsOnly.length() > 2) {
                parseInput = "+" + digitsOnly.substring(2);
            }

            Phonenumber.PhoneNumber parsed = PHONE_NUMBER_UTIL.parse(
                    parseInput,
                    parseInput.startsWith("+") ? null : region
            );

            if (PHONE_NUMBER_UTIL.isValidNumber(parsed)) {
                return PHONE_NUMBER_UTIL
                        .format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                        .replace("+", "");
            }
        } catch (NumberParseException ignored) {
            // Fallback manual abajo para no romper el flujo de venta/WhatsApp.
        }

        return normalizeWhatsappPhoneFallback(cleanPhone, region);
    }

    private String normalizeWhatsappPhoneFallback(String rawPhone, String region) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }

        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }

        // Si ya parece venir con codigo internacional, lo respetamos.
        if (digits.length() >= 11) {
            return digits;
        }

        String prefix = phonePrefixByRegion(region);
        if (prefix == null) {
            return digits;
        }

        if (digits.startsWith(prefix)) {
            return digits;
        }

        // Muchos paises escriben el movil local con 0 inicial: VE 0412..., UK 07..., etc.
        // Si libphonenumber no pudo parsear, quitamos ese trunk solo como fallback.
        if (digits.startsWith("0") && digits.length() > 1) {
            digits = digits.substring(1);
        }

        return prefix + digits;
    }

    private String resolveTenantPhoneRegion(Tenant tenant) {
        String country = tenant == null ? null : cleanText(tenant.getPais());

        if (country == null && tenant != null && tenant.getId() != null) {
            country = tenantSettingsRepository.findByTenant_Id(tenant.getId())
                    .map(settings -> {
                        Map<String, Object> config = settings.getScheduleConfig();
                        Object raw = firstNonNullConfig(
                                config,
                                "phoneCountry",
                                "phoneCountryCode",
                                "countryCode",
                                "country",
                                "pais"
                        );

                        String fromConfig = raw == null ? null : cleanText(raw.toString());
                        if (fromConfig != null) return fromConfig;

                        String fromTimezone = regionFromTimezone(settings.getTimezone());
                        if (fromTimezone != null) return fromTimezone;

                        return regionFromCurrency(settings.getCurrency());
                    })
                    .orElse(null);
        }

        return countryToRegion(country);
    }

    private String regionFromTimezone(String timezone) {
        String zone = cleanText(timezone);
        if (zone == null) return null;

        return switch (zone) {
            case "America/Lima" -> "PE";
            case "America/Caracas" -> "VE";
            case "America/Bogota" -> "CO";
            case "America/Guayaquil" -> "EC";
            case "America/Santiago" -> "CL";
            case "America/Argentina/Buenos_Aires" -> "AR";
            case "America/La_Paz" -> "BO";
            case "America/Sao_Paulo" -> "BR";
            case "America/Montevideo" -> "UY";
            case "America/Asuncion" -> "PY";
            case "America/Panama" -> "PA";
            case "America/Costa_Rica" -> "CR";
            case "America/Managua" -> "NI";
            case "America/Tegucigalpa" -> "HN";
            case "America/El_Salvador" -> "SV";
            case "America/Guatemala" -> "GT";
            case "America/Belize" -> "BZ";
            case "America/Mexico_City" -> "MX";
            case "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles" -> "US";
            case "Europe/Madrid" -> "ES";
            case "Europe/Lisbon" -> "PT";
            case "Europe/Paris" -> "FR";
            case "Europe/Rome" -> "IT";
            case "Europe/Berlin" -> "DE";
            case "Europe/London" -> "GB";
            default -> null;
        };
    }

    private String regionFromCurrency(String currency) {
        String code = normalizeCountryText(currency);
        if (code == null) return null;

        return switch (code) {
            case "PEN" -> "PE";
            case "VES" -> "VE";
            case "COP" -> "CO";
            case "USD" -> "US";
            case "MXN" -> "MX";
            case "CLP" -> "CL";
            case "ARS" -> "AR";
            case "BOB" -> "BO";
            case "BRL" -> "BR";
            case "UYU" -> "UY";
            case "PYG" -> "PY";
            case "EUR" -> null; // Europa usa EUR en varios paises: preferir pais/timezone.
            default -> null;
        };
    }

    private Object firstNonNullConfig(Map<String, Object> config, String... keys) {
        if (config == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (key != null && config.containsKey(key) && config.get(key) != null) {
                return config.get(key);
            }
        }

        return null;
    }

    private String phonePrefixByRegion(String region) {
        String cleanRegion = countryToRegion(region);
        if (cleanRegion == null) {
            return null;
        }

        int prefix = PHONE_NUMBER_UTIL.getCountryCodeForRegion(cleanRegion);
        return prefix > 0 ? String.valueOf(prefix) : null;
    }

    private String countryToRegion(String country) {
        String code = normalizeCountryText(country);
        if (code == null) {
            return null;
        }

        if (code.length() == 2 && isValidIsoRegion(code)) {
            return code;
        }

        return switch (code) {
            // Sudamerica
            case "PERU" -> "PE";
            case "VENEZUELA", "VEN", "REPUBLICA BOLIVARIANA DE VENEZUELA" -> "VE";
            case "COLOMBIA" -> "CO";
            case "ECUADOR" -> "EC";
            case "CHILE" -> "CL";
            case "ARGENTINA" -> "AR";
            case "BOLIVIA" -> "BO";
            case "BRASIL", "BRAZIL" -> "BR";
            case "URUGUAY" -> "UY";
            case "PARAGUAY" -> "PY";
            case "GUYANA" -> "GY";
            case "SURINAME", "SURINAM" -> "SR";
            case "GUAYANA FRANCESA", "FRENCH GUIANA" -> "GF";

            // Centroamerica, Norteamerica y Caribe frecuente
            case "PANAMA" -> "PA";
            case "COSTA RICA" -> "CR";
            case "NICARAGUA" -> "NI";
            case "HONDURAS" -> "HN";
            case "EL SALVADOR", "SALVADOR" -> "SV";
            case "GUATEMALA" -> "GT";
            case "BELICE", "BELIZE" -> "BZ";
            case "MEXICO" -> "MX";
            case "ESTADOS UNIDOS", "UNITED STATES", "USA", "US", "EEUU", "EE UU" -> "US";
            case "CANADA" -> "CA";
            case "REPUBLICA DOMINICANA", "DOMINICAN REPUBLIC" -> "DO";
            case "PUERTO RICO" -> "PR";
            case "CUBA" -> "CU";
            case "JAMAICA" -> "JM";
            case "HAITI" -> "HT";
            case "TRINIDAD Y TOBAGO", "TRINIDAD AND TOBAGO" -> "TT";

            // Europa: si guardas ISO2 funciona para todos. Estos aliases cubren nombres comunes.
            case "ESPANA", "SPAIN" -> "ES";
            case "PORTUGAL" -> "PT";
            case "FRANCIA", "FRANCE" -> "FR";
            case "ITALIA", "ITALY" -> "IT";
            case "ALEMANIA", "GERMANY", "DEUTSCHLAND" -> "DE";
            case "REINO UNIDO", "UNITED KINGDOM", "UK", "INGLATERRA", "ENGLAND", "GRAN BRETANA", "GREAT BRITAIN" -> "GB";
            case "IRLANDA", "IRELAND" -> "IE";
            case "PAISES BAJOS", "HOLANDA", "NETHERLANDS" -> "NL";
            case "BELGICA", "BELGIUM" -> "BE";
            case "SUIZA", "SWITZERLAND" -> "CH";
            case "AUSTRIA" -> "AT";
            case "DINAMARCA", "DENMARK" -> "DK";
            case "SUECIA", "SWEDEN" -> "SE";
            case "NORUEGA", "NORWAY" -> "NO";
            case "FINLANDIA", "FINLAND" -> "FI";
            case "ISLANDIA", "ICELAND" -> "IS";
            case "POLONIA", "POLAND" -> "PL";
            case "CHEQUIA", "REPUBLICA CHECA", "CZECH REPUBLIC", "CZECHIA" -> "CZ";
            case "ESLOVAQUIA", "SLOVAKIA" -> "SK";
            case "HUNGRIA", "HUNGARY" -> "HU";
            case "RUMANIA", "ROMANIA" -> "RO";
            case "BULGARIA" -> "BG";
            case "GRECIA", "GREECE" -> "GR";
            case "CROACIA", "CROATIA" -> "HR";
            case "ESLOVENIA", "SLOVENIA" -> "SI";
            case "SERBIA" -> "RS";
            case "MONTENEGRO" -> "ME";
            case "BOSNIA", "BOSNIA Y HERZEGOVINA", "BOSNIA AND HERZEGOVINA" -> "BA";
            case "ALBANIA" -> "AL";
            case "MACEDONIA", "NORTH MACEDONIA", "MACEDONIA DEL NORTE" -> "MK";
            case "MALTA" -> "MT";
            case "LUXEMBURGO", "LUXEMBOURG" -> "LU";
            case "LIECHTENSTEIN" -> "LI";
            case "MONACO" -> "MC";
            case "ANDORRA" -> "AD";
            case "SAN MARINO" -> "SM";
            case "VATICANO", "VATICAN", "HOLY SEE" -> "VA";
            case "ESTONIA" -> "EE";
            case "LETONIA", "LATVIA" -> "LV";
            case "LITUANIA", "LITHUANIA" -> "LT";
            case "UCRANIA", "UKRAINE" -> "UA";
            case "MOLDAVIA", "MOLDOVA" -> "MD";
            case "BIELORRUSIA", "BELARUS" -> "BY";
            case "RUSIA", "RUSSIA" -> "RU";
            case "TURQUIA", "TURKEY", "TURKIYE" -> "TR";
            case "CHIPRE", "CYPRUS" -> "CY";
            case "GEORGIA" -> "GE";
            case "ARMENIA" -> "AM";
            case "AZERBAIYAN", "AZERBAIJAN" -> "AZ";
            default -> null;
        };
    }

    private String normalizeCountryText(String value) {
        String clean = cleanText(value);
        if (clean == null) {
            return null;
        }

        String normalized = Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ");

        return normalized.isBlank() ? null : normalized;
    }

    private boolean isValidIsoRegion(String region) {
        if (region == null || region.length() != 2) {
            return false;
        }

        for (String iso : Locale.getISOCountries()) {
            if (iso.equals(region)) {
                return true;
            }
        }

        return false;
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

    private Map<String, Object> resolveWhatsappConfig(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            return Map.of();
        }

        return tenantSettingsRepository.findByTenant_Id(tenant.getId())
                .map(TenantSettings::getScheduleConfig)
                .orElse(Map.of());
    }

    private boolean readBooleanConfig(Map<String, Object> config, String key, boolean fallback) {
        if (config == null || !config.containsKey(key)) {
            return fallback;
        }

        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }

        return fallback;
    }

    private String readStringConfig(Map<String, Object> config, String key) {
        if (config == null || !config.containsKey(key)) {
            return null;
        }

        Object value = config.get(key);
        return value == null ? null : cleanText(value.toString());
    }

    private String resolveMobileAppDownloadUrl(Map<String, Object> config) {
        String url = cleanText(readStringConfig(config, WHATSAPP_APP_DOWNLOAD_URL_KEY));
        if (url == null) {
            url = cleanText(mobileAppDownloadUrl);
        }
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

        if (isCourtesySale(sale)) {
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

        if (isCourtesySale(sale)) {
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

    private boolean isCourtesySale(Sale sale) {
        if (sale == null) {
            return false;
        }
        String code = normalizeMethod(sale.getMetodoPago());
        return "FREE".equals(code)
                || "GRATIS".equals(code)
                || "CORTESIA".equals(code)
                || "CORTESÍA".equals(code);
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
        if (sale.getItems() != null) {
            LinkedHashSet<String> itemBarbers = sale.getItems().stream()
                    .filter(item -> item.getBarberUser() != null)
                    .map(item -> item.getBarberUser().getNombre())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!itemBarbers.isEmpty()) {
                return String.join(", ", itemBarbers);
            }
        }

        if (sale.getUser() != null
                && sale.getUser().getNombre() != null
                && !sale.getUser().getNombre().trim().isEmpty()) {
            return sale.getUser().getNombre().trim();
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
