package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.request.UpdateOwnerAppointmentRequest;
import com.gods.saas.domain.dto.response.OwnerAgendaResponse;
import com.gods.saas.domain.dto.response.OwnerAppointmentAvailabilityResponse;
import com.gods.saas.domain.dto.response.OwnerAppointmentSlotResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerAgendaAppointmentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Lima");
    private static final LocalTime DEFAULT_OPENING_TIME = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSING_TIME = LocalTime.of(21, 0);
    private static final int SLOT_INTERVAL_MINUTES = 60;

    private final AppointmentRepository appointmentRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository appUserRepository;
    private final ServiceRepository serviceRepository;
    private final BarberAvailabilityRepository barberAvailabilityRepository;
    private final BarberTimeBlockRepository barberTimeBlockRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final NotificationService notificationService;

    @Transactional
    public OwnerAgendaResponse createAppointment(Long tenantId, Long branchId, CreateOwnerAppointmentRequest request) {
        Long effectiveBranchId = request.getBranchId() != null ? request.getBranchId() : branchId;

        if (effectiveBranchId == null) throw new RuntimeException("La sede es obligatoria");
        if (request.getCustomerId() == null) throw new RuntimeException("El cliente es obligatorio");
        if (request.getServiceId() == null) throw new RuntimeException("El servicio es obligatorio");
        if (request.getBarberUserId() == null) throw new RuntimeException("El barbero es obligatorio");
        if (request.getFecha() == null || request.getFecha().isBlank()) throw new RuntimeException("La fecha es obligatoria");
        if (request.getHoraInicio() == null || request.getHoraInicio().isBlank()) throw new RuntimeException("La hora inicio es obligatoria");

        Branch branch = getBranch(tenantId, effectiveBranchId);
        Customer customer = getCustomer(tenantId, request.getCustomerId());
        ServiceEntity service = getService(tenantId, request.getServiceId());
        AppUser barber = getBarber(tenantId, effectiveBranchId, request.getBarberUserId());

        LocalDate fecha = LocalDate.parse(request.getFecha());
        LocalTime horaInicio = LocalTime.parse(request.getHoraInicio());
        LocalTime horaFin = resolveHoraFin(horaInicio, request.getHoraFin(), service);

        validateBookingDate(fecha);
        validateTimeNotPast(fecha, horaInicio);
        validateSlotAvailable(tenantId, effectiveBranchId, barber.getId(), fecha, horaInicio, horaFin, null);

        BigDecimal totalAmount = resolveServicePrice(service);
        boolean depositRequired = Boolean.TRUE.equals(request.getDepositRequired());
        BigDecimal depositAmount = depositRequired ? normalizeMoney(request.getDepositAmount()) : BigDecimal.ZERO;

        validateDepositAmount(totalAmount, depositAmount);

        Appointment appointment = Appointment.builder()
                .tenant(branch.getTenant())
                .branch(branch)
                .customer(customer)
                .service(service)
                .user(barber)
                .fecha(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .estado(depositRequired ? "PENDING_DEPOSIT_VALIDATION" : "RESERVADO")
                .notas(clean(request.getNotas()))
                .originalAmount(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .depositRequired(depositRequired)
                .depositAmount(depositAmount)
                .remainingAmount(totalAmount.subtract(depositAmount).max(BigDecimal.ZERO))
                .depositStatus(depositRequired ? "PENDING_VALIDATION" : "NOT_REQUIRED")
                .depositMethodCode(clean(request.getDepositMethodCode()))
                .depositMethodName(clean(request.getDepositMethodName()))
                .depositOperationCode(clean(request.getDepositOperationCode()))
                .depositEvidenceUrl(clean(request.getDepositEvidenceUrl()))
                .depositNote(clean(request.getDepositNote()))
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);
        return toAgendaResponse(saved);
    }

    @Transactional
    public OwnerAgendaResponse updateAppointment(Long tenantId, Long branchId, Long appointmentId, UpdateOwnerAppointmentRequest request) {
        Appointment appointment = appointmentRepository.findByIdAndTenant_Id(appointmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        Long effectiveBranchId = branchId != null ? branchId : appointment.getBranch().getId();
        if (appointment.getBranch() == null || !appointment.getBranch().getId().equals(effectiveBranchId)) {
            throw new RuntimeException("La cita no pertenece a la sede seleccionada");
        }

        Customer customer = request.getCustomerId() != null
                ? getCustomer(tenantId, request.getCustomerId())
                : appointment.getCustomer();

        ServiceEntity service = request.getServiceId() != null
                ? getService(tenantId, request.getServiceId())
                : appointment.getService();

        AppUser barber = request.getBarberUserId() != null
                ? getBarber(tenantId, effectiveBranchId, request.getBarberUserId())
                : appointment.getUser();

        if (service == null) throw new RuntimeException("La cita no tiene servicio asociado");
        if (barber == null) throw new RuntimeException("La cita no tiene barbero asociado");

        LocalDate fecha = request.getFecha() != null && !request.getFecha().isBlank()
                ? LocalDate.parse(request.getFecha())
                : appointment.getFecha();

        LocalTime horaInicio = request.getHoraInicio() != null && !request.getHoraInicio().isBlank()
                ? LocalTime.parse(request.getHoraInicio())
                : appointment.getHoraInicio();

        if (fecha == null) throw new RuntimeException("La cita no tiene fecha asociada");
        if (horaInicio == null) throw new RuntimeException("La cita no tiene hora inicio asociada");

        LocalTime horaFin = resolveHoraFin(horaInicio, request.getHoraFin(), service);

        validateBookingDate(fecha);
        validateTimeNotPast(fecha, horaInicio);
        validateSlotAvailable(tenantId, effectiveBranchId, barber.getId(), fecha, horaInicio, horaFin, appointment.getId());

        BigDecimal totalAmount = resolveServicePrice(service);
        boolean depositRequired = request.getDepositRequired() != null
                ? Boolean.TRUE.equals(request.getDepositRequired())
                : Boolean.TRUE.equals(appointment.getDepositRequired());

        BigDecimal depositAmount = request.getDepositAmount() != null
                ? normalizeMoney(request.getDepositAmount())
                : normalizeMoney(appointment.getDepositAmount());

        if (!depositRequired) depositAmount = BigDecimal.ZERO;

        validateDepositAmount(totalAmount, depositAmount);

        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setUser(barber);
        appointment.setFecha(fecha);
        appointment.setHoraInicio(horaInicio);
        appointment.setHoraFin(horaFin);
        appointment.setNotas(clean(request.getNotas()));
        appointment.setOriginalAmount(totalAmount);
        appointment.setDiscountAmount(BigDecimal.ZERO);
        appointment.setTotalAmount(totalAmount);
        appointment.setDepositRequired(depositRequired);
        appointment.setDepositAmount(depositAmount);
        appointment.setRemainingAmount(totalAmount.subtract(depositAmount).max(BigDecimal.ZERO));

        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            appointment.setEstado(request.getEstado().trim());
        } else if (depositRequired && "NOT_REQUIRED".equalsIgnoreCase(nullSafe(appointment.getDepositStatus()))) {
            appointment.setEstado("PENDING_DEPOSIT_VALIDATION");
        }

        if (!depositRequired) {
            appointment.setDepositStatus("NOT_REQUIRED");
        } else if (appointment.getDepositStatus() == null || appointment.getDepositStatus().isBlank()
                || "NOT_REQUIRED".equalsIgnoreCase(appointment.getDepositStatus())) {
            appointment.setDepositStatus("PENDING_VALIDATION");
        }

        if (request.getDepositMethodCode() != null) appointment.setDepositMethodCode(clean(request.getDepositMethodCode()));
        if (request.getDepositMethodName() != null) appointment.setDepositMethodName(clean(request.getDepositMethodName()));
        if (request.getDepositOperationCode() != null) appointment.setDepositOperationCode(clean(request.getDepositOperationCode()));
        if (request.getDepositEvidenceUrl() != null) appointment.setDepositEvidenceUrl(clean(request.getDepositEvidenceUrl()));
        if (request.getDepositNote() != null) appointment.setDepositNote(clean(request.getDepositNote()));

        return toAgendaResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public OwnerAgendaResponse validateDeposit(
            Long tenantId,
            Long branchId,
            Long appointmentId,
            Long actorUserId,
            boolean approved,
            String note
    ) {
        Appointment appointment = appointmentRepository.findByIdAndTenant_Id(appointmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        if (branchId != null
                && appointment.getBranch() != null
                && !appointment.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("La cita no pertenece a la sede seleccionada");
        }

        if (!Boolean.TRUE.equals(appointment.getDepositRequired())) {
            throw new RuntimeException("Esta cita no requiere pago inicial");
        }

        appointment.setDepositValidatedAt(LocalDateTime.now(BUSINESS_ZONE));
        appointment.setDepositValidatedByUserId(actorUserId);

        String cleanNote = clean(note);
        if (cleanNote != null) {
            appointment.setDepositNote(cleanNote);
        }

        if (approved) {
            appointment.setDepositStatus("PAID");
            String currentStatus = nullSafe(appointment.getEstado()).toUpperCase();
            if (currentStatus.isBlank()
                    || "PENDING_DEPOSIT_VALIDATION".equals(currentStatus)
                    || "DEPOSIT_REJECTED".equals(currentStatus)
                    || "REJECTED".equals(currentStatus)) {
                appointment.setEstado("RESERVADO");
            }
        } else {
            appointment.setDepositStatus("REJECTED");
            appointment.setEstado("DEPOSIT_REJECTED");
        }

        return toAgendaResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public OwnerAgendaResponse cancelAppointment(Long tenantId, Long branchId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndTenant_Id(appointmentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada"));

        if (branchId != null && appointment.getBranch() != null && !appointment.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("La cita no pertenece a la sede seleccionada");
        }

        appointment.setEstado("CANCELADO");

        return toAgendaResponse(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public OwnerAppointmentAvailabilityResponse getAvailability(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            Long serviceId,
            LocalDate fecha,
            Long appointmentIdToIgnore
    ) {
        if (branchId == null) throw new RuntimeException("La sede es obligatoria");
        if (barberUserId == null) throw new RuntimeException("El barbero es obligatorio");
        if (serviceId == null) throw new RuntimeException("El servicio es obligatorio");

        getBranch(tenantId, branchId);
        ServiceEntity service = getService(tenantId, serviceId);
        AppUser barber = getBarber(tenantId, branchId, barberUserId);

        int duration = getServiceDuration(service);

        BarberAvailability availability = getWorkingAvailabilityOrNull(tenantId, branchId, barber.getId(), fecha);

        LocalTime opening = availability != null && availability.getStartTime() != null
                ? availability.getStartTime()
                : DEFAULT_OPENING_TIME;

        LocalTime closing = availability != null && availability.getEndTime() != null
                ? availability.getEndTime()
                : DEFAULT_CLOSING_TIME;

        LocalTime current = normalizeStartTime(fecha, opening);
        List<OwnerAppointmentSlotResponse> slots = new ArrayList<>();

        while (!current.plusMinutes(duration).isAfter(closing)) {
            LocalTime end = current.plusMinutes(duration);
            SlotStatus status = resolveSlotStatus(
                    tenantId, branchId, barber.getId(), fecha, current, end, appointmentIdToIgnore, availability
            );

            slots.add(OwnerAppointmentSlotResponse.builder()
                    .hora(current.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .horaFin(end.format(DateTimeFormatter.ofPattern("HH:mm")))
                    .available(status.available())
                    .reason(status.reason())
                    .appointmentId(status.appointmentId())
                    .build());

            current = current.plusMinutes(SLOT_INTERVAL_MINUTES);
        }

        return OwnerAppointmentAvailabilityResponse.builder()
                .fecha(fecha.toString())
                .branchId(branchId)
                .barberUserId(barberUserId)
                .serviceId(serviceId)
                .serviceDurationMinutes(duration)
                .slots(slots)
                .build();
    }

    private Branch getBranch(Long tenantId, Long branchId) {
        return branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    private Customer getCustomer(Long tenantId, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        if (customer.getTenant() == null || !customer.getTenant().getId().equals(tenantId)) {
            throw new RuntimeException("El cliente no pertenece al tenant");
        }

        return customer;
    }

    private ServiceEntity getService(Long tenantId, Long serviceId) {
        return serviceRepository.findByIdAndTenant_Id(serviceId, tenantId)
                .orElseThrow(() -> new RuntimeException("Servicio no encontrado"));
    }

    private AppUser getBarber(Long tenantId, Long branchId, Long barberUserId) {
        AppUser barber = appUserRepository.findByIdAndTenant_Id(barberUserId, tenantId)
                .orElseThrow(() -> new RuntimeException("Barbero no encontrado"));

        boolean belongsToBranch = userTenantRoleRepository.existsByUser_IdAndTenant_IdAndBranch_Id(
                barber.getId(),
                tenantId,
                branchId
        );

        if (!belongsToBranch) throw new RuntimeException("El barbero no pertenece a esta sucursal");

        return barber;
    }

    private LocalTime resolveHoraFin(LocalTime horaInicio, String rawHoraFin, ServiceEntity service) {
        if (rawHoraFin != null && !rawHoraFin.isBlank()) {
            LocalTime parsed = LocalTime.parse(rawHoraFin);
            if (!horaInicio.isBefore(parsed)) throw new RuntimeException("La hora fin debe ser mayor a la hora inicio");
            return parsed;
        }

        return horaInicio.plusMinutes(getServiceDuration(service));
    }

    private int getServiceDuration(ServiceEntity service) {
        Integer duration = service.getDuracionMinutos();
        return duration != null && duration > 0 ? duration : 30;
    }

    private BigDecimal resolveServicePrice(ServiceEntity service) {
        BigDecimal price = BigDecimal.valueOf(service.getPrecio());
        return price != null ? price : BigDecimal.ZERO;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void validateDepositAmount(BigDecimal totalAmount, BigDecimal depositAmount) {
        if (depositAmount.compareTo(BigDecimal.ZERO) < 0) throw new RuntimeException("El pago inicial no puede ser negativo");
        if (depositAmount.compareTo(totalAmount) > 0) throw new RuntimeException("El pago inicial no puede superar el total del servicio");
    }

    private void validateBookingDate(LocalDate fecha) {
        if (fecha == null) throw new RuntimeException("La fecha es obligatoria");
        if (fecha.isBefore(LocalDate.now(BUSINESS_ZONE))) throw new RuntimeException("No se puede reservar en una fecha pasada");
    }

    private void validateTimeNotPast(LocalDate fecha, LocalTime horaInicio) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);

        if (fecha.equals(today) && !horaInicio.isAfter(now)) {
            throw new RuntimeException("No se puede reservar una hora pasada");
        }
    }

    private void validateSlotAvailable(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long appointmentIdToIgnore
    ) {
        BarberAvailability availability = getWorkingAvailabilityOrNull(tenantId, branchId, barberId, fecha);
        if (availability == null) throw new RuntimeException("El barbero no trabaja ese día");

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            throw new RuntimeException("La cita excede el horario disponible del barbero");
        }

        if (isBlockedByManualBlock(tenantId, branchId, barberId, fecha, horaInicio, horaFin)) {
            throw new RuntimeException("Ese horario está bloqueado manualmente");
        }

        if (findConflictingAppointment(tenantId, branchId, barberId, fecha, horaInicio, horaFin, appointmentIdToIgnore) != null) {
            throw new RuntimeException("El barbero ya tiene una cita en ese horario");
        }
    }

    private SlotStatus resolveSlotStatus(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long appointmentIdToIgnore,
            BarberAvailability availability
    ) {
        if (availability == null) return new SlotStatus(false, "El barbero no trabaja ese día", null);

        if (horaInicio.isBefore(availability.getStartTime()) || horaFin.isAfter(availability.getEndTime())) {
            return new SlotStatus(false, "Fuera de horario laboral", null);
        }

        if (fecha.equals(LocalDate.now(BUSINESS_ZONE)) && !horaInicio.isAfter(LocalTime.now(BUSINESS_ZONE))) {
            return new SlotStatus(false, "Hora pasada", null);
        }

        if (isBlockedByManualBlock(tenantId, branchId, barberId, fecha, horaInicio, horaFin)) {
            return new SlotStatus(false, "Horario bloqueado", null);
        }

        Appointment conflict = findConflictingAppointment(tenantId, branchId, barberId, fecha, horaInicio, horaFin, appointmentIdToIgnore);
        if (conflict != null) return new SlotStatus(false, "Cita existente", conflict.getId());

        return new SlotStatus(true, null, null);
    }

    private boolean isBlockedByManualBlock(Long tenantId, Long branchId, Long barberId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        List<BarberTimeBlock> blocks = barberTimeBlockRepository
                .findByTenant_IdAndBranch_IdAndBarber_IdAndBlockDateOrderByStartTimeAsc(
                        tenantId, branchId, barberId, fecha
                );

        return blocks.stream().anyMatch(block ->
                block.getStartTime() != null &&
                        block.getEndTime() != null &&
                        horaInicio.isBefore(block.getEndTime()) &&
                        horaFin.isAfter(block.getStartTime())
        );
    }

    private Appointment findConflictingAppointment(
            Long tenantId,
            Long branchId,
            Long barberId,
            LocalDate fecha,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long appointmentIdToIgnore
    ) {
        return appointmentRepository
                .findActiveAppointmentsByBarberAndDate(tenantId, branchId, barberId, fecha)
                .stream()
                .filter(a -> appointmentIdToIgnore == null || !a.getId().equals(appointmentIdToIgnore))
                .filter(a -> a.getHoraInicio() != null && a.getHoraFin() != null)
                .filter(a -> horaInicio.isBefore(a.getHoraFin()) && horaFin.isAfter(a.getHoraInicio()))
                .findFirst()
                .orElse(null);
    }

    private BarberAvailability getWorkingAvailabilityOrNull(Long tenantId, Long branchId, Long barberId, LocalDate fecha) {
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

    private LocalTime normalizeStartTime(LocalDate fecha, LocalTime opening) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (!fecha.equals(today)) return opening;

        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        if (now.isBefore(opening)) return opening;

        int minute = now.getMinute();
        int nextQuarter = ((minute / SLOT_INTERVAL_MINUTES) + 1) * SLOT_INTERVAL_MINUTES;

        LocalTime rounded = now.withSecond(0).withNano(0);
        if (nextQuarter >= 60) {
            rounded = rounded.plusHours(1).withMinute(0);
        } else {
            rounded = rounded.withMinute(nextQuarter);
        }

        return rounded.isAfter(opening) ? rounded : opening;
    }

    private OwnerAgendaResponse toAgendaResponse(Appointment a) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        BigDecimal precioServicio = a.getTotalAmount() != null ? a.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal originalAmount = a.getOriginalAmount() != null ? a.getOriginalAmount() : precioServicio;
        BigDecimal discountAmount = a.getDiscountAmount() != null ? a.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal totalAmount = a.getTotalAmount() != null
                ? a.getTotalAmount()
                : originalAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal montoPagoInicial = a.getDepositAmount() != null ? a.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal saldoPendiente = a.getRemainingAmount() != null ? a.getRemainingAmount() : precioServicio.subtract(montoPagoInicial);
        if (saldoPendiente.compareTo(BigDecimal.ZERO) < 0) saldoPendiente = BigDecimal.ZERO;

        Boolean requierePagoInicial = Boolean.TRUE.equals(a.getDepositRequired());
        String depositStatus = a.getDepositStatus() != null && !a.getDepositStatus().isBlank()
                ? a.getDepositStatus()
                : (requierePagoInicial ? "PENDING_VALIDATION" : "NOT_REQUIRED");

        Boolean pagoInicialValidado = "PAID".equalsIgnoreCase(depositStatus) || a.getDepositValidatedAt() != null;
        String estadoPagoInicial = mapDepositStatusToFrontend(depositStatus, requierePagoInicial);

        return OwnerAgendaResponse.builder()
                .appointmentId(a.getId())
                .customerId(a.getCustomer() != null ? a.getCustomer().getId() : null)
                .serviceId(a.getService() != null ? a.getService().getId() : null)
                .barberUserId(a.getUser() != null ? a.getUser().getId() : null)
                .branchId(a.getBranch() != null ? a.getBranch().getId() : null)
                .fecha(a.getFecha() != null ? a.getFecha().toString() : null)
                .hora(a.getHoraInicio() != null ? a.getHoraInicio().format(timeFormatter) : "")
                .horaFin(a.getHoraFin() != null ? a.getHoraFin().format(timeFormatter) : "")
                .cliente(a.getCustomer() != null ? resolveCustomerName(a.getCustomer()) : "Cliente")
                .telefono(a.getCustomer() != null ? a.getCustomer().getTelefono() : null)
                .servicio(a.getService() != null ? a.getService().getNombre() : "Servicio")
                .barbero(a.getUser() != null ? resolveUserName(a.getUser()) : "Sin asignar")
                .estado(a.getEstado() != null ? a.getEstado() : "RESERVADO")
                .promotionTitle(a.getPromotionTitle())
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .requierePagoInicial(requierePagoInicial)
                .montoPagoInicial(montoPagoInicial)
                .precioServicio(precioServicio)
                .saldoPendiente(saldoPendiente)
                .estadoPagoInicial(estadoPagoInicial)
                .pagoInicialValidado(pagoInicialValidado)
                .metodoPagoInicial(resolveDepositMethod(a))
                .numeroOperacionPagoInicial(a.getDepositOperationCode())
                .comprobantePagoInicialUrl(a.getDepositEvidenceUrl())
                .build();
    }

    private String resolveDepositMethod(Appointment appointment) {
        if (appointment == null) return null;

        if (appointment.getDepositMethodName() != null && !appointment.getDepositMethodName().isBlank()) {
            return appointment.getDepositMethodName();
        }

        if (appointment.getDepositMethodCode() != null && !appointment.getDepositMethodCode().isBlank()) {
            return appointment.getDepositMethodCode();
        }

        if (appointment.getDepositPaymentMethod() != null) {
            return appointment.getDepositPaymentMethod().getDisplayName();
        }

        return null;
    }

    private String mapDepositStatusToFrontend(String depositStatus, Boolean required) {
        if (!Boolean.TRUE.equals(required)) return "NO_REQUIERE";

        String status = depositStatus == null ? "" : depositStatus.trim().toUpperCase();

        return switch (status) {
            case "PAID", "VALIDADO", "VALIDATED" -> "VALIDADO";
            case "REJECTED", "RECHAZADO" -> "RECHAZADO";
            case "PENDING_VALIDATION", "PENDIENTE_VALIDACION", "PENDING_DEPOSIT_VALIDATION" -> "PENDIENTE_VALIDACION";
            default -> "PENDIENTE_VALIDACION";
        };
    }

    private String resolveCustomerName(Customer customer) {
        if (customer == null) return "Cliente";
        String nombres = customer.getNombres() != null ? customer.getNombres().trim() : "";
        String apellidos = customer.getApellidos() != null ? customer.getApellidos().trim() : "";
        String fullName = (nombres + " " + apellidos).trim();
        return fullName.isBlank() ? "Cliente" : fullName;
    }

    private String resolveUserName(AppUser user) {
        if (user == null) return "Sin asignar";
        String nombre = user.getNombre() != null ? user.getNombre().trim() : "";
        String apellido = user.getApellido() != null ? user.getApellido().trim() : "";
        String fullName = (nombre + " " + apellido).trim();
        return fullName.isBlank() ? "Barbero" : fullName;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private record SlotStatus(boolean available, String reason, Long appointmentId) {}
}
