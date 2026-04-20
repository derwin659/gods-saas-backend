package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.model.Notification;

public interface WhatsappNotificationSender {
    String send(Notification notification);
}