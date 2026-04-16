package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientBookingService {

    private static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(21, 0);
    private static final int SLOT_INTERVAL_MINUTES = 15;

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final AppUserRepository appUserRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;

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
        LocalDate fecha = LocalDate.parse(date);
        validateBookingDate(fecha);

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        if (!service.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El servicio no pertenece al tenant");
        }

        int duracion = getServiceDuration(service);
        List<String> slots = new ArrayList<>();

        if (barberId != null) {
            AppUser barber = appUserRepository.findByIdAndTenant_Id(barberId, tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

            validateBarberBranch(barber, branchId);

            BarberAvailability availability = getWorkingAvailabilityOrNull(
                    tenantId, branchId, barberId, fecha
            );

            if (availability == null) {
                return BookingAvailabilityResponse.builder()
                        .date(date)
                        .slots(List.of())
                        .build();
            }

            LocalTime apertura = availability.getStartTime();
            LocalTime cierre = availability.getEndTime();
            LocalTime current = normalizeStartTime(fecha, apertura);

            while (!current.plusMinutes(duracion).isAfter(cierre)) {
                LocalTime candidateStart = current;
                LocalTime candidateEnd = current.plusMinutes(duracion);

                if (isBarberAvailableConsideringAllRules(
                        tenantId, branchId, barberId, fecha, candidateStart, candidateEnd
                )) {
                    slots.add(candidateStart.toString());
                }

                current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        } else {
            List<AppUser> barberos = getActiveBranchBarbers(tenantId, branchId);

            List<AppUser> availableBarbersForDay = barberos.stream()
                    .filter(b -> getWorkingAvailabilityOrNull(tenantId, branchId, b.getId(), fecha) != null)
                    .sorted(Comparator.comparing(AppUser::getId))
                    .toList();

            if (availableBarbersForDay.isEmpty()) {
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

            while (!current.plusMinutes(duracion).isAfter(cierre)) {
                LocalTime candidateStart = current;
                LocalTime candidateEnd = current.plusMinutes(duracion);

                boolean anyAvailable = availableBarbersForDay.stream()
                        .anyMatch(b -> isBarberAvailableConsideringAllRules(
                                tenantId, branchId, b.getId(), fecha, candidateStart, candidateEnd
                        ));

                if (anyAvailable) {
                    slots.add(candidateStart.toString());
                }

                current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
            }
        }

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
        BarberAvailability availability = getWorkingAvailabilityOrNull(tenantId, branchId, barberId, fecha);
        if (availability == null) {
            return false;
        }

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            return false;
        }

        if (isBlockedByManualBlock(tenantId, branchId, barberId, fecha, horaInicio, horaFin)) {
            return false;
        }

        if (fecha.equals(LocalDate.now()) && !horaInicio.isAfter(LocalTime.now())) {
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

        return conflicts == 0;
    }

    private BarberAvailability getWorkingAvailabilityOrNull(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha
    ) {
        return barberAvailabilityRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndDayOfWeek(
                        tenantId,
                        branchId,
                        barberId,
                        fecha.getDayOfWeek().getValue()
                )
                .filter(a -> Boolean.TRUE.equals(a.getIsWorking()))
                .orElse(null);
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

        return blocks.stream().anyMatch(b ->
                b.getStartTime() != null &&
                        b.getEndTime() != null &&
                        horaInicio.isBefore(b.getEndTime()) &&
                        horaFin.isAfter(b.getStartTime())
        );
    }

    private List<AppUser> getActiveBranchBarbers(Long tenantId, Long branchId) {
        return appUserRepository.findByTenant_IdAndRolAndActivoTrue(tenantId, "BARBER")
                .stream()
                .filter(b -> b.getBranch() != null && b.getBranch().getId().equals(branchId))
                .sorted(Comparator.comparing(AppUser::getId))
                .toList();
    }

    private void validateBarberBranch(AppUser barber, Long branchId) {
        if (barber.getBranch() == null || !barber.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("El barbero no pertenece a la sucursal seleccionada");
        }
    }

    private int getServiceDuration(ServiceEntity service) {
        return service.getDuracionMinutos() != null && service.getDuracionMinutos() > 0
                ? service.getDuracionMinutos()
                : 30;
    }

    private void validateBookingDate(LocalDate fecha) {
        if (fecha.isBefore(LocalDate.now())) {
            throw new RuntimeException("No se puede reservar en una fecha pasada");
        }
    }

    private void validateTimeNotPast(LocalDate fecha, LocalTime horaInicio) {
        if (fecha.equals(LocalDate.now()) && !horaInicio.isAfter(LocalTime.now())) {
            throw new RuntimeException("No se puede reservar una hora pasada");
        }
    }

    private LocalTime normalizeStartTime(LocalDate fecha, LocalTime apertura) {
        if (!fecha.equals(LocalDate.now())) {
            return apertura;
        }

        LocalTime now = LocalTime.now();
        LocalTime rounded = roundUpToNextSlot(now);

        if (rounded.isBefore(apertura)) {
            return apertura;
        }

        return rounded;
    }

    private LocalTime roundUpToNextSlot(LocalTime time) {
        int minute = time.getMinute();
        int remainder = minute % SLOT_INTERVAL_MINUTES;
        int minutesToAdd = remainder == 0 ? SLOT_INTERVAL_MINUTES : SLOT_INTERVAL_MINUTES - remainder;

        LocalTime rounded = time.withSecond(0).withNano(0).plusMinutes(minutesToAdd);
        return rounded;
    }
}