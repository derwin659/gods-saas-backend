package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.*;

public interface NotificationService {

    void notifyBookingCreated(Appointment appointment);

    void notifyBookingReminder(Appointment appointment, NotificationType reminderType);

    void notifyPointsEarned(Customer customer, Integer points, Long saleId);

    void notifyPromotionCreated(Promotion promotion, boolean sendNotification);

    void notifyRewardCreated(RewardItem reward, boolean sendNotification);

    void notifyRewardRedeemed(RewardRedemption redemption, Customer customer, RewardItem reward);

    void notifyBarberPaymentCreated(BarberPayment payment);
}