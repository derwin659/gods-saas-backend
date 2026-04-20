package com.gods.saas.service.impl.impl;

public interface NotificationDispatchService {
    void processPendingPush();
    void processPendingWhatsapp();
}