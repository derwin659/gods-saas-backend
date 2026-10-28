package com.gods.saas.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Separate from visual AI credits. Limits count handled inbound turns, not bookings. */
@Service
public class WhatsappAssistantPolicy {
    @Value("${whatsapp.assistant.starter-turns:100}") private int starterTurns;
    @Value("${whatsapp.assistant.growth-turns:300}") private int growthTurns;
    @Value("${whatsapp.assistant.pro-turns:600}") private int proTurns;

    public int monthlyTurns(String plan) {
        if (plan == null) return 0;
        return Math.max(0, switch (plan.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "STARTER" -> starterTurns;
            case "GROWTH", "GODS_AI" -> growthTurns;
            case "PRO", "PRO_LEGACY", "ENTERPRISE" -> proTurns;
            default -> 0;
        });
    }
}
