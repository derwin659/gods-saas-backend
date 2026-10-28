package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostSaleWhatsappAssistantInvitation {
    private final WhatsappAssistantService assistant;

    // A failed optional lookup must not roll back the sale transaction.
    @Transactional(readOnly=true, propagation=Propagation.REQUIRES_NEW)
    public String build(Long tenantId) {
        if (tenantId==null) return "";
        var status=assistant.status(tenantId);
        if (!Boolean.TRUE.equals(status.get("enabled"))) return "";
        String code=Objects.toString(status.get("businessCode"),"").trim();
        String url=Objects.toString(status.get("bookingWhatsappUrl"),"").trim();
        if (code.isBlank() || url.isBlank()) return "";
        return "\n\nTambien puedes reservar por WhatsApp con nuestro asistente. Abre este enlace y envia: GODS "+code+"\n"+url;
    }
}
