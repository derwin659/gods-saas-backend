package com.gods.saas.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.domain.model.TenantSettings;
import com.gods.saas.domain.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

/** Approved connections are provisioned only in server secrets, never in an owner's settings JSON. */
@Service
@RequiredArgsConstructor
public class WhatsappBusinessSenderService {
    public static final String MODE_KEY = "whatsappSenderMode";
    public static final String PHONE_KEY = "whatsappBusinessPhone";
    private final TenantSettingsRepository settings;
    private final WhatsappBusinessConnectionStore automaticConnections;
    private final ObjectMapper mapper=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    @Value("${whatsapp.twilio.business-connections:}") private String businessConnections;
    @Value("${whatsapp.twilio.from-number:}") private String sharedPhone;
    @Value("${whatsapp.twilio.account-sid:}") private String sharedAccountSid;
    @Value("${whatsapp.twilio.enabled:false}") private boolean enabled;
    @Value("${whatsapp.twilio.validate-inbound-signature:true}") private boolean validateSignature=true;

    public record Connection(Long tenantId,String phone,String accountSid,String authToken,
                             String bookingContentSid,String depositContentSid) {
        @Override public String toString() { return "WhatsApp connection [redacted]"; }
    }
    public record Selection(String senderMode,String businessPhone,String businessConnectionStatus,
                            String effectiveSenderMode,String effectiveSenderPhone) {
        public boolean businessConnected() { return "CONNECTED".equals(businessConnectionStatus); }
    }
    public Selection selection(Long tenantId) {
        return selection(tenantId,settings.findByTenant_Id(tenantId).map(TenantSettings::getScheduleConfig).orElse(Map.of()));
    }
    public Selection selection(Long tenantId,Map<String,Object> config) {
        String mode="BUSINESS".equals(text(config,MODE_KEY)) ? "BUSINESS" : "SHARED";
        String phone=normalizePhone(text(config,PHONE_KEY));
        var connection=connection(tenantId).orElse(null);
        boolean connected=enabled && validateSignature && "BUSINESS".equals(mode) && phone!=null
                && connection!=null && phone.equals(connection.phone());
        return new Selection(mode,Objects.toString(phone,""),connected ? "CONNECTED" : "BUSINESS".equals(mode) ? "PENDING" : "NOT_REQUESTED",
                connected ? "BUSINESS" : "SHARED",Objects.toString(connected ? phone : normalizePhone(sharedPhone),""));
    }
    public Optional<Connection> inbound(String to) {
        String phone=normalizePhone(to);
        if (!enabled || !validateSignature || phone==null || isSharedDestination(phone)) return Optional.empty();
        return connections().values().stream().filter(c -> c.phone().equals(phone))
                .filter(c -> selection(c.tenantId()).businessConnected()).findFirst();
    }
    public Optional<Connection> connection(Long tenantId) {
        return tenantId==null ? Optional.empty() : Optional.ofNullable(connections().get(tenantId));
    }
    public Optional<Connection> forOrigin(Long tenantId,String sender) {
        String phone=normalizePhone(sender);
        return connection(tenantId).filter(c -> enabled && c.phone().equals(phone));
    }
    public boolean isSharedDestination(String phone) {
        String normalized=normalizePhone(phone);
        return normalized!=null && normalized.equals(normalizePhone(sharedPhone));
    }
    public static String normalizePhone(String raw) {
        if (raw==null) return null;
        String phone=raw.trim().replaceFirst("^whatsapp:","").replaceAll("[\\s()\\-]","");
        return phone.matches("\\+[1-9][0-9]{7,14}") ? phone : null;
    }
    private Map<Long,Connection> connections() {
        Map<Long,Connection> result=new LinkedHashMap<>(manualConnections());
        for(var connection:automaticConnections.active()) {
            if(result.containsKey(connection.tenantId()))continue;
            if(result.values().stream().anyMatch(existing -> existing.phone().equals(connection.phone()) || existing.accountSid().equals(connection.accountSid())))return Map.of();
            result.put(connection.tenantId(),connection);
        }
        return result;
    }
    private Map<Long,Connection> manualConnections() {
        if (businessConnections==null || businessConnections.isBlank()) return Map.of();
        try {
            var root=mapper.readTree(businessConnections);
            if (!root.isObject()) return Map.of();
            Map<Long,Connection> result=new LinkedHashMap<>();
            Set<String> phones=new HashSet<>(), accounts=new HashSet<>();
            var fields=root.fields();
            while (fields.hasNext()) {
                var entry=fields.next();
                if (!entry.getKey().matches("[1-9][0-9]{0,17}")) return Map.of();
                var node=entry.getValue();
                String phone=normalizePhone(node.path("phone").asText());
                String account=node.path("accountSid").asText(), token=node.path("authToken").asText();
                if (phone==null || isSharedDestination(phone) || !phones.add(phone)
                        || !account.matches("AC[0-9a-fA-F]{32}") || account.equals(sharedAccountSid) || !accounts.add(account)
                        || !token.matches("[0-9a-fA-F]{32}")) return Map.of();
                if (!"CONNECTED".equals(node.path("status").asText())) continue;
                Long tenant=Long.valueOf(entry.getKey());
                result.put(tenant,new Connection(tenant,phone,account,token,
                        node.path("bookingContentSid").asText(),node.path("depositContentSid").asText()));
            }
            return result;
        } catch (Exception invalid) { return Map.of(); }
    }
    private String text(Map<String,Object> config,String key) {
        return config==null ? "" : Objects.toString(config.get(key),"").trim();
    }
}
