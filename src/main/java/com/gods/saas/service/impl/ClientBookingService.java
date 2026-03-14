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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientBookingService {

    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final AppUserRepository appUserRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;

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

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        int duracion = service.getDuracionMinutos() != null ? service.getDuracionMinutos() : 30;

        LocalTime apertura = LocalTime.of(8, 0);
        LocalTime cierre = LocalTime.of(21, 0);

        List<Appointment> appointments = barberId != null
                ? appointmentRepository.findByTenant_IdAndBranch_IdAndUser_IdAndFechaOrderByHoraInicioAsc(
                tenantId, branchId, barberId, fecha)
                : appointmentRepository.findByTenant_IdAndBranch_IdAndFechaOrderByHoraInicioAsc(
                tenantId, branchId, fecha);

        List<String> slots = new ArrayList<>();

        LocalTime current = apertura;
        while (!current.plusMinutes(duracion).isAfter(cierre)) {
            LocalTime candidateStart = current;
            LocalTime candidateEnd = current.plusMinutes(duracion);

            boolean ocupado = appointments.stream().anyMatch(a ->
                    a.getHoraInicio() != null &&
                            a.getHoraFin() != null &&
                            candidateStart.isBefore(a.getHoraFin()) &&
                            candidateEnd.isAfter(a.getHoraInicio())
            );

            if (!ocupado) {
                slots.add(candidateStart.toString());
            }

            current = current.plusMinutes(15);
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

        ServiceEntity service = serviceRepository.findById(req.getServiceId())
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        AppUser barber;
        LocalDate fecha = LocalDate.parse(req.getDate());
        LocalTime horaInicio = LocalTime.parse(req.getHoraInicio());
        int duracion = service.getDuracionMinutos() != null ? service.getDuracionMinutos() : 30;
        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        if (req.getBarberId() != null) {
            barber = appUserRepository.findByIdAndTenant_Id(req.getBarberId(), tenantId)
                    .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));
        } else {
            barber = elegirBarberoDisponible(
                    tenantId,
                    req.getBranchId(),
                    fecha,
                    horaInicio,
                    horaFin
            );
        }



        List<Appointment> existing = barber != null
                ? appointmentRepository.findByTenant_IdAndBranch_IdAndUser_IdAndFechaOrderByHoraInicioAsc(
                tenantId, req.getBranchId(), barber.getId(), fecha)
                : appointmentRepository.findByTenant_IdAndBranch_IdAndFechaOrderByHoraInicioAsc(
                tenantId, req.getBranchId(), fecha);

        boolean ocupado = existing.stream().anyMatch(a ->
                a.getHoraInicio() != null &&
                        a.getHoraFin() != null &&
                        horaInicio.isBefore(a.getHoraFin()) &&
                        horaFin.isAfter(a.getHoraInicio())
        );

        if (ocupado) {
            throw new RuntimeException("Ese horario ya no está disponible");
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
        List<AppUser> barberos = appUserRepository
                .findByTenant_IdAndRolAndActivoTrue(tenantId, "BARBER")
                .stream()
                .filter(b -> b.getBranch() != null && b.getBranch().getId().equals(branchId))
                .toList();

        for (AppUser b : barberos) {
            List<Appointment> citas = appointmentRepository
                    .findByTenant_IdAndBranch_IdAndUser_IdAndFechaOrderByHoraInicioAsc(
                            tenantId, branchId, b.getId(), fecha
                    );

            boolean ocupado = citas.stream().anyMatch(a ->
                    a.getHoraInicio() != null &&
                            a.getHoraFin() != null &&
                            horaInicio.isBefore(a.getHoraFin()) &&
                            horaFin.isAfter(a.getHoraInicio())
            );

            if (!ocupado) {
                return b;
            }
        }

        throw new RuntimeException("No hay barberos disponibles para ese horario");
    }
}
