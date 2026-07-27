package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.CustomerRepository;
import com.gods.saas.domain.repository.NotificationDeliveryRepository;
import com.gods.saas.domain.repository.NotificationRepository;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final CustomerRepository customerRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final OwnerWhatsappPhoneVerificationService ownerWhatsappPhoneVerificationService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void notifyBookingCreated(Appointment appointment, boolean customerInitiated) {
        String serviceName = appointment.getService() != null ? appointment.getService().getNombre() : "Servicio";
        String date = appointment.getFecha() != null ? appointment.getFecha().format(DATE_FMT) : "";
        String time = appointment.getHoraInicio() != null ? appointment.getHoraInicio().format(TIME_FMT) : "";

        if (appointment.getCustomer() != null) {
            Notification n = saveCustomerNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    appointment.getCustomer(),
                    NotificationType.BOOKING_CREATED,
                    "Reserva confirmada",
                    "Tu reserva de " + serviceName + " fue registrada para el " + date + " a las " + time + ".",
                    "APPOINTMENT",
                    appointment.getId()
            );
            registerDefaultChannels(n, true);
        }

        notifyBookingTeamCreated(appointment, serviceName, date, time, customerInitiated);
    }


    @Override
    public void notifyBookingCancelledByClient(Appointment appointment) {
        if (appointment == null || appointment.getTenant() == null) return;

        String serviceName = appointment.getService() != null ? appointment.getService().getNombre() : "Servicio";
        String date = appointment.getFecha() != null ? appointment.getFecha().format(DATE_FMT) : "";
        String time = appointment.getHoraInicio() != null ? appointment.getHoraInicio().format(TIME_FMT) : "";

        String customerName = appointment.getCustomer() != null
                ? safeFullName(appointment.getCustomer().getNombres(), appointment.getCustomer().getApellidos())
                : "Cliente";

        if (appointment.getUser() != null) {
            Notification n = saveUserNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    appointment.getUser(),
                    NotificationType.BOOKING_CANCELLED_BY_CLIENT,
                    "Reserva cancelada",
                    customerName + " canceló su reserva de " + serviceName + " del " + date + " a las " + time + ".",
                    "APPOINTMENT",
                    appointment.getId()
            );
            registerDefaultChannels(n, false);
        }

        notifyOwnersAndAdminsAppointmentEvent(
                appointment,
                NotificationType.BOOKING_CANCELLED_BY_CLIENT,
                "Reserva cancelada por cliente",
                customerName + " canceló su reserva de " + serviceName + " del " + date + " a las " + time + "."
        );
    }

    @Override
    public void notifyBookingRescheduledByClient(
            Appointment appointment,
            java.time.LocalDate oldFecha,
            java.time.LocalTime oldHoraInicio,
            java.time.LocalTime oldHoraFin
    ) {
        if (appointment == null || appointment.getTenant() == null) return;

        String serviceName = appointment.getService() != null ? appointment.getService().getNombre() : "Servicio";

        String customerName = appointment.getCustomer() != null
                ? safeFullName(appointment.getCustomer().getNombres(), appointment.getCustomer().getApellidos())
                : "Cliente";

        String oldDate = oldFecha != null ? oldFecha.format(DATE_FMT) : "";
        String oldTime = oldHoraInicio != null ? oldHoraInicio.format(TIME_FMT) : "";
        String newDate = appointment.getFecha() != null ? appointment.getFecha().format(DATE_FMT) : "";
        String newTime = appointment.getHoraInicio() != null ? appointment.getHoraInicio().format(TIME_FMT) : "";

        String message = customerName + " reprogramó su reserva de " + serviceName
                + " del " + oldDate + " a las " + oldTime
                + " para el " + newDate + " a las " + newTime + ".";

        if (appointment.getUser() != null) {
            Notification n = saveUserNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    appointment.getUser(),
                    NotificationType.BOOKING_RESCHEDULED_BY_CLIENT,
                    "Reserva reprogramada",
                    message,
                    "APPOINTMENT",
                    appointment.getId()
            );
            registerDefaultChannels(n, false);
        }

        notifyOwnersAndAdminsAppointmentEvent(
                appointment,
                NotificationType.BOOKING_RESCHEDULED_BY_CLIENT,
                "Reserva reprogramada por cliente",
                message
        );
    }

    @Override
    public void notifyBookingReminder(Appointment appointment, NotificationType reminderType) {
        if (appointment.getCustomer() == null) return;

        if (notificationRepository.existsByTypeAndReferenceTypeAndReferenceId(
                reminderType, "APPOINTMENT", appointment.getId()
        )) {
            return;
        }

        String serviceName = appointment.getService() != null ? appointment.getService().getNombre() : "Servicio";
        String time = appointment.getHoraInicio() != null ? appointment.getHoraInicio().format(TIME_FMT) : "";

        String title = switch (reminderType) {
            case BOOKING_REMINDER_24H -> "Tu cita es manana";
            case BOOKING_REMINDER_60 -> "Tu cita es en 1 hora";
            default -> "Tu cita es en 30 minutos";
        };

        String message = switch (reminderType) {
            case BOOKING_REMINDER_24H ->
                    "Te recordamos tu reserva de " + serviceName + " para manana a las " + time + ".";
            case BOOKING_REMINDER_60 ->
                    "Te recordamos tu reserva de " + serviceName + " a las " + time + ".";
            default ->
                    "Tu reserva de " + serviceName + " es en 30 minutos. Te esperamos a las " + time + ".";
        };

        Notification n = saveCustomerNotification(
                appointment.getTenant(),
                appointment.getBranch(),
                appointment.getCustomer(),
                reminderType,
                title,
                message,
                "APPOINTMENT",
                appointment.getId()
        );

        registerDefaultChannels(n, shouldIncludeWhatsappForBookingReminder(appointment, reminderType));
    }

    @Override
    public void notifyPointsEarned(Customer customer, Integer points, Long saleId) {
        if (customer == null || points == null || points <= 0) return;

        Notification n = saveCustomerNotification(
                customer.getTenant(),
                null,
                customer,
                NotificationType.POINTS_EARNED,
                "Ganaste puntos",
                "Has ganado " + points + " puntos por tu visita.",
                "SALE",
                saleId
        );

        registerDefaultChannels(n, true);
    }

    @Override
    public void notifyPromotionCreated(Promotion promotion, boolean sendNotification) {
        if (!sendNotification || promotion == null || promotion.getTenant() == null) return;
        if (!promotion.isActivo()) return;

        Long tenantId = promotion.getTenant().getId();
        String title = safeText(promotion.getTitulo(), "Nueva promoción");
        String priceText = safeText(promotion.getPriceText(), null);

        String message = priceText == null
                ? "Nueva promoción disponible: " + title + "."
                : "Nueva promoción disponible: " + title + " - " + priceText + ".";

        broadcastToTenantCustomers(
                promotion.getTenant(),
                promotion.getBranch(),
                NotificationType.PROMOTION_CREATED,
                "Nueva promoción",
                message,
                "PROMOTION",
                promotion.getId()
        );
    }

    @Override
    public void notifyRewardCreated(RewardItem reward, boolean sendNotification) {
        if (!sendNotification || reward == null || reward.getTenant() == null) return;
        if (Boolean.FALSE.equals(reward.getActivo())) return;

        String rewardName = safeText(reward.getNombre(), "Premio");
        String pointsText = reward.getPuntosRequeridos() != null && reward.getPuntosRequeridos() > 0
                ? " por " + reward.getPuntosRequeridos() + " puntos"
                : "";

        broadcastToTenantCustomers(
                reward.getTenant(),
                null,
                NotificationType.REWARD_CREATED,
                "Nuevo premio disponible",
                "Nuevo premio: " + rewardName + pointsText + ".",
                "REWARD",
                reward.getId()
        );
    }

    @Override
    public void notifyRewardRedeemed(RewardRedemption redemption, Customer customer, RewardItem reward) {
        if (customer == null) return;

        String rewardName = reward != null ? reward.getNombre() : "Premio";

        Notification n = saveCustomerNotification(
                customer.getTenant(),
                null,
                customer,
                NotificationType.REWARD_REDEEMED,
                "Premio canjeado",
                "Tu canje fue generado correctamente: " + rewardName + ".",
                "REWARD_REDEMPTION",
                redemption != null ? redemption.getId() : null
        );

        registerDefaultChannels(n, true);
    }

    @Override
    public void notifyBarberPaymentCreated(BarberPayment payment) {
        if (payment == null || payment.getBarberUser() == null) return;

        Notification n = saveUserNotification(
                payment.getTenant(),
                payment.getBranch(),
                payment.getBarberUser(),
                NotificationType.BARBER_PAYMENT_CREATED,
                "Pago registrado",
                "Se registró un pago a tu favor por " + payment.getAmountPaid() + ".",
                "BARBER_PAYMENT",
                payment.getId()
        );

        registerDefaultChannels(n, true);
    }

    private void notifyBookingTeamCreated(
            Appointment appointment,
            String serviceName,
            String date,
            String time,
            boolean customerInitiated
    ) {
        if (appointment == null || appointment.getTenant() == null || appointment.getTenant().getId() == null) {
            return;
        }

        Long tenantId = appointment.getTenant().getId();
        Long branchId = appointment.getBranch() != null ? appointment.getBranch().getId() : null;
        String customerName = appointment.getCustomer() != null
                ? safeFullName(appointment.getCustomer().getNombres(), appointment.getCustomer().getApellidos())
                : "Cliente";
        String barberName = appointment.getUser() != null
                ? safeUserName(appointment.getUser())
                : "Sin profesional";

        Map<Long, AppUser> recipients = new LinkedHashMap<>();
        Set<Long> ownerIds = new HashSet<>();
        Set<Long> adminIds = new HashSet<>();
        Long professionalId = appointment.getUser() != null ? appointment.getUser().getId() : null;

        if (professionalId != null) {
            recipients.put(professionalId, appointment.getUser());
        }

        List<AppUser> owners = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(
                tenantId,
                null,
                RoleType.OWNER
        );
        for (AppUser owner : owners) {
            if (owner != null && owner.getId() != null) {
                recipients.put(owner.getId(), owner);
                ownerIds.add(owner.getId());
            }
        }

        List<AppUser> admins = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(
                tenantId,
                branchId,
                RoleType.ADMIN
        );
        for (AppUser admin : admins) {
            if (admin != null && admin.getId() != null) {
                recipients.put(admin.getId(), admin);
                adminIds.add(admin.getId());
            }
        }

        Map<String, Object> config = tenantSettingsRepository.findByTenant_Id(tenantId)
                .map(TenantSettings::getScheduleConfig)
                .orElse(Map.of());
        boolean alertEnabled = readBooleanConfig(
                config,
                OwnerWhatsappSettingsService.OWNER_BOOKING_ALERT_ENABLED_KEY,
                false
        );
        boolean includeAdmins = readBooleanConfig(
                config,
                OwnerWhatsappSettingsService.OWNER_BOOKING_ALERT_ADMINS_KEY,
                false
        );
        boolean includeProfessional = readBooleanConfig(
                config,
                OwnerWhatsappSettingsService.OWNER_BOOKING_ALERT_PROFESSIONAL_KEY,
                false
        );
        boolean includeStaffCreated = readBooleanConfig(
                config,
                OwnerWhatsappSettingsService.OWNER_BOOKING_ALERT_STAFF_CREATED_KEY,
                false
        );
        boolean whatsappEventEnabled = alertEnabled
                && ownerWhatsappPhoneVerificationService.isCentralReady()
                && (customerInitiated || includeStaffCreated);

        String ownerMessage = buildOwnerBookingMessage(
                appointment,
                serviceName,
                customerName,
                barberName,
                date,
                time,
                customerInitiated
        );
        String professionalMessage = customerName + " reservo " + serviceName
                + " para el " + date + " a las " + time + ".";

        for (AppUser recipient : recipients.values()) {
            if (recipient == null || recipient.getId() == null) continue;
            if (notificationRepository.existsByTenant_IdAndTypeAndReferenceTypeAndReferenceIdAndUser_Id(
                    tenantId,
                    NotificationType.BOOKING_CREATED,
                    "APPOINTMENT",
                    appointment.getId(),
                    recipient.getId()
            )) {
                continue;
            }

            boolean isOwner = ownerIds.contains(recipient.getId());
            boolean isAdmin = adminIds.contains(recipient.getId());
            boolean isProfessional = professionalId != null && professionalId.equals(recipient.getId());
            boolean managementRecipient = isOwner || isAdmin;

            Notification notification = saveUserNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    recipient,
                    NotificationType.BOOKING_CREATED,
                    managementRecipient ? "Nueva reserva de cliente" : "Nueva reserva",
                    managementRecipient ? ownerMessage : professionalMessage,
                    "APPOINTMENT",
                    appointment.getId()
            );

            boolean includeWhatsapp = whatsappEventEnabled
                    && ownerWhatsappPhoneVerificationService.isVerifiedRecipient(recipient)
                    && (
                    isOwner
                            || (isAdmin && includeAdmins)
                            || (isProfessional && includeProfessional)
            );
            registerDefaultChannels(notification, includeWhatsapp);
        }
    }


    private void notifyOwnersAndAdminsAppointmentEvent(
            Appointment appointment,
            NotificationType type,
            String title,
            String message
    ) {
        if (appointment == null || appointment.getTenant() == null) return;

        Long tenantId = appointment.getTenant().getId();
        Long branchId = appointment.getBranch() != null ? appointment.getBranch().getId() : null;

        Map<Long, AppUser> recipients = new LinkedHashMap<>();

        List<AppUser> owners = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(
                tenantId,
                null,
                RoleType.OWNER
        );

        for (AppUser owner : owners) {
            if (owner != null && owner.getId() != null) {
                recipients.put(owner.getId(), owner);
            }
        }

        List<AppUser> admins = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(
                tenantId,
                branchId,
                RoleType.ADMIN
        );

        for (AppUser admin : admins) {
            if (admin != null && admin.getId() != null) {
                recipients.put(admin.getId(), admin);
            }
        }

        for (AppUser recipient : recipients.values()) {
            Notification n = saveUserNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    recipient,
                    type,
                    title,
                    message,
                    "APPOINTMENT",
                    appointment.getId()
            );

            registerDefaultChannels(n, false);
        }
    }

    private void broadcastToTenantCustomers(
            Tenant tenant,
            Branch branch,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ) {
        if (tenant == null || tenant.getId() == null) return;

        List<Customer> customers = customerRepository.findActiveNotificationTargetsByTenant(tenant.getId());

        for (Customer customer : customers) {
            if (customer == null || customer.getId() == null) continue;

            Notification n = saveCustomerNotification(
                    tenant,
                    branch,
                    customer,
                    type,
                    title,
                    message,
                    referenceType,
                    referenceId
            );

            // Para campañas masivas usamos IN_APP + PUSH.
            // No activamos WHATSAPP automáticamente para evitar costos/envíos masivos no deseados.
            registerDefaultChannels(n, false);
        }
    }

    private Notification saveCustomerNotification(
            Tenant tenant,
            Branch branch,
            Customer customer,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ) {
        Notification n = Notification.builder()
                .tenant(tenant)
                .branch(branch)
                .customer(customer)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        return notificationRepository.save(n);
    }

    private Notification saveUserNotification(
            Tenant tenant,
            Branch branch,
            AppUser user,
            NotificationType type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ) {
        Notification n = Notification.builder()
                .tenant(tenant)
                .branch(branch)
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        return notificationRepository.save(n);
    }

    private void registerDefaultChannels(Notification notification, boolean includeWhatsapp) {
        createDelivery(notification, NotificationChannel.IN_APP, NotificationDeliveryStatus.SENT);
        createDelivery(notification, NotificationChannel.PUSH, NotificationDeliveryStatus.PENDING);

        if (includeWhatsapp) {
            createDelivery(notification, NotificationChannel.WHATSAPP, NotificationDeliveryStatus.PENDING);
        }
    }

    private void createDelivery(
            Notification notification,
            NotificationChannel channel,
            NotificationDeliveryStatus status
    ) {
        NotificationDelivery delivery = NotificationDelivery.builder()
                .notification(notification)
                .channel(channel)
                .status(status)
                .attempts(0)
                .build();

        notificationDeliveryRepository.save(delivery);
    }

    private boolean shouldIncludeWhatsappForBookingReminder(
            Appointment appointment,
            NotificationType reminderType
    ) {
        if (appointment == null || appointment.getTenant() == null || appointment.getTenant().getId() == null) {
            return true;
        }

        if (reminderType != NotificationType.BOOKING_REMINDER_24H
                && reminderType != NotificationType.BOOKING_REMINDER_60
                && reminderType != NotificationType.BOOKING_REMINDER_30) {
            return true;
        }

        String key = reminderType == NotificationType.BOOKING_REMINDER_24H
                ? OwnerWhatsappSettingsService.REMINDER_24H_ENABLED_KEY
                : OwnerWhatsappSettingsService.REMINDER_60_ENABLED_KEY;
        boolean fallback = reminderType != NotificationType.BOOKING_REMINDER_24H;

        return tenantSettingsRepository.findByTenant_Id(appointment.getTenant().getId())
                .map(settings -> readBooleanConfig(
                        settings.getScheduleConfig(),
                        key,
                        fallback
                ))
                .orElse(fallback);
    }

    private String buildOwnerBookingMessage(
            Appointment appointment,
            String serviceName,
            String customerName,
            String professionalName,
            String date,
            String time,
            boolean customerInitiated
    ) {
        String customerPhone = appointment.getCustomer() != null
                ? safeText(appointment.getCustomer().getTelefono(), "No registrado")
                : "No registrado";
        String tenantName = safeText(appointment.getTenant().getNombre(), "Negocio");
        String branchName = appointment.getBranch() != null
                ? safeText(appointment.getBranch().getNombre(), "Sede")
                : "Sede";
        String endTime = appointment.getHoraFin() != null
                ? " - " + appointment.getHoraFin().format(TIME_FMT)
                : "";
        String status = safeText(appointment.getEstado(), "RESERVADO");
        String depositStatus = ownerDepositStatus(appointment);
        String total = appointment.getTotalAmount() != null
                ? appointment.getTotalAmount().stripTrailingZeros().toPlainString()
                : "No informado";
        String contactLink = customerWhatsappLink(customerPhone, appointment.getTenant());
        String agendaUrl = "https://www.supergodsapp.com/owner/agenda"
                + "?appointmentId=" + appointment.getId()
                + "&branchId=" + (appointment.getBranch() == null ? "" : appointment.getBranch().getId())
                + "&date=" + (appointment.getFecha() == null ? "" : appointment.getFecha());
        StringBuilder message = new StringBuilder()

                .append("Nueva reserva #").append(appointment.getId()).append('\n')
                .append("Cliente: ").append(customerName).append('\n')
                .append("WhatsApp: ").append(customerPhone).append('\n')
                .append("Negocio: ").append(tenantName).append('\n')
                .append("Sede: ").append(branchName).append('\n')
                .append("Servicio: ").append(serviceName).append('\n')
                .append("Profesional: ").append(professionalName).append('\n')
                .append("Fecha: ").append(date).append('\n')
                .append("Horario: ").append(time).append(endTime).append('\n')
                .append("Estado: ").append(status).append('\n')
                .append("Adelanto: ").append(depositStatus).append('\n')
                .append("Total: ").append(total).append('\n')
                .append("Origen: ").append(customerInitiated ? "Cliente" : "Equipo").append('\n')
                .append("Agenda: ").append(agendaUrl);

        if (contactLink != null) {
            message.append('\n').append("Contactar: ").append(contactLink);
        }

        return limitText(message.toString(), 500);
    }

    private String ownerDepositStatus(Appointment appointment) {
        if (appointment == null || !Boolean.TRUE.equals(appointment.getDepositRequired())) {
            return "No requerido";
        }

        String amount = appointment.getDepositAmount() != null
                ? appointment.getDepositAmount().stripTrailingZeros().toPlainString()
                : "0";
        String status = safeText(appointment.getDepositStatus(), "PENDING_VALIDATION")
                .toUpperCase(Locale.ROOT);

        return switch (status) {
            case "PAID", "VALIDATED", "VALIDADO" -> "Validado - " + amount;
            case "REJECTED", "RECHAZADO" -> "Rechazado - " + amount;
            default -> "Pendiente de validar - " + amount;
        };
    }

    private String customerWhatsappLink(String rawPhone, Tenant tenant) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }
        if (digits.isBlank() || "No registrado".equalsIgnoreCase(rawPhone)) {
            return null;
        }

        if (digits.length() < 11) {
            String country = tenant == null ? "" : safeText(tenant.getPais(), "").toUpperCase(Locale.ROOT);
            String prefix = switch (country) {
                case "PE", "PERU", "PERÚ" -> "51";
                case "CO", "COLOMBIA" -> "57";
                case "MX", "MEXICO", "MÉXICO" -> "52";
                case "CL", "CHILE" -> "56";
                case "AR", "ARGENTINA" -> "54";
                case "BO", "BOLIVIA" -> "591";
                default -> "";
            };
            if (!prefix.isBlank() && !digits.startsWith(prefix)) {
                digits = prefix + digits;
            }
        }

        return "https://wa.me/" + digits;
    }

    private String limitText(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
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

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isBlank()) return fallback;
        return value.trim();
    }

    private String safeFullName(String nombres, String apellidos) {
        String full = ((nombres == null ? "" : nombres.trim()) + " " +
                (apellidos == null ? "" : apellidos.trim())).trim();
        return full.isBlank() ? "Cliente" : full;
    }

    private String safeUserName(AppUser user) {
        if (user == null) return "Usuario";

        String full = ((user.getNombre() == null ? "" : user.getNombre().trim()) + " " +
                (user.getApellido() == null ? "" : user.getApellido().trim())).trim();

        return full.isBlank() ? "Usuario" : full;
    }

    @Override
    public void notifySaleReceipt(Sale sale, String message, boolean notifyOwnersPendingWhatsapp) {
        if (sale == null || sale.getTenant() == null || sale.getCustomer() == null) {
            return;
        }

        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Notification n = saveCustomerNotification(
                sale.getTenant(),
                sale.getBranch(),
                sale.getCustomer(),
                NotificationType.SALE_RECEIPT,
                "Comprobante de venta",
                message,
                "SALE",
                sale.getId()
        );

        registerDefaultChannels(n, true);
        if (notifyOwnersPendingWhatsapp) {
            notifyOwnersSaleReceiptPending(sale);
        }
    }

    private void notifyOwnersSaleReceiptPending(Sale sale) {
        if (sale == null || sale.getTenant() == null || sale.getTenant().getId() == null) {
            return;
        }

        String customerName = sale.getCustomer() != null
                ? safeFullName(sale.getCustomer().getNombres(), sale.getCustomer().getApellidos())
                : "Cliente";

        List<AppUser> owners = userTenantRoleRepository.findActiveUsersByTenantBranchAndRole(
                sale.getTenant().getId(),
                null,
                RoleType.OWNER
        );

        for (AppUser owner : owners) {
            if (owner == null || owner.getId() == null) {
                continue;
            }

            Notification ownerNotification = saveUserNotification(
                    sale.getTenant(),
                    sale.getBranch(),
                    owner,
                    NotificationType.SALE_RECEIPT,
                    "WhatsApp post-venta pendiente",
                    "Hay un mensaje post-venta pendiente para " + customerName + ".",
                    "SALE",
                    sale.getId()
            );

            registerDefaultChannels(ownerNotification, false);
        }
    }
}
