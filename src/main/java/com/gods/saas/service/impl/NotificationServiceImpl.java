package com.gods.saas.service.impl;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.enums.NotificationDeliveryStatus;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.NotificationDeliveryRepository;
import com.gods.saas.domain.repository.NotificationRepository;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.impl.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void notifyBookingCreated(Appointment appointment) {
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

        if (appointment.getUser() != null) {
            String customerName = appointment.getCustomer() != null
                    ? safeFullName(appointment.getCustomer().getNombres(), appointment.getCustomer().getApellidos())
                    : "Cliente";

            Notification n = saveUserNotification(
                    appointment.getTenant(),
                    appointment.getBranch(),
                    appointment.getUser(),
                    NotificationType.BOOKING_CREATED,
                    "Nueva reserva",
                    customerName + " reservó " + serviceName + " para el " + date + " a las " + time + ".",
                    "APPOINTMENT",
                    appointment.getId()
            );
            registerDefaultChannels(n, false);
        }

        notifyOwnersAndAdminsBookingCreated(appointment, serviceName, date, time);
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

        String title = reminderType == NotificationType.BOOKING_REMINDER_60
                ? "Tu cita es en 1 hora"
                : "Tu cita es en 30 minutos";

        String message = reminderType == NotificationType.BOOKING_REMINDER_60
                ? "Te recordamos tu reserva de " + serviceName + " a las " + time + "."
                : "Tu reserva de " + serviceName + " es en 30 minutos. Te esperamos a las " + time + ".";

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

        registerDefaultChannels(n, true);
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
        if (!sendNotification) return;
        // Aquí luego haces broadcast a clientes del tenant
        // Por ahora dejamos el método preparado para integrar con CustomerRepository.
    }

    @Override
    public void notifyRewardCreated(RewardItem reward, boolean sendNotification) {
        if (!sendNotification) return;
        // Igual que promociones: broadcast masivo en la siguiente capa.
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

    private void notifyOwnersAndAdminsBookingCreated(
            Appointment appointment,
            String serviceName,
            String date,
            String time
    ) {
        if (appointment == null || appointment.getTenant() == null) return;

        Long tenantId = appointment.getTenant().getId();
        Long branchId = appointment.getBranch() != null ? appointment.getBranch().getId() : null;

        String customerName = appointment.getCustomer() != null
                ? safeFullName(appointment.getCustomer().getNombres(), appointment.getCustomer().getApellidos())
                : "Cliente";

        String barberName = appointment.getUser() != null
                ? safeUserName(appointment.getUser())
                : "Sin barbero";

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
                    NotificationType.BOOKING_CREATED,
                    "Nueva cita agendada",
                    "Se agendó una cita para " + customerName + " con " + barberName + " el " + date + " a las " + time + ".",
                    "APPOINTMENT",
                    appointment.getId()
            );

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
}