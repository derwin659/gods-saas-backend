package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.ActualizarClienteRequest;
import com.gods.saas.domain.dto.CambiarTelefonoRequest;
import com.gods.saas.domain.dto.ClienteResponse;
import com.gods.saas.domain.dto.VentaRapidaRequest;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.OtpCode;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.repository.projection.LastVisitProjection;
import com.gods.saas.domain.repository.projection.CustomerHistorySaleItemProjection;
import com.gods.saas.service.impl.impl.LoyaltyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CustomerRepository customerRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final SaleItemRepository saleItemRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final LoyaltyService loyaltyService;
    private final SaleRepository saleRepository;

    @Transactional
    public Customer registrarCliente(VentaRapidaRequest req) {
        throw new IllegalStateException("Usa registrarCliente(tenantId, req) para respetar multi-tenant");
    }

    @Transactional
    public Customer registrarCliente(Long tenantId, VentaRapidaRequest req) {
        if (tenantId == null) {
            throw new RuntimeException("tenantId es obligatorio");
        }

        if (req.getPhone() == null || req.getPhone().isBlank()) {
            throw new RuntimeException("El teléfono es obligatorio");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        String telefono = req.getPhone().trim();

        if (customerRepository.existsByTenant_IdAndTelefono(tenantId, telefono)) {
            throw new RuntimeException("El teléfono ya está registrado en esta barbería");
        }

        Customer newClient = Customer.builder()
                .tenant(tenant)
                .telefono(telefono)
                .nombres(req.getNombre())
                .apellidos(req.getApellido())
                .fechaNacimiento(req.getFechaNacimiento())
                .origenCliente(req.getOrigenCliente() != null ? req.getOrigenCliente() : "CAJA")
                .phoneVerified(false)
                .fechaRegistro(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .puntosDisponibles(0)
                .migrated(false)
                .appActivated(false)
                .welcomeBonusGranted(false)
                .activationBonusGranted(false)
                .activo(true)
                .source("INTERNAL")
                .build();

        return customerRepository.save(newClient);
    }

    @Transactional
    public Customer actualizarCliente(Long customerId, ActualizarClienteRequest req) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (req.getNombre() != null) customer.setNombres(req.getNombre());
        if (req.getApellido() != null) customer.setApellidos(req.getApellido());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getFechaNacimiento() != null) customer.setFechaNacimiento(req.getFechaNacimiento());

        customer.setFechaActualizacion(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer actualizarCliente(Long tenantId, Long customerId, ActualizarClienteRequest req) {
        Customer customer = customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (req.getNombre() != null && !req.getNombre().isBlank()) {
            customer.setNombres(req.getNombre().trim());
        }

        if (req.getApellido() != null) {
            customer.setApellidos(req.getApellido().trim());
        }

        if (req.getTelefono() != null) {
            String telefono = req.getTelefono().trim();

            if (telefono.isBlank()) {
                throw new RuntimeException("El teléfono es obligatorio");
            }

            customerRepository.findByTenant_IdAndTelefono(tenantId, telefono)
                    .filter(existing -> !existing.getId().equals(customerId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("El teléfono ya está registrado en esta barbería");
                    });

            customer.setTelefono(telefono);
        }

        if (req.getEmail() != null) {
            String email = req.getEmail().trim();
            if (!email.isBlank()) {
                customerRepository.findByEmailAndTenantId(email, tenantId)
                        .filter(existing -> !existing.getId().equals(customerId))
                        .ifPresent(existing -> {
                            throw new RuntimeException("El email ya está registrado en esta barbería");
                        });
                customer.setEmail(email);
            } else {
                customer.setEmail(null);
            }
        }

        if (req.getFechaNacimiento() != null) {
            customer.setFechaNacimiento(req.getFechaNacimiento());
        }

        customer.setFechaActualizacion(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    @Transactional
    public void eliminarClienteOwner(Long tenantId, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        customer.setActivo(false);
        customer.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(customer);
    }

    @Transactional
    public void deleteMyCustomerAccount(Long customerId, String confirmation) {
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente inválido");
        }

        final String confirm = confirmation == null ? "" : confirmation.trim().toUpperCase();
        if (!Objects.equals(confirm, "ELIMINAR")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes confirmar la eliminación escribiendo ELIMINAR"
            );
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        if (!Boolean.TRUE.equals(customer.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta ya fue eliminada");
        }

        String marker = "deleted_customer_" + customer.getId() + "_" + System.currentTimeMillis();

        customer.setNombres("Cuenta eliminada");
        customer.setApellidos("");
        customer.setTelefono(marker);
        customer.setEmail(marker + "@deleted.local");
        customer.setPhoneVerified(false);
        customer.setPhonePendiente(null);
        customer.setPhonePendienteVerificacion(false);
        customer.setAppActivated(false);
        customer.setActivo(false);
        customer.setFechaActualizacion(LocalDateTime.now());
        customer.setSource("DELETED");

        customerRepository.save(customer);
    }

    public List<Customer> listarClientesOwner(Long tenantId, String q, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        PageRequest pageable = PageRequest.of(0, safeLimit);

        if (q == null || q.isBlank()) {
            return customerRepository.findByTenant_IdAndActivoTrueOrderByFechaRegistroDesc(tenantId, pageable);
        }

        return customerRepository.searchByNameOrPhone(tenantId, q.strip(), pageable);
    }

    public Customer obtenerClienteOwner(Long tenantId, Long customerId) {
        return customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    public Customer obtenerClientePorTelefono(Long tenantId, String phone) {
        return customerRepository.findByTenant_IdAndTelefonoAndActivoTrue(tenantId, phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    public Customer getById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @Transactional
    public void solicitarCambioTelefono(String nuevoTelefono) {
        String code = String.format("%06d", new Random().nextInt(999999));

        OtpCode otp = OtpCode.builder()
                .phone(nuevoTelefono)
                .code(code)
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        otpCodeRepository.save(otp);
        System.out.println("🔐 OTP para cambio de teléfono: " + code);
    }

    @Transactional
    public Customer confirmarCambioTelefono(String nuevoTelefono, String code, Long tenantId) {
        OtpCode otp = otpCodeRepository
                .findTopByPhoneAndTenantIdAndUsedIsFalseOrderByCreatedAtDesc(nuevoTelefono, tenantId)
                .orElseThrow(() -> new RuntimeException("OTP no encontrado"));

        if (!otp.getCode().equals(code)) {
            throw new RuntimeException("Código incorrecto");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Código expirado");
        }

        Customer customer = customerRepository.findByPhonePendienteAndTenantId(nuevoTelefono, tenantId)
                .orElseThrow(() -> new RuntimeException("No hay cliente con solicitud de cambio"));

        customer.setTelefono(nuevoTelefono);
        customer.setPhonePendiente(null);
        customer.setPhonePendienteVerificacion(false);
        customer.setPhoneVerified(true);
        customer.setFechaActualizacion(LocalDateTime.now());

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        return customerRepository.save(customer);
    }

    @Transactional
    public Customer recuperarCuentaPorTelefono(CambiarTelefonoRequest req) {
        Customer customer = customerRepository.findByTelefono(req.getOldPhone())
                .orElseThrow(() -> new RuntimeException("No existe un cliente con ese teléfono antiguo"));

        customerRepository.findByTelefono(req.getNewPhone())
                .ifPresent(c -> {
                    throw new RuntimeException("El nuevo teléfono ya está en uso");
                });

        customer.setPhonePendiente(req.getNewPhone());
        customer.setPhonePendienteVerificacion(true);
        customer.setFechaActualizacion(LocalDateTime.now());
        customerRepository.save(customer);

        String code = String.format("%06d", new Random().nextInt(999999));

        OtpCode otp = OtpCode.builder()
                .phone(req.getNewPhone())
                .code(code)
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        otpCodeRepository.save(otp);

        System.out.println("🔐 OTP generado para recuperar cuenta con nuevo teléfono: " + code);

        return customer;
    }

    @Transactional
    public ClientLoginResponse verifyLoginOtp(Long otpId, String code) {
        OtpCode otp = otpCodeRepository.findById(otpId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OTP no encontrado"));

        if (Boolean.TRUE.equals(otp.getUsed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP ya usado");
        }

        if (otp.getExpiresAt() != null && otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código expirado");
        }

        if (otp.getCode() == null || !otp.getCode().equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código incorrecto");
        }

        Long tenantId = otp.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP inválido (sin tenant)");
        }

        Customer customer = customerRepository.findByTenantIdAndTelefonoWithTenant(tenantId, otp.getPhone())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        customer.setPhoneVerified(true);
        customer.setFechaActualizacion(LocalDateTime.now());

        if (!Boolean.TRUE.equals(customer.getAppActivated())) {
            customer.setAppActivated(true);
            customer.setAppActivatedAt(LocalDateTime.now());
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);
        customerRepository.save(customer);

        if (Boolean.TRUE.equals(customer.getMigrated())
                && !Boolean.TRUE.equals(customer.getActivationBonusGranted())) {
            loyaltyService.grantActivationBonusIfNeeded(customer);

            customer = customerRepository.findByTenantIdAndTelefonoWithTenant(tenantId, otp.getPhone())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        if (!Boolean.TRUE.equals(customer.getMigrated())
                && !Boolean.TRUE.equals(customer.getWelcomeBonusGranted())) {
            loyaltyService.grantWelcomeBonusIfNeeded(customer);

            customer = customerRepository.findByTenantIdAndTelefonoWithTenant(tenantId, otp.getPhone())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        Tenant tenant = customer.getTenant();

        return ClientLoginResponse.builder()
                .customerId(customer.getId())
                .tenantId(tenant.getId())
                .tenantNombre(tenant.getNombre())
                .tenantLogoUrl(tenant.getLogoUrl())
                .phoneVerified(Boolean.TRUE.equals(customer.isPhoneVerified()))
                .appActivated(Boolean.TRUE.equals(customer.getAppActivated()))
                .build();
    }

    @Transactional
    public Customer registerFromApp(Long tenantId, String phone, String nombres, String apellidos) {
        customerRepository.findByTenantIdAndTelefono(tenantId, phone)
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono ya está registrado");
                });

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));

        Customer customer = Customer.builder()
                .tenant(tenant)
                .telefono(phone)
                .nombres(nombres)
                .apellidos(apellidos)
                .phoneVerified(false)
                .fechaRegistro(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .migrated(false)
                .appActivated(false)
                .welcomeBonusGranted(false)
                .activationBonusGranted(false)
                .source("APP")
                .activo(true)
                .origenCliente("APP")
                .puntosDisponibles(0)
                .build();

        return customerRepository.save(customer);
    }

    @Transactional
    public OtpCode requestLoginOtp(Long tenantId, String phone) {
        customerRepository.findByTenantIdAndTelefono(tenantId, phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        String code = String.format("%06d", new Random().nextInt(999999));

        OtpCode otp = OtpCode.builder()
                .tenantId(tenantId)
                .phone(phone)
                .code(code)
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();

        otpCodeRepository.save(otp);

        System.out.println("🔐 OTP LOGIN (DEV): " + code);
        return otp;
    }

    public Customer obtenerClientePorId(Long tenantId, Long customerId) {
        return customerRepository.findByIdAndTenant_IdAndActivoTrue(customerId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    @Transactional
    public ClientHomeResponse getClientHome(Long tenantId, Long customerId) {
        Customer customer = customerRepository.findByIdAndTenantIdWithTenant(customerId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        Tenant tenant = customer.getTenant();

        LoyaltyAccount loyaltyAccount = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customerId)
                .orElseGet(() -> {
                    LoyaltyAccount created = LoyaltyAccount.builder()
                            .tenant(new Tenant(tenantId))
                            .customer(customer)
                            .puntosAcumulados(0)
                            .puntosDisponibles(0)
                            .fechaUltimoMovimiento(LocalDateTime.now())
                            .build();
                    return loyaltyAccountRepository.save(created);
                });

        int meta = 200;
        int disponibles = loyaltyAccount.getPuntosDisponibles() == null ? 0 : loyaltyAccount.getPuntosDisponibles();
        int acumulados = loyaltyAccount.getPuntosAcumulados() == null ? 0 : loyaltyAccount.getPuntosAcumulados();

        int faltan = Math.max(meta - disponibles, 0);
        double progreso = meta <= 0 ? 0.0 : Math.min((double) disponibles / meta, 1.0);

        ClientHomeResponse.NextAppointmentResponse nextAppointment = buildNextAppointment(tenantId, customerId);
        List<ClientHomeResponse.LastVisitResponse> lastVisits = buildLastVisits(tenantId, customerId);
        ClientHomeResponse.BenefitsResponse benefits = buildBenefits(tenantId, customerId, acumulados);

        return ClientHomeResponse.builder()
                .tenant(ClientHomeResponse.TenantMini.builder()
                        .id(tenant.getId())
                        .nombre(tenant.getNombre())
                        .logoUrl(tenant.getLogoUrl())
                        .ciudad(tenant.getCiudad())
                        .build())
                .customer(ClienteResponse.fromEntity(customer))
                .points(ClientHomeResponse.PointsSummary.builder()
                        .disponibles(disponibles)
                        .acumulados(acumulados)
                        .metaCorteGratis(meta)
                        .faltan(faltan)
                        .progreso(progreso)
                        .build())
                .nextAppointment(nextAppointment)
                .lastVisits(lastVisits)
                .benefits(benefits)
                .build();
    }

    private ClientHomeResponse.NextAppointmentResponse buildNextAppointment(Long tenantId, Long customerId) {
        return appointmentRepository.findNextAppointment(
                        tenantId,
                        customerId,
                        LocalDate.now(),
                        LocalTime.now()
                )
                .map(p -> ClientHomeResponse.NextAppointmentResponse.builder()
                        .appointmentId(p.getAppointmentId())
                        .fecha(p.getFecha() != null ? p.getFecha().toString() : null)
                        .horaInicio(p.getHoraInicio() != null ? p.getHoraInicio().toString() : null)
                        .horaFin(p.getHoraFin() != null ? p.getHoraFin().toString() : null)
                        .servicio(p.getServicio())
                        .barbero(p.getBarbero())
                        .branch(p.getBranch())
                        .estado("PENDIENTE")
                        .build())
                .orElse(null);
    }

    private List<ClientHomeResponse.LastVisitResponse> buildLastVisits(Long tenantId, Long customerId) {
        List<LastVisitProjection> rows = appointmentRepository.findLastVisits(tenantId, customerId, 5);
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        return rows.stream()
                .map(v -> ClientHomeResponse.LastVisitResponse.builder()
                        .appointmentId(v.getAppointmentId())
                        .fecha(v.getFecha() != null ? v.getFecha().toString() : null)
                        .servicio(v.getServicio())
                        .puntos(0)
                        .total(0.0)
                        .build())
                .collect(Collectors.toList());
    }

    private ClientHomeResponse.BenefitsResponse buildBenefits(Long tenantId, Long customerId, int acumulados) {
        int cantidadCanjes = (int) rewardRedemptionRepository
                .countCompletedOrGeneratedRedemptions(tenantId, customerId);

        return ClientHomeResponse.BenefitsResponse.builder()
                .nivel(acumulados >= 200 ? "Gold" : acumulados >= 100 ? "Silver" : "Bronze")
                .cantidadCanjes(cantidadCanjes)
                .puntosMes(acumulados)
                .racha(0)
                .build();
    }

    public Integer obtenerPuntosDisponiblesReales(Long tenantId, Long customerId) {
        return loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customerId)
                .map(account -> account.getPuntosDisponibles() != null
                        ? account.getPuntosDisponibles()
                        : 0)
                .orElse(0);
    }

    public OwnerCustomerLoyaltyResponse obtenerLoyaltyOwner(Long tenantId, Long customerId) {
        Customer customer = obtenerClienteOwner(tenantId, customerId);

        LoyaltyAccount account = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customerId)
                .orElse(null);

        int disponibles = account != null && account.getPuntosDisponibles() != null
                ? account.getPuntosDisponibles()
                : 0;

        int acumulados = account != null && account.getPuntosAcumulados() != null
                ? account.getPuntosAcumulados()
                : 0;

        return new OwnerCustomerLoyaltyResponse(
                customer.getId(),
                customer.getNombres(),
                customer.getApellidos(),
                customer.getTelefono(),
                disponibles,
                acumulados,
                customer.getMigrated(),
                customer.getAppActivated()
        );
    }
    public Integer obtenerPuntosAcumuladosReales(Long tenantId, Long customerId) {
        return loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customerId)
                .map(account -> account.getPuntosAcumulados() != null
                        ? account.getPuntosAcumulados()
                        : 0)
                .orElse(0);
    }

    public List<OwnerCustomerHistoryResponse> obtenerHistorialOwner(Long tenantId, Long customerId, int limit) {
        obtenerClienteOwner(tenantId, customerId);

        int safeLimit = Math.min(Math.max(limit, 1), 30);

        List<LastVisitProjection> rows = appointmentRepository.findLastVisits(
                tenantId,
                customerId,
                safeLimit
        );

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> saleIds = rows.stream()
                .map(LastVisitProjection::getAppointmentId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<CustomerHistorySaleItemProjection>> itemsBySaleId = saleIds.isEmpty()
                ? Collections.emptyMap()
                : saleIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> saleItemRepository.findCustomerHistoryItemsBySale(
                        tenantId,
                        customerId,
                        id
                )
        ));

        return rows.stream()
                .map(v -> {
                    List<OwnerCustomerHistoryItemResponse> items = itemsBySaleId
                            .getOrDefault(v.getAppointmentId(), Collections.emptyList())
                            .stream()
                            .map(item -> OwnerCustomerHistoryItemResponse.builder()
                                    .id(item.getId())
                                    .nombre(item.getNombre() != null && !item.getNombre().isBlank()
                                            ? item.getNombre()
                                            : "Item")
                                    .tipo(item.getTipo() != null && !item.getTipo().isBlank()
                                            ? item.getTipo()
                                            : "SERVICE")
                                    .cantidad(item.getCantidad() != null ? item.getCantidad() : 1)
                                    .precioUnitario(item.getPrecioUnitario() != null
                                            ? item.getPrecioUnitario()
                                            : BigDecimal.ZERO)
                                    .subtotal(item.getSubtotal() != null
                                            ? item.getSubtotal()
                                            : BigDecimal.ZERO)
                                    .barbero(item.getBarbero() != null && !item.getBarbero().isBlank()
                                            ? item.getBarbero()
                                            : "Sin asignar")
                                    .barberPhotoUrl(item.getBarberPhotoUrl() != null
                                            ? item.getBarberPhotoUrl()
                                            : "")
                                    .build())
                            .collect(Collectors.toList());

                    return OwnerCustomerHistoryResponse.builder()
                            .id(v.getAppointmentId())
                            .fecha(v.getFecha() != null ? v.getFecha().toString() : null)
                            .servicio(v.getServicio() != null ? v.getServicio() : "Producto / venta")
                            .barbero(v.getBarbero() != null && !v.getBarbero().isBlank()
                                    ? v.getBarbero()
                                    : "Sin asignar")
                            .barberPhotoUrl(v.getBarberPhotoUrl() != null ? v.getBarberPhotoUrl() : "")
                            .puntos(0)
                            .total(v.getTotal() != null ? BigDecimal.valueOf(v.getTotal()) : BigDecimal.ZERO)
                            .tipo("SALE")
                            .items(items)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<InactiveCustomerResponse> listarClientesInactivosOwner(Long tenantId, Integer daysInactive) {
        int safeDays = daysInactive == null ? 30 : Math.max(7, Math.min(daysInactive, 365));

        return saleRepository.findInactiveCustomers(tenantId, safeDays)
                .stream()
                .map(item -> InactiveCustomerResponse.builder()
                        .customerId(item.getCustomerId())
                        .nombre(item.getNombre())
                        .telefono(item.getTelefono())
                        .ultimaVisita(item.getUltimaVisita())
                        .build())
                .toList();
    }
}