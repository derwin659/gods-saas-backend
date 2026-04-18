package com.gods.saas.domain.repository;

import com.gods.saas.domain.enums.NotificationChannel;
import com.gods.saas.domain.model.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByNotification_IdAndChannel(Long notificationId, NotificationChannel channel);
}