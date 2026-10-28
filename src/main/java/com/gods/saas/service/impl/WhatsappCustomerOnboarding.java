package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Called only after signature validation and the assistant's per-phone session lock. */
@Service
@RequiredArgsConstructor
public class WhatsappCustomerOnboarding {
    private final CustomerRepository customers;
    private final TenantRepository tenants;
    private final SubscriptionService subscriptions;
    private final WhatsappAssistantPolicy policy;
    private final WhatsappAssistantUsageService usage;
    private final TenantTimeService time;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${whatsapp.assistant.tenant-ids:}") private String tenantIds;
    @Value("${whatsapp.twilio.validate-inbound-signature:true}") private boolean signedInbound;

    private String clean(String value) {
        return java.text.Normalizer.normalize(Objects.toString(value,""),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }
    private boolean eligible(Tenant tenant) {
        if (tenant==null || !Boolean.TRUE.equals(tenant.getActive()) || !allowed().contains(tenant.getId())) return false;
        try {
            subscriptions.validateSubscriptionActive(tenant.getId());
            return policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(tenant.getId()).getPlan())>0;
        } catch (RuntimeException failure) { return false; }
    }
    private Set<Long> allowed() {
        return Arrays.stream(Objects.toString(tenantIds,"").split(",")).map(String::trim)
                .filter(v->v.matches("[0-9]{1,18}")).map(Long::valueOf).collect(java.util.stream.Collectors.toSet());
    }
    private List<Customer> existing(Long tenant,String phone) throws Exception {
        var util=PhoneNumberUtil.getInstance();
        var number=util.parse(phone,null);
        return customers.findPhoneCandidates(tenant,phone,phone.substring(1),util.getNationalSignificantNumber(number),
                util.format(number,PhoneNumberUtil.PhoneNumberFormat.NATIONAL).replaceAll("[^0-9]",""));
    }
    private String prompt(String phone,ObjectNode meta,Instant now,String text) throws Exception {
        meta.put("lastPrompt",text);
        ObjectNode root=mapper.createObjectNode(); root.set("_assistant",meta);
        jdbc.update("update whatsapp_assistant_session set tenant_id=null,draft=?,confirmation_code=null,quote=null,expires_at=? where phone=?",
                mapper.writeValueAsString(root),Timestamp.from(now.plusSeconds(1800)),phone);
        return text;
    }
    private String activate(String phone,Tenant tenant,Instant now,boolean reserve) throws Exception {
        if (reserve && !usage.reserve(tenant.getId(),YearMonth.from(time.today(tenant.getId())).toString(),
                policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(tenant.getId()).getPlan())))
            return "Este negocio alcanzo su limite mensual del asistente. Contacta al negocio para reservar.";
        String menu=WhatsappAssistantMenu.TEXT;
        ObjectNode root=mapper.createObjectNode(); root.set("_assistant",mapper.createObjectNode().put("stage","MENU").put("mode","BOOKING").put("lastPrompt",menu));
        jdbc.update("update whatsapp_assistant_session set tenant_id=?,draft=?,confirmation_code=null,quote=null,expires_at=? where phone=?",
                tenant.getId(),mapper.writeValueAsString(root),Timestamp.from(now.plusSeconds(1800)),phone);
        return "Estas en "+tenant.getNombre()+".\n"+menu;
    }
    private String enter(String phone,Tenant tenant,Instant now) throws Exception {
        if (!eligible(tenant)) return "No encontre esa barberia habilitada. Revisa su nombre o codigo con el negocio.";
        List<Customer> matches=existing(tenant.getId(),phone);
        if (!matches.isEmpty()) {
            if (matches.size()!=1) return blocked(phone,now);
            Customer client=matches.get(0);
            if (!phone.equals(client.getTelefono()) || !client.isPhoneVerified() || Boolean.FALSE.equals(client.getActivo())
                    || client.getWhatsappOptedOutAt()!=null || !Boolean.TRUE.equals(client.getWhatsappTransactionalEnabled())) return blocked(phone,now);
            return activate(phone,tenant,now,true);
        }
        return prompt(phone,mapper.createObjectNode().put("stage","REGISTER_NAME").put("tenant",tenant.getId()),now,
                "Estas en "+tenant.getNombre()+". Para reservar crearemos tu ficha de cliente. ¿Cual es tu nombre? Escribe SALIR para cancelar.");
    }
    private String blocked(String phone,Instant now) throws Exception {
        return prompt(phone,mapper.createObjectNode(),now,"Este numero ya tiene una ficha en el negocio. Verifica tu cuenta y permisos de WhatsApp en la app o consulta al negocio. No crearemos una ficha duplicada.");
    }
    @Transactional(rollbackFor=Exception.class)
    public String reply(String phone,String text,ObjectNode root,Instant now) throws Exception {
        ObjectNode meta=root.path("_assistant") instanceof ObjectNode node?node.deepCopy():mapper.createObjectNode();
        String stage=meta.path("stage").asText();
        String input=clean(text);
        if (stage.startsWith("REGISTER_")) {
            if (!signedInbound) return "El registro por WhatsApp requiere validar la firma de Twilio. Contacta al negocio o registrate en la app.";
            Tenant tenant=tenants.findById(meta.path("tenant").asLong()).orElse(null);
            if (!eligible(tenant)) return prompt(phone,mapper.createObjectNode(),now,"Esta barberia ya no esta habilitada. Escribe otro nombre o codigo.");
            if (Set.of("no","no acepto","cancelar").contains(input)) return prompt(phone,mapper.createObjectNode(),now,"Registro cancelado. No creamos ninguna ficha. Escribe otra barberia para empezar de nuevo.");
            if (Set.of("hola","menu","ayuda").contains(input)) return meta.path("lastPrompt").asText();
            if (stage.equals("REGISTER_NAME")) {
                String name=text.trim().replaceAll("\\s+"," ");
                if (name.length()<2 || name.length()>120 || !name.matches("[\\p{L}\\p{M} .'-]+")) return "Escribe tu nombre con letras (entre 2 y 120 caracteres), o SALIR para cancelar.";
                meta.put("name",name).put("stage","REGISTER_CONSENT");
                return prompt(phone,meta,now,"Gracias, "+name+". ¿Aceptas que "+tenant.getNombre()+" registre tu nombre y este numero para gestionar tus reservas y enviarte avisos por WhatsApp? No incluye publicidad. Responde ACEPTO o NO.");
            }
            if (!Set.of("acepto","si acepto").contains(input)) return meta.path("lastPrompt").asText();
            // Per-phone session lock prevents duplicate deliveries; tenant lock serializes registrations.
            int limit=policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(tenant.getId()).getPlan());
            if (!usage.reserve(tenant.getId(),YearMonth.from(time.today(tenant.getId())).toString(),limit))
                return "Este negocio alcanzo su limite mensual del asistente. No creamos tu ficha; contacta al negocio para reservar.";
            jdbc.queryForList("select tenant_id from tenant where tenant_id=? for update",tenant.getId());
            if (!existing(tenant.getId(),phone).isEmpty()) return enter(phone,tenant,now);
            LocalDateTime localNow=LocalDateTime.ofInstant(now,ZoneOffset.UTC);
            customers.saveAndFlush(Customer.builder().tenant(tenant).telefono(phone).nombres(meta.path("name").asText())
                    .phoneVerified(true).origenCliente("WHATSAPP").source("WHATSAPP").preferredContactChannel("WHATSAPP")
                    .fechaRegistro(localNow).fechaActualizacion(localNow).puntosDisponibles(0)
                    .whatsappTransactionalEnabled(true).whatsappMarketingEnabled(false)
                    .customerNotes("ACEPTO registro y avisos transaccionales por WhatsApp: "+now+". Control del numero acreditado mediante webhook Twilio firmado.").build());
            return "¡Listo! Tu ficha de cliente fue creada.\n"+activate(phone,tenant,now,false);
        }
        if (stage.equals("DISCOVER") && input.matches("[0-9]{1,2}")) {
            int index=Integer.parseInt(input)-1;
            JsonNode options=meta.path("options");
            if (index>=0 && index<options.size()) return enter(phone,tenants.findById(options.get(index).path("id").asLong()).orElse(null),now);
            return meta.path("lastPrompt").asText();
        }
        String query=input.replaceFirst("^(quiero reservar en|quiero una cita en|reservar en|barberia de codigo|codigo|gods) +","").trim();
        if (query.length()<2) return prompt(phone,mapper.createObjectNode(),now,"¿En que barberia quieres reservar? Escribe su nombre o codigo, por ejemplo GODS INDYSTRY.");
        List<Tenant> available=tenants.findAllById(allowed()).stream().filter(this::eligible).toList();
        List<Tenant> exact=available.stream().filter(t->clean(t.getCodigo()).equals(query)||clean(t.getNombre()).equals(query)).toList();
        List<Tenant> matches=exact.isEmpty()?available.stream().filter(t->clean(t.getNombre()).contains(query)).limit(21).toList():exact;
        if (matches.size()==1) return enter(phone,matches.get(0),now);
        if (matches.isEmpty()) return prompt(phone,mapper.createObjectNode(),now,"No encontre una barberia habilitada con ese nombre o codigo. Revisa el codigo con el negocio o envia el mensaje de su enlace/QR.");
        if (matches.size()>20) return prompt(phone,mapper.createObjectNode(),now,"Hay muchas coincidencias. Escribe un nombre mas completo o el codigo de la barberia.");
        ObjectNode next=mapper.createObjectNode().put("stage","DISCOVER"); var options=next.putArray("options");
        StringBuilder message=new StringBuilder("Encontre varias barberias. ¿Cual eliges? Responde con su numero o codigo.");
        for (Tenant tenant:matches) {
            options.addObject().put("id",tenant.getId());
            message.append("\n").append(options.size()).append(". ").append(tenant.getNombre()).append(" (").append(tenant.getCodigo()).append(")");
        }
        return prompt(phone,next,now,message.toString());
    }
}
