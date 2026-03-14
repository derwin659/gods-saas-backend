package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.*;
import com.gods.saas.domain.dto.response.ClientHomeResponse;
import com.gods.saas.domain.model.LoyaltyAccount;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.*;
import com.gods.saas.domain.repository.projection.LastVisitProjection;
import com.gods.saas.service.impl.impl.LoyaltyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.gods.saas.domain.model.Customer;
import com.gods.saas.domain.model.OtpCode;
import org.springframework.web.server.ResponseStatusException;


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

    /**
     * Crear cliente rápido desde POS (venta rápida).
     */
    public Customer registrarCliente(VentaRapidaRequest req) {

        customerRepository.findByTelefono(req.getPhone())
                .ifPresent(c -> { throw new RuntimeException("El teléfono ya está registrado"); });

        Customer newClient = Customer.builder()
                .telefono(req.getPhone())
                .nombres(req.getNombre())
                .apellidos(req.getApellido())
                .fechaNacimiento(req.getFechaNacimiento())
                .origenCliente(req.getOrigenCliente())
                .phoneVerified(false)
                .fechaRegistro(LocalDateTime.now())
                .build();

        return customerRepository.save(newClient);
    }

    /**
     * Obtener cliente por teléfono
     */
    public Customer obtenerClientePorTelefono(Long tenantId, String phone) {
        return customerRepository.findByTenantIdAndTelefono(tenantId, phone)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
    }

    /**
     * Obtener cliente por ID
     */
    public Customer getById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    /**
     * Actualizar datos del cliente (desde Backoffice / Perfil)
     */
    @Transactional
    public Customer actualizarCliente(Long customerId, ActualizarClienteRequest req) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (req.getNombre() != null) customer.setNombres(req.getNombre());
        if (req.getApellido() != null) customer.setApellidos(req.getApellido());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getFechaNacimiento() != null) customer.setFechaNacimiento(req.getFechaNacimiento());
        if (req.getOrigenCliente() != null) customer.setOrigenCliente(req.getOrigenCliente());

        customer.setFechaActualizacion(LocalDateTime.now());

        return customerRepository.save(customer);
    }

    /**
     * Completar perfil del cliente (desde App cliente)
     */
    @Transactional
    public Customer completarPerfil(Long customerId, PerfilClienteRequest req) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (req.getNombre() != null) customer.setNombres(req.getNombre());
        if (req.getApellido() != null) customer.setApellidos(req.getApellido());
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getFechaNacimiento() != null) customer.setFechaNacimiento(req.getFechaNacimiento());
        if (req.getOrigenCliente() != null) customer.setOrigenCliente(req.getOrigenCliente());

        customer.setFechaActualizacion(LocalDateTime.now());

        return customerRepository.save(customer);
    }

    /**
     * Solicitar cambio de teléfono (envía OTP al nuevo número)
     */
    @Transactional
    public void solicitarCambioTelefono(Long customerId, String nuevoTelefono) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        // Validar que no exista otro cliente con ese número
        customerRepository.findByTelefono(nuevoTelefono)
                .ifPresent(c -> { throw new RuntimeException("Ese teléfono ya está registrado"); });

        // Guardar el nuevo teléfono pendiente
        customer.setPhonePendiente(nuevoTelefono);
        customer.setPhonePendienteVerificacion(true);
        customerRepository.save(customer);

        // Generar OTP para el nuevo teléfono
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

    /**
     * Confirmar cambio de teléfono usando OTP
     */
    @Transactional
    public Customer confirmarCambioTelefono(String nuevoTelefono, String code, Long tenanId) {

        // OJO: el repo debe tener este método:
        // Optional<OtpCode> findTopByPhoneAndUsedIsFalseOrderByCreatedAtDesc(String phone);
        OtpCode otp = otpCodeRepository
                .findTopByPhoneAndTenantIdAndUsedIsFalseOrderByCreatedAtDesc(nuevoTelefono, tenanId )
                .orElseThrow(() -> new RuntimeException("OTP no encontrado"));

        if (!otp.getCode().equals(code)) {
            throw new RuntimeException("Código incorrecto");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Código expirado");
        }

        // Obtener cliente que tenía ese nuevo teléfono como pendiente
        Customer customer = customerRepository.findByPhonePendiente(nuevoTelefono)
                .orElseThrow(() -> new RuntimeException("No hay cliente con solicitud de cambio"));

        // Aplicar cambio real
        customer.setTelefono(nuevoTelefono);
        customer.setPhonePendiente(null);
        customer.setPhonePendienteVerificacion(false);
        customer.setPhoneVerified(true);
        customer.setFechaActualizacion(LocalDateTime.now());

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        return customerRepository.save(customer);
    }

    /**
     * Recuperar cuenta por teléfono antiguo:
     * - Cliente perdió acceso al número anterior
     * - Quiere asociar su cuenta a un nuevo teléfono
     * - Se envía OTP al nuevo número
     */
    @Transactional
    public Customer recuperarCuentaPorTelefono(CambiarTelefonoRequest req) {

        // 1. Buscar cliente por número antiguo
        Customer customer = customerRepository.findByTelefono(req.getOldPhone())
                .orElseThrow(() -> new RuntimeException("No existe un cliente con ese teléfono antiguo"));

        // 2. Validar que el nuevo teléfono no esté en uso
        customerRepository.findByTelefono(req.getNewPhone())
                .ifPresent(c -> { throw new RuntimeException("El nuevo teléfono ya está en uso"); });

        // 3. Guardar el nuevo teléfono en campo temporal
        customer.setPhonePendiente(req.getNewPhone());
        customer.setPhonePendienteVerificacion(true);
        customerRepository.save(customer);

        // 4. Generar OTP para el nuevo número
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
    public Customer verifyLoginOtp(Long otpId, String code) {

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

        Customer c = customerRepository.findByTenantIdAndTelefono(tenantId, otp.getPhone())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));

        c.setPhoneVerified(true);
        c.setFechaActualizacion(LocalDateTime.now());

        if (!Boolean.TRUE.equals(c.getAppActivated())) {
            c.setAppActivated(true);
            c.setAppActivatedAt(LocalDateTime.now());
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        c = customerRepository.save(c);

        // 1) Bono por activación de cliente migrado
        if (Boolean.TRUE.equals(c.getMigrated())
                && !Boolean.TRUE.equals(c.getActivationBonusGranted())) {

            loyaltyService.grantActivationBonusIfNeeded(c);

            // refrescar customer por si el servicio actualizó flags
            c = customerRepository.findById(c.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        // 2) Bono de bienvenida para cliente nuevo real
        if (!Boolean.TRUE.equals(c.getMigrated())
                && !Boolean.TRUE.equals(c.getWelcomeBonusGranted())) {

            loyaltyService.grantWelcomeBonusIfNeeded(c);

            c = customerRepository.findById(c.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        }

        return c;
    }

    @Transactional
    public Customer registerFromApp(Long tenantId, String phone, String nombres, String apellidos) {

        customerRepository.findByTenantIdAndTelefono(tenantId, phone)
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El teléfono ya está registrado");
                });

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant no encontrado"));

        Customer c = Customer.builder()
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

        return customerRepository.save(c);
    }

    @Transactional
    public OtpCode requestLoginOtp(Long tenantId, String phone) {

        // validar cliente existe
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

    public ClientHomeResponse getClientHome(Long tenantId, Long customerId) {

        Customer c = obtenerClientePorId(tenantId, customerId);
        System.out.printf("customer {}"+c);
        Tenant t = c.getTenant();
        System.out.printf("tenanid {}"+t);

        LoyaltyAccount la = loyaltyAccountRepository
                .findByTenant_IdAndCustomer_Id(tenantId, customerId)
                .orElseGet(() -> {
                    LoyaltyAccount created = LoyaltyAccount.builder()
                            .tenant(new Tenant(tenantId))
                            .customer(c)
                            .puntosAcumulados(0)
                            .puntosDisponibles(0)
                            .fechaUltimoMovimiento(LocalDateTime.now())
                            .build();
                    return loyaltyAccountRepository.save(created);
                });

        int meta = 200; // luego lo sacas de tenant_settings.schedule_config o config
        int disponibles = la.getPuntosDisponibles() == null ? 0 : la.getPuntosDisponibles();
        int acumulados  = la.getPuntosAcumulados() == null ? 0 : la.getPuntosAcumulados();

        int faltan = Math.max(meta - disponibles, 0);
        double progreso = meta <= 0 ? 0.0 : Math.min((double) disponibles / meta, 1.0);

        ClientHomeResponse.NextAppointmentResponse nextAppointment = buildNextAppointment(tenantId, customerId);
        List<ClientHomeResponse.LastVisitResponse> lastVisits = buildLastVisits(tenantId, customerId);
        ClientHomeResponse.BenefitsResponse benefits = buildBenefits(tenantId, customerId, acumulados);

        return ClientHomeResponse.builder()
                .tenant(ClientHomeResponse.TenantMini.builder()
                        .id(t.getId())
                        .nombre(t.getNombre())
                        .logoUrl(t.getLogoUrl())
                        .ciudad(t.getCiudad())
                        .build())
                .customer(ClienteResponse.fromEntity(c))
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
                        .barbero(blankToNull(p.getBarbero()))
                        .branch(p.getBranch())
                        .estado(p.getEstado())
                        .build())
                .orElse(null);
    }

    private List<ClientHomeResponse.LastVisitResponse> buildLastVisits(Long tenantId, Long customerId) {
        List<LastVisitProjection> visits = appointmentRepository.findLastVisits(tenantId, customerId, 3);

        if (visits == null || visits.isEmpty()) {
            return Collections.emptyList();
        }
        System.out.printf("visit {}"+visits);
        return visits.stream()
                .map(v -> ClientHomeResponse.LastVisitResponse.builder()
                        .appointmentId(v.getAppointmentId())
                        .fecha(v.getFecha() != null ? v.getFecha().toString() : null)
                        .servicio(v.getServicio())
                        .puntos(v.getPuntos() == null ? 0 : v.getPuntos())
                        .total(v.getTotal() == null ? 0.0 : v.getTotal())
                        .build())
                .toList();
    }

    private ClientHomeResponse.BenefitsResponse buildBenefits(Long tenantId, Long customerId, int acumulados) {
        long cantidadCanjes = rewardRedemptionRepository
                .countCompletedOrGeneratedRedemptions(tenantId, customerId);

        Integer puntosMes = loyaltyMovementRepository
                .sumPositivePointsCurrentMonth(tenantId, customerId);

        int racha = calcularRachaMensual(
                appointmentRepository.findVisitedMonths(tenantId, customerId)
        );

        return ClientHomeResponse.BenefitsResponse.builder()
                .nivel(calcularNivel(acumulados))
                .cantidadCanjes((int) cantidadCanjes)
                .puntosMes(puntosMes == null ? 0 : puntosMes)
                .racha(racha)
                .build();
    }

    private String calcularNivel(int puntosAcumulados) {
        if (puntosAcumulados >= 1000) return "Black";
        if (puntosAcumulados >= 500) return "Oro";
        if (puntosAcumulados >= 200) return "Plata";
        return "Bronce";
    }

    private int calcularRachaMensual(List<LocalDate> mesesConVisita) {
        if (mesesConVisita == null || mesesConVisita.isEmpty()) {
            return 0;
        }

        Set<YearMonth> months = mesesConVisita.stream()
                .map(YearMonth::from)
                .collect(Collectors.toSet());

        YearMonth current = YearMonth.now();
        int streak = 0;

        while (months.contains(current)) {
            streak++;
            current = current.minusMonths(1);
        }

        return streak;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

