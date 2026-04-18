package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientBookingService {

    private static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(21, 0);
    private static final int SLOT_INTERVAL_MINUTES = 15;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final AppUserRepository appUserRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;
    private final NotificationService notificationService;

    public BookingBootstrapResponse getBootstrap(Long tenantId) {
        List<Branch> branches = branchRepository.findByTenant_IdAndActivoTrue(tenantId);
        List<ServiceEntity> services = serviceRepository.findByTenant_IdAndActivoTrue(tenantId);
        List<AppUser> barbers = appUserRepository.findByTenant_IdAndRolAndActivoTrue(tenantId, "BARBER");

        return BookingBootstrapResponse.builder()
                .branches(branches.stream().map(BranchMiniResponse::fromEntity).toList())
                .services(services.stream().map(ServiceMiniResponse::fromEntity).toList())
                .barbers(barbers.stream().map(BarberMiniResponse::fromEntity).toList())
                .build();
    }

    public BookingAvailabilityResponse getAvailability(
            Long tenantId,
            Long branchId,
            Long serviceId,
            String date,
            Long barberId
    ) {
        log.info("BOOKING AVAILABILITY START => tenantId={}, branchId={}, serviceId={}, date={}, barberId={}",
                tenantId, branchId, serviceId, date, barberId);

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

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        log.info("SERVICE FOUND => serviceId={}, tenantId={}, duracionMinutos={}",
                service.getId(),
                service.getTenant() != null ? service.getTenant().getId() : null,
                service.getDuracionMinutos());

        if (!service.getTenant().getId().equals(tenantId)) {
            log.warn("SERVICE TENANT MISMATCH => expectedTenantId={}, serviceTenantId={}",
                    tenantId, service.getTenant().getId());
            throw new RuntimeException("El servicio no pertenece al tenant");
        }

        int duracion = getServiceDuration(service);
        List<String> slots = new ArrayList<>();

        log.info("SERVICE DURATION RESOLVED => {} minutos", duracion);

        if (barberId != null) {
            AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

            log.info("BARBER FOUND => barberId={}, nombre={} {}, barberBranchId={}, tenantId={}",
                    barber.getId(),
                    barber.getNombre(),
                    barber.getApellido(),
                    barber.getBranch() != null ? barber.getBranch().getId() : null,
                    barber.getTenant() != null ? barber.getTenant().getId() : null);

            validateBarberBranch(barber, branchId);

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
            LocalTime current = normalizeStartTime(fecha, apertura);

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

                current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
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

            LocalTime current = normalizeStartTime(fecha, apertura);

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

                current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
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

        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        ServiceEntity service = serviceRepository.findById(req.getServiceId())
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        if (!service.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El servicio no pertenece al tenant");
        }

        LocalDate fecha = LocalDate.parse(req.getDate());
        LocalTime horaInicio = LocalTime.parse(req.getHoraInicio());

        validateBookingDate(fecha);
        validateTimeNotPast(fecha, horaInicio);

        int duracion = getServiceDuration(service);
        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        AppUser barber;
        if (req.getBarberId() != null) {
            barber = appUserRepository.findByIdAndTenant_Id(req.getBarberId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

            validateBarberBranch(barber, req.getBranchId());

            if (!isBarberAvailableConsideringAllRules(
                    tenantId, req.getBranchId(), barber.getId(), fecha, horaInicio, horaFin
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

        Appointment appointment = Appointment.builder()
                .tenant(customer.getTenant())
                .branch(branch)
                .customer(customer)
                .user(barber)
                .service(service)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .estado("CREATED")
                .notas(null)
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);



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
        List<AppUser> allBarbers = appUserRepository.findByTenant_IdAndRolAndActivoTrue(tenantId, "BARBER");

        log.info("ALL ACTIVE TENANT BARBERS => tenantId={}, count={}, barberIds={}",
                tenantId,
                allBarbers.size(),
                allBarbers.stream().map(AppUser::getId).toList());

        List<AppUser> filtered = allBarbers.stream()
                .filter(b -> b.getBranch() != null && b.getBranch().getId().equals(branchId))
                .sorted(Comparator.comparing(AppUser::getId))
                .toList();

        log.info("ACTIVE BRANCH BARBERS FILTERED => branchId={}, count={}, barberIds={}",
                branchId,
                filtered.size(),
                filtered.stream().map(AppUser::getId).toList());

        return filtered;
    }

    private void validateBarberBranch(AppUser barber, Long branchId) {
        Long barberBranchId = barber.getBranch() != null ? barber.getBranch().getId() : null;

        log.info("VALIDATING BARBER BRANCH => barberId={}, barberBranchId={}, requestedBranchId={}",
                barber.getId(), barberBranchId, branchId);

        if (barber.getBranch() == null || !barber.getBranch().getId().equals(branchId)) {
            log.warn("BARBER BRANCH MISMATCH => barberId={}, barberBranchId={}, requestedBranchId={}",
                    barber.getId(), barberBranchId, branchId);
            throw new RuntimeException("El barbero no pertenece a la sucursal seleccionada");
        }
    }

    private int getServiceDuration(ServiceEntity service) {
        return service.getDuracionMinutos() != null && service.getDuracionMinutos() > 0
                ? service.getDuracionMinutos()
                : 30;
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

    private LocalTime normalizeStartTime(LocalDate fecha, LocalTime apertura) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        if (!fecha.equals(today)) {
            return apertura;
        }

        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        LocalTime rounded = roundUpToNextSlot(now);

        log.info("NORMALIZE START TIME => fecha={}, today={}, nowLima={}, rounded={}, apertura={}",
                fecha, today, now, rounded, apertura);

        if (rounded.isBefore(apertura)) {
            return apertura;
        }

        return rounded;
    }

    private LocalTime roundUpToNextSlot(LocalTime time) {
        int minute = time.getMinute();
        int remainder = minute % SLOT_INTERVAL_MINUTES;
        int minutesToAdd = remainder == 0 ? SLOT_INTERVAL_MINUTES : SLOT_INTERVAL_MINUTES - remainder;

        return time.withSecond(0).withNano(0).plusMinutes(minutesToAdd);
    }
}
