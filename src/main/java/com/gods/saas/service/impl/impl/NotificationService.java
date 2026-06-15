package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;

import java.time.LocalDate;
import java.time.LocalTime;

public interface NotificationService {

    void notifyBookingCreated(Appointment appointment);

    void notifyBookingCancelledByClient(Appointment appointment);

    void notifyBookingRescheduledByClient(
            Appointment appointment,
            LocalDate oldFecha,
            LocalTime oldHoraInicio,
            LocalTime oldHoraFin
    );

    void notifyBookingReminder(Appointment appointment, NotificationType reminderType);

    void notifyPointsEarned(Customer customer, Integer points, Long saleId);

    void notifySaleReceipt(Sale sale, String message, boolean notifyOwnersPendingWhatsapp);

    void notifyPromotionCreated(Promotion promotion, boolean sendNotification);

    void notifyRewardCreated(RewardItem reward, boolean sendNotification);

    void notifyRewardRedeemed(RewardRedemption redemption, Customer customer, RewardItem reward);

    void notifyBarberPaymentCreated(BarberPayment payment);
}
