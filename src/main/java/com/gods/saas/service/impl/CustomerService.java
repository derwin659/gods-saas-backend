package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.ActualizarClienteRequest;
import com.gods.saas.domain.dto.CambiarTelefonoRequest;
import com.gods.saas.domain.dto.ClienteResponse;
import com.gods.saas.domain.dto.VentaRapidaRequest;
import com.gods.saas.domain.dto.response.ClientHomeResponse;
import com.gods.saas.domain.dto.response.ClientLoginResponse;
import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.OtpCode;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.AppointmentRepository;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.LoyaltyAccountRepository;
import com.gods.saas.domain.repository.LoyaltyMovementRepository;
import com.gods.saas.domain.repository.OtpCodeRepository;
import com.gods.saas.domain.repository.RewardRedemptionRepository;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.domain.repository.projection.LastVisitProjection;
import com.gods.saas.service.impl.impl.LoyaltyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final CustomerRepository customerRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final TenantRepository tenantRepository;
    private final AppointmentRepository appointmentRepository;
    private final LoyaltyMovementRepository loyaltyMovementRepository;
    private final RewardRedemptionRepository rewardRedemptionRepository;
    private final LoyaltyService loyaltyService;

    @Transactional
    public Customer registrarCliente(VentaRapidaRequest req) {
        customerRepository.findByTelefono(req.getPhone())
                .ifPresent(c -> {
                    throw new RuntimeException("El teléfono ya está registrado");
                });

        Customer newClient = Customer.builder()
                .telefono(req.getPhone())
                .nombres(req.getNombre())
                .apellidos(req.getApellido())
                .fechaNacimiento(req.getFechaNacimiento())
                .origenCliente(req.getOrigenCliente())
                .phoneVerified(false)
                .fechaRegistro(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
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


    public Customer obtenerClientePorTelefono(Long tenantId, String phone) {
        return customerRepository.findByTenantIdAndTelefono(tenantId, phone)
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

        Customer customer = customerRepository.findByPhonePendiente(nuevoTelefono)
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
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
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
}