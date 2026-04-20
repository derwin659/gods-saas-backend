package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.model.Notification;

public interface PushNotificationSender {
    String send(Notification notification);
}