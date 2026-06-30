package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.request.PublicCreateAppointmentRequest;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class ClientBookingService {

    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final TenantRepository tenantRepository;

    private static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(21, 0);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final AppUserRepository appUserRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;
    private final NotificationService notificationService;
    private final PromotionRepository promotionRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    public BookingBootstrapResponse getBootstrap(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado."));

        List<Branch> branches = branchRepository.findByTenant_IdAndActivoTrue(tenantId);
        List<ServiceEntity> services = serviceRepository.findByTenant_IdAndActivoTrue(tenantId);
        List<AppUser> barbers = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(tenantId, null, RoleType.BARBER);

        List<TenantPaymentMethod> paymentMethods =
                tenantPaymentMethodRepository.findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId);

        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId).orElse(null);
        String countryCode = countryCodeFor(tenant.getPais());
        String currency = settings != null && settings.getCurrency() != null && !settings.getCurrency().isBlank()
                ? settings.getCurrency().trim().toUpperCase(Locale.ROOT)
                : currencyForCountry(countryCode);

        Map<String, Object> config = settings != null && settings.getScheduleConfig() != null
                ? settings.getScheduleConfig()
                : Map.of();

        Boolean depositEnabled = getBoolean(config, "bookingDepositEnabled", false);
        BigDecimal defaultAmount = getBigDecimal(config, "bookingDepositDefaultAmount", BigDecimal.ZERO);
        Integer defaultPercent = getInteger(config, "bookingDepositDefaultPercent", null);

        return BookingBootstrapResponse.builder()
                .tenantName(tenant.getNombre())
                .tenantLogoUrl(tenant.getLogoUrl())
                .tenantCoverImageUrl(firstBranchImage(branches))
                .country(tenant.getPais())
                .countryCode(countryCode)
                .currency(currency)
                .currencySymbol(resolveCurrencySymbol(currency))
                .branches(branches.stream().map(BranchMiniResponse::fromEntity).toList())
                .services(services.stream().map(ServiceMiniResponse::fromEntity).toList())
                .barbers(barbers.stream().map(barber -> mapBootstrapBarber(tenantId, barber)).toList())
                .bookingDepositEnabled(depositEnabled)
                .bookingDepositDefaultAmount(defaultAmount)
                .bookingDepositDefaultPercent(defaultPercent)
                .paymentMethods(paymentMethods.stream().map(PaymentMethodMiniResponse::fromEntity).toList())
                .build();
    }

    private BarberMiniResponse mapBootstrapBarber(Long tenantId, AppUser barber) {
        List<Long> branchIds = userTenantRoleRepository
                .findByUserIdAndTenantIdAndRoleWithBranch(barber.getId(), tenantId, RoleType.BARBER)
                .stream()
                .map(UserTenantRole::getBranch)
                .filter(branch -> branch != null && branch.getId() != null)
                .map(Branch::getId)
                .distinct()
                .toList();

        return BarberMiniResponse.fromEntity(barber, branchIds);
    }

    public BookingAvailabilityResponse getAvailability(
            Long tenantId,
            Long branchId,
            Long serviceId,
            String date,
            Long barberId
    ) {
        return getAvailability(tenantId, branchId, serviceId, null, date, barberId);
    }

    public BookingAvailabilityResponse getAvailability(
            Long tenantId,
            Long branchId,
            Long serviceId,
            List<Long> serviceIds,
            String date,
            Long barberId
    ) {
        log.info("BOOKING AVAILABILITY START => tenantId={}, branchId={}, serviceId={}, serviceIds={}, date={}, barberId={}",
                tenantId, branchId, serviceId, serviceIds, date, barberId);

        LocalDate fecha = LocalDate.parse(date);
        validateBookingDate(fecha);

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        log.info("BRANCH FOUND => branchId={}, tenantId={}",
                branch.getId(),
                branch.getTenant() != null ? branch.getTenant().getId() : null);

        if (!branch.getTenant().getId().equals(tenantId)) {
            log.warn("BRANCH TENANT MISMATCH => expectedTenantId={}, branchTenantId={}",
                    tenantId, branch.getTenant().getId());
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        List<ServiceEntity> selectedServices = resolveBookingServices(tenantId, serviceId, serviceIds);
        ServiceEntity service = selectedServices.get(0);
        int duracion = getTotalServiceDuration(selectedServices);
        int slotInterval = resolveSlotIntervalMinutes(duracion);
        List<String> slots = new ArrayList<>();

        log.info("SERVICE DURATION RESOLVED => {} minutos", duracion);

        if (barberId != null) {
            AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

            log.info("BARBER FOUND => barberId={}, nombre={} {}, barberBranchId={}, tenantId={}",
                    barber.getId(),
                    barber.getNombre(),
                    barber.getApellido(),
                    branchId,
                    tenantId);

            validateBarberBranch(barber, tenantId, branchId);

            BarberAvailability availability = getWorkingAvailabilityOrNull(
                    tenantId, branchId, barberId, fecha
            );

            log.info("BARBER AVAILABILITY RESULT => barberId={}, fecha={}, dayOfWeek={}, found={}",
                    barberId, fecha, fecha.getDayOfWeek().getValue(), availability != null);

            if (availability == null) {
                log.warn("NO WORKING AVAILABILITY FOUND => tenantId={}, branchId={}, barberId={}, fecha={}, dayOfWeek={}",
                        tenantId, branchId, barberId, fecha, fecha.getDayOfWeek().getValue());

                return BookingAvailabilityResponse.builder()
                        .date(date)
                        .slots(List.of())
                        .build();
            }

            LocalTime apertura = availability.getStartTime();
            LocalTime cierre = availability.getEndTime();
            LocalTime current = normalizeStartTime(fecha, apertura, slotInterval);

            log.info("BARBER SCHEDULE WINDOW => barberId={}, apertura={}, cierre={}, normalizedStart={}",
                    barberId, apertura, cierre, current);

            while (!current.plusMinutes(duracion).isAfter(cierre)) {
                LocalTime candidateStart = current;
                LocalTime candidateEnd = current.plusMinutes(duracion);

                boolean available = isBarberAvailableConsideringAllRules(
                        tenantId, branchId, barberId, fecha, candidateStart, candidateEnd
                );

                log.info("BARBER SLOT EVAL => barberId={}, fecha={}, start={}, end={}, available={}",
                        barberId, fecha, candidateStart, candidateEnd, available);

                if (available) {
                    slots.add(candidateStart.toString());
                }

                current = current.plusMinutes(slotInterval);
            }
        } else {
            List<AppUser> barberos = getActiveBranchBarbers(tenantId, branchId);

            log.info("ACTIVE BRANCH BARBERS => branchId={}, count={}, barberIds={}",
                    branchId,
                    barberos.size(),
                    barberos.stream().map(AppUser::getId).toList());

            List<AppUser> availableBarbersForDay = barberos.stream()
                    .filter(b -> getWorkingAvailabilityOrNull(tenantId, branchId, b.getId(), fecha) != null)
                    .sorted(Comparator.comparing(AppUser::getId))
                    .toList();

            log.info("AVAILABLE BARBERS FOR DAY => fecha={}, count={}, barberIds={}",
                    fecha,
                    availableBarbersForDay.size(),
                    availableBarbersForDay.stream().map(AppUser::getId).toList());

            if (availableBarbersForDay.isEmpty()) {
                log.warn("NO AVAILABLE BARBERS FOR DAY => tenantId={}, branchId={}, fecha={}",
                        tenantId, branchId, fecha);

                return BookingAvailabilityResponse.builder()
                        .date(date)
                        .slots(List.of())
                        .build();
            }

            LocalTime apertura = availableBarbersForDay.stream()
                    .map(b -> getWorkingAvailabilityOrNull(tenantId, branchId, b.getId(), fecha))
                    .map(BarberAvailability::getStartTime)
                    .min(LocalTime::compareTo)
                    .orElse(DEFAULT_OPENING_TIME);

            LocalTime cierre = availableBarbersForDay.stream()
                    .map(b -> getWorkingAvailabilityOrNull(tenantId, branchId, b.getId(), fecha))
                    .map(BarberAvailability::getEndTime)
                    .max(LocalTime::compareTo)
                    .orElse(DEFAULT_CLOSING_TIME);

            LocalTime current = normalizeStartTime(fecha, apertura, slotInterval);

            log.info("GLOBAL SCHEDULE WINDOW => fecha={}, apertura={}, cierre={}, normalizedStart={}",
                    fecha, apertura, cierre, current);

            while (!current.plusMinutes(duracion).isAfter(cierre)) {
                LocalTime candidateStart = current;
                LocalTime candidateEnd = current.plusMinutes(duracion);

                boolean anyAvailable = availableBarbersForDay.stream()
                        .anyMatch(b -> isBarberAvailableConsideringAllRules(
                                tenantId, branchId, b.getId(), fecha, candidateStart, candidateEnd
                        ));

                log.info("ANY BARBER SLOT EVAL => fecha={}, start={}, end={}, anyAvailable={}",
                        fecha, candidateStart, candidateEnd, anyAvailable);

                if (anyAvailable) {
                    slots.add(candidateStart.toString());
                }

                current = current.plusMinutes(slotInterval);
            }
        }

        log.info("BOOKING AVAILABILITY END => tenantId={}, branchId={}, serviceId={}, date={}, barberId={}, slotsCount={}, slots={}",
                tenantId, branchId, serviceId, date, barberId, slots.size(), slots);

        return BookingAvailabilityResponse.builder()
                .date(date)
                .slots(slots)
                .build();
    }

    @Transactional
    public CreateAppointmentResponse createAppointment(
            Long tenantId,
            Long customerId,
            CreateAppointmentRequest req
    ) {
        Customer customer = customerRepository.findByTenant_IdAndId(tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        Branch branch = branchRepository.findById(req.getBranchId())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        if (branch.getTenant() == null || !branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        List<ServiceEntity> selectedServices = resolveBookingServices(
                tenantId,
                req.getServiceId(),
                req.getServiceIds()
        );
        ServiceEntity service = selectedServices.get(0);

        if (service.getTenant() == null || !service.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El servicio no pertenece al tenant");
        }

        if (service.getPrecio() == null || service.getPrecio() <= 0) {
            throw new RuntimeException("El servicio no tiene un precio válido configurado");
        }

        LocalDate fecha = LocalDate.parse(req.getDate());
        LocalTime horaInicio = LocalTime.parse(req.getHoraInicio());

        validateBookingDate(fecha);
        validateTimeNotPast(fecha, horaInicio);

        int duracion = getTotalServiceDuration(selectedServices);
        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        AppUser barber;
        if (req.getBarberId() != null) {
            barber = appUserRepository.findByIdAndTenant_Id(req.getBarberId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

            validateBarberBranch(barber, tenantId, req.getBranchId());

            if (!isBarberAvailableConsideringAllRules(
                    tenantId,
                    req.getBranchId(),
                    barber.getId(),
                    fecha,
                    horaInicio,
                    horaFin
            )) {
                throw new RuntimeException("Ese horario ya no está disponible");
            }
        } else {
            barber = elegirBarberoDisponible(
                    tenantId,
                    req.getBranchId(),
                    fecha,
                    horaInicio,
                    horaFin
            );
        }

        BigDecimal originalAmount = getTotalServiceAmount(selectedServices);

        Promotion appliedPromotion = resolvePromotionOrNull(
                tenantId,
                req.getPromotionId(),
                branch,
                service
        );

        BigDecimal discountAmount = calculatePromotionDiscount(appliedPromotion, originalAmount);
        BigDecimal totalAmount = originalAmount.subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        boolean depositRequired = Boolean.TRUE.equals(req.getDepositRequired());

        BigDecimal depositAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = totalAmount;

        String appointmentStatus = "CREATED";
        String depositStatus = "NOT_REQUIRED";

        TenantPaymentMethod depositMethod = null;
        String depositMethodCode = null;
        String depositMethodName = null;

        if (depositRequired) {
            if (req.getDepositPaymentMethodId() == null) {
                throw new RuntimeException("Debes seleccionar un método de pago para el inicial");
            }

            depositMethod = tenantPaymentMethodRepository
                    .findByIdAndTenant_IdAndActiveTrue(req.getDepositPaymentMethodId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Método de pago no válido o inactivo"));

            if (depositMethod.getBranch() != null
                    && !depositMethod.getBranch().getId().equals(req.getBranchId())) {
                throw new RuntimeException("El método de pago no pertenece a la sede seleccionada");
            }

            if (req.getDepositAmount() == null
                    || req.getDepositAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("El monto del pago inicial debe ser mayor a cero");
            }

            depositAmount = req.getDepositAmount()
                    .setScale(2, RoundingMode.HALF_UP);

            if (depositAmount.compareTo(totalAmount) > 0) {
                throw new RuntimeException("El pago inicial no puede ser mayor al precio del servicio");
            }

            if (Boolean.TRUE.equals(depositMethod.getRequiresOperationCode())
                    && (req.getDepositOperationCode() == null
                    || req.getDepositOperationCode().isBlank())) {
                throw new RuntimeException("Debes ingresar el número de operación o referencia");
            }

            if (Boolean.TRUE.equals(depositMethod.getRequiresEvidence())
                    && (req.getDepositEvidenceUrl() == null
                    || req.getDepositEvidenceUrl().isBlank())) {
                throw new RuntimeException("Debes adjuntar el comprobante del pago");
            }

            remainingAmount = totalAmount.subtract(depositAmount)
                    .setScale(2, RoundingMode.HALF_UP);

            appointmentStatus = "PENDING_DEPOSIT_VALIDATION";
            depositStatus = "PENDING_VALIDATION";

            depositMethodCode = depositMethod.getCode();
            depositMethodName = depositMethod.getDisplayName();
        }

        Appointment appointment = Appointment.builder()
                .tenant(customer.getTenant())
                .branch(branch)
                .customer(customer)
                .user(barber)
                .service(service)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .estado(appointmentStatus)
                .notas(buildMultiServiceNotes(selectedServices))

                // Promoción / precio final
                .promotion(appliedPromotion)
                .promotionTitle(appliedPromotion != null ? appliedPromotion.getTitulo() : null)
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)

                // Pago inicial
                .depositRequired(depositRequired)
                .depositAmount(depositAmount)
                .remainingAmount(remainingAmount)
                .depositStatus(depositStatus)
                .depositPaymentMethod(depositMethod)
                .depositMethodCode(depositMethodCode)
                .depositMethodName(depositMethodName)
                .depositOperationCode(trimToNull(req.getDepositOperationCode()))
                .depositEvidenceUrl(trimToNull(req.getDepositEvidenceUrl()))
                .depositNote(trimToNull(req.getDepositNote()))
                .depositPaidAt(depositRequired ? LocalDateTime.now(BUSINESS_ZONE) : null)
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        notificationService.notifyBookingCreated(saved);

        return CreateAppointmentResponse.builder()
                .appointmentId(saved.getId())
                .estado(saved.getEstado())
                .build();
    }

    @Transactional
    public CreateAppointmentResponse cancelAppointment(
            Long tenantId,
            Long customerId,
            Long appointmentId
    ) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenant_IdAndCustomer_Id(appointmentId, tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        String currentStatus = appointment.getEstado() == null
                ? ""
                : appointment.getEstado().trim().toUpperCase();

        if (List.of(
                "CANCELADO",
                "CANCELADA",
                "CANCELLED",
                "ATENDIDO",
                "COMPLETADO",
                "COMPLETADA",
                "COMPLETED",
                "FINALIZADO",
                "FINALIZADA"
        ).contains(currentStatus)) {
            throw new RuntimeException("Esta reserva ya no se puede cancelar");
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        if (appointment.getFecha() != null
                && appointment.getHoraInicio() != null
                && (appointment.getFecha().isBefore(today)
                || (appointment.getFecha().isEqual(today)
                && appointment.getHoraInicio().isBefore(now)))) {
            throw new RuntimeException("No puedes cancelar una reserva que ya paso");
        }

        appointment.setEstado("CANCELADO");
        Appointment saved = appointmentRepository.save(appointment);

        notificationService.notifyBookingCancelledByClient(saved);

        return CreateAppointmentResponse.builder()
                .appointmentId(saved.getId())
                .estado(saved.getEstado())
                .build();
    }

    @Transactional
    public CreateAppointmentResponse rescheduleAppointment(
            Long tenantId,
            Long customerId,
            Long appointmentId,
            CreateAppointmentRequest req
    ) {
        Appointment appointment = appointmentRepository
                .findByIdAndTenant_IdAndCustomer_Id(appointmentId, tenantId, customerId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

        String currentStatus = appointment.getEstado() == null
                ? ""
                : appointment.getEstado().trim().toUpperCase();

        if (List.of(
                "CANCELADO",
                "CANCELADA",
                "CANCELLED",
                "ATENDIDO",
                "COMPLETADO",
                "COMPLETADA",
                "COMPLETED",
                "FINALIZADO",
                "FINALIZADA"
        ).contains(currentStatus)) {
            throw new RuntimeException("Esta reserva ya no se puede reprogramar");
        }

        if (req.getDate() == null || req.getHoraInicio() == null) {
            throw new RuntimeException("Selecciona fecha y hora para reprogramar");
        }

        LocalDate fecha = LocalDate.parse(req.getDate());
        LocalTime horaInicio = LocalTime.parse(req.getHoraInicio());
        validateBookingDate(fecha);
        validateTimeNotPast(fecha, horaInicio);

        Branch branch = appointment.getBranch();
        ServiceEntity service = appointment.getService();
        AppUser barber = appointment.getUser();

        if (branch == null || service == null || barber == null) {
            throw new RuntimeException("La reserva no tiene datos suficientes para reprogramar");
        }

        int duracion = getServiceDuration(service);
        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        LocalDate oldFecha = appointment.getFecha();
        LocalTime oldHoraInicio = appointment.getHoraInicio();
        LocalTime oldHoraFin = appointment.getHoraFin();

        if (!isBarberAvailableConsideringAllRulesExcludingAppointment(
                tenantId,
                branch.getId(),
                barber.getId(),
                fecha,
                horaInicio,
                horaFin,
                appointmentId
        )) {
            throw new RuntimeException("Ese horario ya no esta disponible");
        }

        appointment.setFecha(fecha);
        appointment.setHoraInicio(horaInicio);
        appointment.setHoraFin(horaFin);

        Appointment saved = appointmentRepository.save(appointment);

        notificationService.notifyBookingRescheduledByClient(
                saved,
                oldFecha,
                oldHoraInicio,
                oldHoraFin
        );

        return CreateAppointmentResponse.builder()
                .appointmentId(saved.getId())
                .estado(saved.getEstado())
                .build();
    }

    private AppUser elegirBarberoDisponible(
            Long tenantId,
            Long branchId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        List<AppUser> barberos = getActiveBranchBarbers(tenantId, branchId);

        for (AppUser b : barberos) {
            if (isBarberAvailableConsideringAllRules(
                    tenantId, branchId, b.getId(), fecha, horaInicio, horaFin
            )) {
                return b;
            }
        }

        throw new RuntimeException("No hay barberos disponibles para ese horario");
    }

    private boolean isBarberAvailableConsideringAllRules(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        log.info("VALIDATING BARBER SLOT => tenantId={}, branchId={}, barberId={}, fecha={}, horaInicio={}, horaFin={}",
                tenantId, branchId, barberId, fecha, horaInicio, horaFin);

        BarberAvailability availability = getWorkingAvailabilityOrNull(tenantId, branchId, barberId, fecha);
        if (availability == null) {
            log.warn("SLOT REJECTED => no working availability");
            return false;
        }

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            log.warn("SLOT REJECTED => out of availability range. startTime={}, endTime={}, availabilityStart={}, availabilityEnd={}",
                    horaInicio, horaFin, availability.getStartTime(), availability.getEndTime());
            return false;
        }

        if (isBlockedByManualBlock(tenantId, branchId, barberId, fecha, horaInicio, horaFin)) {
            log.warn("SLOT REJECTED => blocked by manual block");
            return false;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);

        if (fecha.equals(today) && !horaInicio.isAfter(now)) {
            log.warn("SLOT REJECTED => time is in the past. zone={}, now={}, slotStart={}",
                    BUSINESS_ZONE, now, horaInicio);
            return false;
        }

        long conflicts = appointmentRepository.countConflictingAppointments(
                tenantId,
                branchId,
                barberId,
                fecha,
                horaInicio,
                horaFin
        );

        log.info("APPOINTMENT CONFLICT CHECK => barberId={}, fecha={}, horaInicio={}, horaFin={}, conflicts={}",
                barberId, fecha, horaInicio, horaFin, conflicts);

        boolean available = conflicts == 0;
        log.info("SLOT FINAL RESULT => barberId={}, fecha={}, horaInicio={}, horaFin={}, available={}",
                barberId, fecha, horaInicio, horaFin, available);

        return available;
    }

    private boolean isBarberAvailableConsideringAllRulesExcludingAppointment(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long appointmentId
    ) {
        BarberAvailability availability = getWorkingAvailabilityOrNull(tenantId, branchId, barberId, fecha);
        if (availability == null) return false;

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            return false;
        }

        if (isBlockedByManualBlock(tenantId, branchId, barberId, fecha, horaInicio, horaFin)) {
            return false;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        if (fecha.equals(today) && !horaInicio.isAfter(now)) {
            return false;
        }

        long conflicts = appointmentRepository.countConflictingAppointmentsExcluding(
                tenantId,
                branchId,
                barberId,
                fecha,
                horaInicio,
                horaFin,
                appointmentId
        );

        return conflicts == 0;
    }

    private BarberAvailability getWorkingAvailabilityOrNull(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha
    ) {
        int dayOfWeek = fecha.getDayOfWeek().getValue();

        log.info("SEARCH WORKING AVAILABILITY => tenantId={}, branchId={}, barberId={}, fecha={}, dayOfWeek={}",
                tenantId, branchId, barberId, fecha, dayOfWeek);

        var optionalAvailability = barberAvailabilityRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndDayOfWeek(
                        tenantId,
                        branchId,
                        barberId,
                        dayOfWeek
                );

        if (optionalAvailability.isEmpty()) {
            log.warn("RAW AVAILABILITY NOT FOUND => tenantId={}, branchId={}, barberId={}, dayOfWeek={}",
                    tenantId, branchId, barberId, dayOfWeek);
            return null;
        }

        BarberAvailability availability = optionalAvailability.get();

        log.info("RAW AVAILABILITY FOUND => id={}, barberId={}, branchId={}, dayOfWeek={}, isWorking={}, startTime={}, endTime={}",
                availability.getId(),
                availability.getBarber() != null ? availability.getBarber().getId() : null,
                availability.getBranch() != null ? availability.getBranch().getId() : null,
                availability.getDayOfWeek(),
                availability.getIsWorking(),
                availability.getStartTime(),
                availability.getEndTime());

        if (!Boolean.TRUE.equals(availability.getIsWorking())) {
            log.warn("AVAILABILITY EXISTS BUT NOT WORKING => id={}, barberId={}, dayOfWeek={}",
                    availability.getId(), barberId, dayOfWeek);
            return null;
        }

        return availability;
    }

    private boolean isBlockedByManualBlock(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        List<BarberTimeBlock> blocks = barberTimeBlockRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndBlockDateOrderByStartTimeAsc(
                        tenantId, branchId, barberId, fecha
                );

        log.info("MANUAL BLOCKS FOUND => barberId={}, fecha={}, blocksCount={}",
                barberId, fecha, blocks.size());

        for (BarberTimeBlock b : blocks) {
            log.info("BLOCK DETAIL => id={}, allDay={}, startTime={}, endTime={}, reason={}",
                    b.getId(), b.getAllDay(), b.getStartTime(), b.getEndTime(), b.getReason());
        }

        boolean blocked = blocks.stream().anyMatch(b ->
                b.getStartTime() != null &&
                        b.getEndTime() != null &&
                        horaInicio.isBefore(b.getEndTime()) &&
                        horaFin.isAfter(b.getStartTime())
        );

        log.info("MANUAL BLOCK RESULT => barberId={}, fecha={}, horaInicio={}, horaFin={}, blocked={}",
                barberId, fecha, horaInicio, horaFin, blocked);

        return blocked;
    }

    private List<AppUser> getActiveBranchBarbers(Long tenantId, Long branchId) {
        List<AppUser> filtered = userTenantRoleRepository
                .findActiveUsersByTenantBranchAndRole(tenantId, branchId, RoleType.BARBER)
                .stream()
                .sorted(Comparator.comparing(AppUser::getId))
                .toList();

        log.info("ACTIVE BRANCH BARBERS FILTERED => branchId={}, count={}, barberIds={}",
                branchId,
                filtered.size(),
                filtered.stream().map(AppUser::getId).toList());

        return filtered;
    }

    private void validateBarberBranch(AppUser barber, Long tenantId, Long branchId) {
        boolean belongsToBranch = userTenantRoleRepository.existsByUserIdAndTenantIdAndBranchIdAndRoleIn(
                barber.getId(),
                tenantId,
                branchId,
                List.of(RoleType.BARBER)
        );

        log.info("VALIDATING BARBER BRANCH => barberId={}, requestedBranchId={}, belongsToBranch={}",
                barber.getId(), branchId, belongsToBranch);

        if (!belongsToBranch) {
            log.warn("BARBER BRANCH MISMATCH => barberId={}, requestedBranchId={}",
                    barber.getId(), branchId);
            throw new RuntimeException("El barbero no pertenece a la sucursal seleccionada");
        }
    }

    private int getServiceDuration(ServiceEntity service) {
        return service.getDuracionMinutos() != null && service.getDuracionMinutos() > 0
                ? service.getDuracionMinutos()
                : 30;
    }

    private List<ServiceEntity> resolveBookingServices(
            Long tenantId,
            Long primaryServiceId,
            List<Long> requestedServiceIds
    ) {
        List<Long> ids = new ArrayList<>();

        if (requestedServiceIds != null) {
            for (Long id : requestedServiceIds) {
                if (id != null && !ids.contains(id)) ids.add(id);
            }
        }

        if (ids.isEmpty() && primaryServiceId != null) {
            ids.add(primaryServiceId);
        } else if (primaryServiceId != null && !ids.contains(primaryServiceId)) {
            ids.add(0, primaryServiceId);
        }

        if (ids.isEmpty()) {
            throw new RuntimeException("Selecciona al menos un servicio");
        }

        List<ServiceEntity> services = new ArrayList<>();
        for (Long id : ids) {
            ServiceEntity service = serviceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

            if (service.getTenant() == null || !service.getTenant().getId().equals(tenantId)) {
                throw new RuntimeException("El servicio no pertenece al tenant");
            }

            if (service.getPrecio() == null || service.getPrecio() <= 0) {
                throw new RuntimeException("El servicio no tiene un precio valido configurado");
            }

            services.add(service);
        }

        return services;
    }

    private int getTotalServiceDuration(List<ServiceEntity> services) {
        int total = services.stream()
                .mapToInt(this::getServiceDuration)
                .sum();

        return total > 0 ? total : 30;
    }

    private BigDecimal getTotalServiceAmount(List<ServiceEntity> services) {
        return services.stream()
                .map(service -> BigDecimal.valueOf(service.getPrecio()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildMultiServiceNotes(List<ServiceEntity> services) {
        if (services == null || services.size() <= 1) return null;

        String names = services.stream()
                .map(ServiceEntity::getNombre)
                .filter(name -> name != null && !name.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");

        return "Servicios reservados: " + names;
    }

    private void validateBookingDate(LocalDate fecha) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        if (fecha.isBefore(today)) {
            throw new RuntimeException("No se puede reservar en una fecha pasada");
        }
    }

    private void validateTimeNotPast(LocalDate fecha, LocalTime horaInicio) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);

        if (fecha.equals(today) && !horaInicio.isAfter(now)) {
            throw new RuntimeException("No se puede reservar una hora pasada");
        }
    }

    private int resolveSlotIntervalMinutes(int durationMinutes) {
        if (durationMinutes <= 15) return 15;
        if (durationMinutes <= 60) return 30;
        return 60;
    }

    private LocalTime normalizeStartTime(LocalDate fecha, LocalTime apertura, int slotIntervalMinutes) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        if (!fecha.equals(today)) {
            return apertura;
        }

        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        LocalTime rounded = roundUpToNextSlot(now, slotIntervalMinutes);

        log.info("NORMALIZE START TIME => fecha={}, today={}, nowLima={}, rounded={}, apertura={}",
                fecha, today, now, rounded, apertura);

        if (rounded.isBefore(apertura)) {
            return apertura;
        }

        return rounded;
    }

    private LocalTime roundUpToNextSlot(LocalTime time, int slotIntervalMinutes) {
        int minute = time.getMinute();
        int remainder = minute % slotIntervalMinutes;
        int minutesToAdd = remainder == 0 ? slotIntervalMinutes : slotIntervalMinutes - remainder;

        return time.withSecond(0).withNano(0).plusMinutes(minutesToAdd);
    }

    private Promotion resolvePromotionOrNull(
            Long tenantId,
            Long promotionId,
            Branch branch,
            ServiceEntity service
    ) {
        if (promotionId == null) {
            return null;
        }

        Promotion promotion = promotionRepository.findByIdAndTenant_Id(promotionId, tenantId)
                .orElseThrow(() -> new RuntimeException("La promoción seleccionada no existe"));

        if (!promotion.isActivo()) {
            throw new RuntimeException("La promoción seleccionada ya no está activa");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        if (promotion.getFechaInicio() != null && promotion.getFechaInicio().isAfter(now)) {
            throw new RuntimeException("La promoción aún no está disponible");
        }

        if (promotion.getFechaFin() != null && promotion.getFechaFin().isBefore(now)) {
            throw new RuntimeException("La promoción ya venció");
        }

        if (promotion.getBranch() != null
                && branch != null
                && !promotion.getBranch().getId().equals(branch.getId())) {
            throw new RuntimeException("La promoción no aplica para la sede seleccionada");
        }

        // Si redirectType=SERVICE y redirectValue trae un serviceId, validamos que coincida.
        if (promotion.getRedirectType() != null
                && "SERVICE".equalsIgnoreCase(promotion.getRedirectType().name())
                && promotion.getRedirectValue() != null
                && !promotion.getRedirectValue().isBlank()) {
            try {
                Long promoServiceId = Long.parseLong(promotion.getRedirectValue().trim());
                if (service != null && !promoServiceId.equals(service.getId())) {
                    throw new RuntimeException("La promoción no aplica para el servicio seleccionado");
                }
            } catch (NumberFormatException ignored) {
                // redirectValue puede contener otro dato en promociones antiguas.
            }
        }

        return promotion;
    }

    private BigDecimal calculatePromotionDiscount(Promotion promotion, BigDecimal originalAmount) {
        if (promotion == null || originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String type = promotion.getDiscountType() != null
                ? promotion.getDiscountType().trim().toUpperCase()
                : null;

        BigDecimal value = promotion.getDiscountValue();

        // Compatibilidad temporal con promociones antiguas:
        // Si aún no llenaste discount_type/discount_value, intentamos leer priceText como precio final.
        if ((type == null || type.isBlank()) && value == null) {
            BigDecimal fixedPriceFromText = extractFirstMoneyValue(promotion.getPriceText());
            if (fixedPriceFromText != null
                    && fixedPriceFromText.compareTo(BigDecimal.ZERO) > 0
                    && fixedPriceFromText.compareTo(originalAmount) < 0) {
                type = "FIXED_PRICE";
                value = fixedPriceFromText;
            }
        }

        if (type == null || type.isBlank() || value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discount = BigDecimal.ZERO;

        switch (type) {
            case "AMOUNT" -> discount = value;
            case "PERCENT" -> discount = originalAmount
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case "FIXED_PRICE" -> discount = originalAmount.subtract(value);
            default -> discount = BigDecimal.ZERO;
        }

        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }

        if (discount.compareTo(originalAmount) > 0) {
            discount = originalAmount;
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    public BookingBootstrapResponse getPublicBootstrapByCode(
            String codigoNegocio,
            Long branchId,
            Long barberId
    ) {
        Tenant tenant = findActiveTenantByCode(codigoNegocio);

        // Reutilizamos tu bootstrap existente. Los filtros branchId/barberId
        // se aplican en el frontend público cuando el link viene preseleccionado.
        return getBootstrap(tenant.getId());
    }

    public PublicBookingLinkInfoResponse getPublicBookingLinkInfo(
            String codigoNegocio,
            Long branchId,
            Long barberId
    ) {
        Tenant tenant = findActiveTenantByCode(codigoNegocio);

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new RuntimeException("Sede no encontrada."));

            if (branch.getTenant() == null || !tenant.getId().equals(branch.getTenant().getId())) {
                throw new RuntimeException("La sede no pertenece al negocio.");
            }
        }

        AppUser barber = null;
        if (barberId != null) {
            barber = appUserRepository.findByIdAndTenant_Id(barberId, tenant.getId())
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado."));

            if (branchId != null) {
                validateBarberBranch(barber, tenant.getId(), branchId);
            }
        }

        return PublicBookingLinkInfoResponse.builder()
                .codigoNegocio(tenant.getCodigo())
                .tenantId(tenant.getId())
                .branchId(branchId)
                .barberId(barberId)
                .tenantName(tenant.getNombre())
                .tenantLogoUrl(tenant.getLogoUrl())
                .branchName(branch != null ? branch.getNombre() : null)
                .branchImageUrl(branch != null ? branch.getImageUrl() : null)
                .barberName(barber != null ? buildUserFullName(barber) : null)
                .bookingLink(buildBookingLink(tenant.getCodigo(), branchId, barberId))
                .build();
    }

    private String firstBranchImage(List<Branch> branches) {
        if (branches == null) return null;
        return branches.stream()
                .map(Branch::getImageUrl)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    public BookingAvailabilityResponse getPublicAvailabilityByCode(
            String codigoNegocio,
            Long branchId,
            Long serviceId,
            List<Long> serviceIds,
            String date,
            Long barberId
    ) {
        Tenant tenant = findActiveTenantByCode(codigoNegocio);

        return getAvailability(
                tenant.getId(),
                branchId,
                serviceId,
                serviceIds,
                date,
                barberId
        );
    }

    @Transactional
    public CreateAppointmentResponse createPublicAppointment(
            String codigoNegocio,
            PublicCreateAppointmentRequest request
    ) {
        Tenant tenant = findActiveTenantByCode(codigoNegocio);

        Customer customer = resolveOrCreatePublicCustomer(tenant, request);

        CreateAppointmentRequest internalRequest = new CreateAppointmentRequest();
        internalRequest.setBranchId(request.getBranchId());
        internalRequest.setServiceId(request.getServiceId());
        internalRequest.setServiceIds(request.getServiceIds());
        internalRequest.setBarberId(request.getBarberId());
        internalRequest.setDate(request.getDate());
        internalRequest.setHoraInicio(request.getHoraInicio());
        internalRequest.setPromotionId(request.getPromotionId());
        internalRequest.setDepositRequired(request.getDepositRequired());
        internalRequest.setDepositPaymentMethodId(request.getDepositPaymentMethodId());
        internalRequest.setDepositAmount(request.getDepositAmount());
        internalRequest.setDepositOperationCode(request.getDepositOperationCode());
        internalRequest.setDepositEvidenceUrl(request.getDepositEvidenceUrl());
        internalRequest.setDepositNote(request.getDepositNote());

        return createAppointment(tenant.getId(), customer.getId(), internalRequest);
    }

    private Tenant findActiveTenantByCode(String codigoNegocio) {
        String code = trimToNull(codigoNegocio);
        if (code == null) {
            throw new RuntimeException("Código de negocio requerido.");
        }

        return tenantRepository.findByCodigoIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Negocio no encontrado."));
    }

    private Customer resolveOrCreatePublicCustomer(Tenant tenant, PublicCreateAppointmentRequest request) {
        String phone = normalizePhone(request.getCustomerPhone());
        String email = trimToNull(request.getCustomerEmail());
        String firstName = trimToNull(request.getCustomerName());
        String lastName = trimToNull(request.getCustomerLastName());

        if (phone == null) {
            throw new RuntimeException("Ingresa el teléfono del cliente.");
        }

        if (firstName == null) {
            throw new RuntimeException("Ingresa el nombre del cliente.");
        }

        Customer existing = customerRepository.findByTenant_IdAndTelefonoAndActivoTrue(tenant.getId(), phone)
                .orElse(null);

        if (existing != null) {
            boolean changed = false;

            if (trimToNull(existing.getNombres()) == null) {
                existing.setNombres(firstName);
                changed = true;
            }
            if (trimToNull(existing.getApellidos()) == null && lastName != null) {
                existing.setApellidos(lastName);
                changed = true;
            }
            if (trimToNull(existing.getEmail()) == null && email != null) {
                existing.setEmail(email);
                changed = true;
            }

            return changed ? customerRepository.save(existing) : existing;
        }

        Customer customer = Customer.builder()
                .tenant(tenant)
                .nombres(firstName)
                .apellidos(lastName)
                .telefono(phone)
                .email(email)
                .phoneVerified(false)
                .phonePendiente(null)
                .phonePendienteVerificacion(false)
                .origenCliente("PUBLIC_BOOKING")
                .puntosDisponibles(0)
                .activo(true)
                .source("PUBLIC_BOOKING")
                .build();

        return customerRepository.save(customer);
    }

    private String buildBookingLink(String codigoNegocio, Long branchId, Long barberId) {
        StringBuilder url = new StringBuilder("https://www.supergodsapp.com/reservar/")
                .append(codigoNegocio.trim());

        boolean hasQuery = false;
        if (branchId != null) {
            url.append("?branchId=").append(branchId);
            hasQuery = true;
        }
        if (barberId != null) {
            url.append(hasQuery ? "&" : "?").append("barberId=").append(barberId);
        }

        return url.toString();
    }

    private String buildUserFullName(AppUser user) {
        String nombre = user.getNombre() == null ? "" : user.getNombre().trim();
        String apellido = user.getApellido() == null ? "" : user.getApellido().trim();
        String fullName = (nombre + " " + apellido).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }

    private String normalizePhone(String phone) {
        String value = trimToNull(phone);
        if (value == null) return null;

        String normalized = value.replaceAll("[^0-9+]", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal extractFirstMoneyValue(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = text
                .replace(",", ".")
                .replaceAll("[^0-9.]", " ")
                .trim();

        if (normalized.isBlank()) {
            return null;
        }

        String[] parts = normalized.split("\\s+");
        for (String part : parts) {
            try {
                return new BigDecimal(part).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Boolean getBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String countryCodeFor(String value) {
        String normalized = normalizeCountry(value);
        return switch (normalized) {
            case "PERU", "PE" -> "PE";
            case "VENEZUELA", "VE" -> "VE";
            case "ESTADOSUNIDOS", "UNITEDSTATES", "USA", "US" -> "US";
            case "COLOMBIA", "CO" -> "CO";
            case "MEXICO", "MX" -> "MX";
            case "CHILE", "CL" -> "CL";
            case "ARGENTINA", "AR" -> "AR";
            case "BOLIVIA", "BO" -> "BO";
            case "BRASIL", "BRAZIL", "BR" -> "BR";
            case "URUGUAY", "UY" -> "UY";
            case "PARAGUAY", "PY" -> "PY";
            case "COSTARICA", "CR" -> "CR";
            case "REPUBLICADOMINICANA", "DOMINICANREPUBLIC", "DO" -> "DO";
            case "GUATEMALA", "GT" -> "GT";
            case "ESPANA", "SPAIN", "EUROPA", "EUROPE", "EU" -> "EU";
            default -> "PE";
        };
    }

    private String currencyForCountry(String countryCode) {
        return switch (countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT)) {
            case "US", "UY", "PY", "CR", "DO", "GT" -> "USD";
            case "VE" -> "VES";
            case "CO" -> "COP";
            case "MX" -> "MXN";
            case "CL" -> "CLP";
            case "AR" -> "ARS";
            case "BO" -> "USD";
            case "BR" -> "BRL";
            case "EU" -> "EUR";
            default -> "PEN";
        };
    }

    private String resolveCurrencySymbol(String currency) {
        return switch (currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT)) {
            case "USD", "COP", "MXN", "CLP", "ARS", "UYU" -> "$";
            case "VES", "BOB" -> "Bs";
            case "BRL" -> "R$";
            case "EUR" -> "EUR";
            case "PYG" -> "Gs";
            case "CRC" -> "CRC";
            case "DOP" -> "RD$";
            case "GTQ" -> "Q";
            default -> "S/";
        };
    }

    private String normalizeCountry(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase(Locale.ROOT);
    }
}
