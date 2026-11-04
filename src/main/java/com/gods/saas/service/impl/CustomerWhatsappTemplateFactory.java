package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.enums.NotificationType;
import com.gods.saas.domain.model.Notification;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerWhatsappTemplateFactory {
    private final AppointmentRepository appointments;
    private final TransactionalWhatsappPolicy policy;
    private final ObjectMapper mapper;

    public record Payload(String contentSid, String variables) {}
    public Optional<Payload> from(Notification n, WhatsappBusinessSenderService.Connection own) {
        if (n.getCustomer() == null || (n.getType() != NotificationType.SALE_RECEIPT && n.getType() != NotificationType.BOOKING_REMINDER_60)) return Optional.empty();
        // These business-initiated sends always need an approved template, even outside the 24-hour window.
        if (own == null || !Objects.equals(own.tenantId(), n.getTenant().getId())) throw new IllegalStateException("Conecta el numero del negocio para activar estos mensajes automaticos.");
        if (!policy.current(n, true)) throw new IllegalStateException("La venta o cita ya no corresponde a este envio.");
        Map<String,String> vars = new LinkedHashMap<>();
        vars.put("1", text(n.getCustomer().getNombres(), "Cliente"));
        vars.put("2", text(n.getTenant().getNombre(), "Negocio"));
        String sid;
        if (n.getType() == NotificationType.SALE_RECEIPT) {
            var snapshot = PostSaleWhatsappMessageFactory.restore(n.getWhatsappTemplateSnapshot());
            vars.clear();
            vars.putAll(snapshot.variables());
            sid = own.saleReceiptContentSids().get(snapshot.variant());
            if (sid == null && PostSaleWhatsappMessageFactory.FULL_VARIANT.equals(snapshot.variant())) sid = own.saleReceiptContentSid();
            if (sid == null || sid.isBlank()) throw new IllegalStateException("Falta la plantilla postventa aprobada para " + snapshot.variant());
        } else {
            var a = appointments.findByIdAndTenant_Id(n.getReferenceId(), n.getTenant().getId()).orElseThrow();
            sid = own.reminder60ContentSid();
            vars.put("3", text(a.getService() == null ? null : a.getService().getNombre(), "Servicio"));
            vars.put("4", a.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            vars.put("5", a.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (sid == null || !sid.matches("HX[0-9a-fA-F]{32}")) throw new IllegalStateException("Falta la plantilla WhatsApp aprobada en la subcuenta de este negocio para " + n.getType());
        try { return Optional.of(new Payload(sid, mapper.writeValueAsString(vars))); }
        catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo construir la plantilla WhatsApp."); }
    }
    private String text(String raw,String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String value = raw.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.substring(0, Math.min(value.length(), 150));
    }
}
