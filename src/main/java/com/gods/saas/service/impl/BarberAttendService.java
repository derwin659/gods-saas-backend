package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.QuickRegisterCustomerRequest;
import com.gods.saas.domain.dto.request.StartWalkInAttendRequest;
import com.gods.saas.domain.dto.response.BarberServiceResponse;
import com.gods.saas.domain.dto.response.CustomerLookupResponse;
import com.gods.saas.domain.dto.response.FinishAttendResponse;
import com.gods.saas.domain.dto.response.StartAttendResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BarberAttendService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");

    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final ServiceRepository serviceRepository;

    private final AppointmentRepository appointmentRepository;
    private final BranchRepository branchRepository;
    private final AppUserRepository appUserRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;

    @Transactional(readOnly = true)
    public CustomerLookupResponse findCustomerByPhone(Long tenantId, String phone) {
        String normalizedPhone = normalizePhone(phone);

        return customerRepository.findByTenant_IdAndTelefono(tenantId, normalizedPhone)
                .map(c -> CustomerLookupResponse.builder()
                        .found(true)
                        .id(c.getId())
                        .nombre(c.getNombres())
                        .apellido(c.getApellidos())
                        .phone(c.getTelefono())
                        .tenantId(c.getTenant().getId())
                        .puntosDisponibles(c.getPuntosDisponibles() != null ? c.getPuntosDisponibles() : 0)
                        .mensaje("Cliente encontrado")
                        .build())
                .orElse(CustomerLookupResponse.builder()
                        .found(false)
                        .phone(normalizedPhone)
                        .tenantId(tenantId)
                        .puntosDisponibles(0)
                        .mensaje("Cliente no encontrado")
                        .build());
    }

    @Transactional(readOnly = true)
    public List<BarberServiceResponse> listServices(Long tenantId) {
        return serviceRepository.findByTenant_IdAndActivoTrueOrderByNombreAsc(tenantId)
                .stream()
                .map(service -> BarberServiceResponse.builder()
                        .id(service.getId())
                        .nombre(service.getNombre())
                        .descripcion(service.getDescripcion())
                        .duracionMin(service.getDuracionMinutos())
                        .precio(service.getPrecio())
                        .categoria(service.getCategoria())
                        .activo(Boolean.TRUE.equals(service.getActivo()))
                        .build())
                .toList();
    }

    @Transactional
    public CustomerLookupResponse quickRegister(QuickRegisterCustomerRequest request) {
        String normalizedPhone = normalizePhone(request.getTelefono());

        if (customerRepository.existsByTenant_IdAndTelefono(request.getTenantId(), normalizedPhone)) {
            Customer existing = customerRepository
                    .findByTenant_IdAndTelefono(request.getTenantId(), normalizedPhone)
                    .orElseThrow();

            return CustomerLookupResponse.builder()
                    .found(true)
                    .id(existing.getId())
                    .nombre(existing.getNombres())
                    .apellido(existing.getApellidos())
                    .phone(existing.getTelefono())
                    .tenantId(existing.getTenant().getId())
                    .puntosDisponibles(existing.getPuntosDisponibles() != null ? existing.getPuntosDisponibles() : 0)
                    .mensaje("El cliente ya existía")
                    .build();
        }

        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));

        Customer customer = new Customer();
        customer.setTenant(tenant);
        customer.setTelefono(normalizedPhone);
        customer.setNombres(clean(request.getNombres()));
        customer.setApellidos(clean(request.getApellidos()));
        customer.setOrigenCliente(clean(request.getOrigenCliente()));
        customer.setPhoneVerified(false);
        customer.setPuntosDisponibles(0);

        Customer saved = customerRepository.save(customer);

        return CustomerLookupResponse.builder()
                .found(true)
                .id(saved.getId())
                .nombre(saved.getNombres())
                .apellido(saved.getApellidos())
                .phone(saved.getTelefono())
                .tenantId(saved.getTenant().getId())
                .puntosDisponibles(saved.getPuntosDisponibles() != null ? saved.getPuntosDisponibles() : 0)
                .mensaje("Cliente registrado correctamente")
                .build();
    }

    @Transactional
    public StartAttendResponse startWalkIn(
            Long tenantId,
            Long barberUserId,
            StartWalkInAttendRequest req
    ) {
        Branch branch = branchRepository.findById(req.getBranchId())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        if (!branch.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La sucursal no pertenece al tenant");
        }

        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        if (barber.getBranch() == null || !barber.getBranch().getId().equals(req.getBranchId())) {
            throw new RuntimeException("El barbero no pertenece a la sucursal seleccionada");
        }

        ServiceEntity service = serviceRepository.findById(req.getServiceId())
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));

        if (!service.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El servicio no pertenece al tenant");
        }

        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findByTenant_IdAndId(tenantId, req.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        }

        LocalDate fecha = LocalDate.now(BUSINESS_ZONE);
        LocalTime horaInicio = LocalTime.now(BUSINESS_ZONE).withSecond(0).withNano(0);

        int duracion = service.getDuracionMinutos() != null && service.getDuracionMinutos() > 0
                ? service.getDuracionMinutos()
                : 30;

        LocalTime horaFin = horaInicio.plusMinutes(duracion);

        validateBarberAvailability(
                tenantId,
                req.getBranchId(),
                barberUserId,
                fecha,
                horaInicio,
                horaFin
        );

        Appointment appointment = Appointment.builder()
                .tenant(branch.getTenant())
                .branch(branch)
                .customer(customer)
                .user(barber)
                .service(service)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .estado("IN_PROGRESS")
                .notas(clean(req.getNotas()))
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        return StartAttendResponse.builder()
                .appointmentId(saved.getId())
                .customerId(customer != null ? customer.getId() : null)
                .barberUserId(barber.getId())
                .serviceId(service.getId())
                .customerName(resolveCustomerName(customer))
                .serviceName(service.getNombre())
                .fecha(saved.getFecha().toString())
                .horaInicio(saved.getHoraInicio().toString())
                .horaFin(saved.getHoraFin().toString())
                .estado(saved.getEstado())
                .duracionMinutos(duracion)
                .walkIn(true)
                .build();
    }

    @Transactional
    public StartAttendResponse startReserved(
            Long tenantId,
            Long barberUserId,
            Long appointmentId
    ) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        if (!appointment.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La cita no pertenece al tenant");
        }

        if (appointment.getUser() == null || !appointment.getUser().getId().equals(barberUserId)) {
            throw new RuntimeException("La cita no pertenece al barbero autenticado");
        }

        String estado = appointment.getEstado() != null ? appointment.getEstado().trim().toUpperCase() : "";
        if (!estado.equals("CREATED") && !estado.equals("RESERVADO") && !estado.equals("EN_COLA")) {
            throw new RuntimeException("La cita no se puede iniciar en su estado actual");
        }

        appointment.setEstado("IN_PROGRESS");
        Appointment saved = appointmentRepository.save(appointment);

        return StartAttendResponse.builder()
                .appointmentId(saved.getId())
                .customerId(saved.getCustomer() != null ? saved.getCustomer().getId() : null)
                .barberUserId(saved.getUser() != null ? saved.getUser().getId() : null)
                .serviceId(saved.getService() != null ? saved.getService().getId() : null)
                .customerName(resolveCustomerName(saved.getCustomer()))
                .serviceName(saved.getService() != null ? saved.getService().getNombre() : "Servicio")
                .fecha(saved.getFecha() != null ? saved.getFecha().toString() : null)
                .horaInicio(saved.getHoraInicio() != null ? saved.getHoraInicio().toString() : null)
                .horaFin(saved.getHoraFin() != null ? saved.getHoraFin().toString() : null)
                .estado(saved.getEstado())
                .duracionMinutos(saved.getService() != null ? saved.getService().getDuracionMinutos() : null)
                .walkIn(false)
                .build();
    }

    @Transactional
    public FinishAttendResponse finishAttend(
            Long tenantId,
            Long barberUserId,
            Long appointmentId
    ) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        if (!appointment.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("La cita no pertenece al tenant");
        }

        if (appointment.getUser() == null || !appointment.getUser().getId().equals(barberUserId)) {
            throw new RuntimeException("La cita no pertenece al barbero autenticado");
        }

        if (!"IN_PROGRESS".equalsIgnoreCase(appointment.getEstado())) {
            throw new RuntimeException("Solo se puede finalizar una atención en progreso");
        }

        appointment.setEstado("COMPLETED");
        Appointment saved = appointmentRepository.save(appointment);

        return FinishAttendResponse.builder()
                .appointmentId(saved.getId())
                .estado(saved.getEstado())
                .fecha(saved.getFecha() != null ? saved.getFecha().toString() : null)
                .horaInicio(saved.getHoraInicio() != null ? saved.getHoraInicio().toString() : null)
                .horaFin(saved.getHoraFin() != null ? saved.getHoraFin().toString() : null)
                .build();
    }

    private void validateBarberAvailability(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
        BarberAvailability availability = barberAvailabilityRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndDayOfWeek(
                        tenantId,
                        branchId,
                        barberId,
                        fecha.getDayOfWeek().getValue()
                )
                .filter(a -> Boolean.TRUE.equals(a.getIsWorking()))
                .orElseThrow(() -> new RuntimeException("El barbero no trabaja en ese horario"));

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            throw new RuntimeException("La atención excede el horario disponible del barbero");
        }

        boolean blocked = barberTimeBlockRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndBlockDateOrderByStartTimeAsc(
                        tenantId, branchId, barberId, fecha
                )
                .stream()
                .anyMatch(b ->
                        b.getStartTime() != null &&
                                b.getEndTime() != null &&
                                horaInicio.isBefore(b.getEndTime()) &&
                                horaFin.isAfter(b.getStartTime())
                );

        if (blocked) {
            throw new RuntimeException("Ese horario está bloqueado manualmente");
        }

        long conflicts = appointmentRepository.countConflictingAppointments(
                tenantId,
                branchId,
                barberId,
                fecha,
                horaInicio,
                horaFin
        );

        if (conflicts > 0) {
            throw new RuntimeException("El barbero ya tiene una atención o reserva en ese horario");
        }
    }

    private String resolveCustomerName(Customer customer) {
        if (customer == null) return "Cliente sin registrar";
        String nombres = customer.getNombres() != null ? customer.getNombres().trim() : "";
        String apellidos = customer.getApellidos() != null ? customer.getApellidos().trim() : "";
        String fullName = (nombres + " " + apellidos).trim();
        return fullName.isEmpty() ? "Cliente" : fullName;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "").trim();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}