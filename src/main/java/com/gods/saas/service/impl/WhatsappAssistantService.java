package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.response.*;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappAssistantService {
    private static final Pattern PHONE = Pattern.compile("^whatsapp:\\+[1-9][0-9]{7,14}$");
    private static final Pattern SID = Pattern.compile("^SM[0-9a-fA-F]{32}$");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CustomerRepository customers;
    private final TenantRepository tenants;
    private final LoyaltyAccountRepository loyalty;
    private final PromotionRepository promotions;
    private final SubscriptionService subscriptions;
    private final ClientBookingService bookings;
    private final TenantTimeService time;
    private final WhatsappAssistantPolicy policy;
    private final WhatsappAssistantInterpreter interpreter;
    private final WhatsappAssistantUsageService usage;
    private final WhatsappDepositService deposits;
    private final WhatsappCustomerOnboarding onboarding;
    private final WhatsappCustomerActions actions;
    @Value("${whatsapp.assistant.enabled:false}") private boolean enabled;
    @Value("${whatsapp.assistant.tenant-ids:}") private String tenantIds;
    @Value("${whatsapp.twilio.from-number:}") private String fromNumber;

    @Transactional(rollbackFor = Exception.class)
    public String reply(String from, String to, String body, String messageSid) throws Exception {
        return replyInternal(from,to,body,messageSid,Map.of());
    }
    @Transactional(rollbackFor = Exception.class)
    public String replyWithMedia(String from,String to,String body,String messageSid,Map<String,String> media) throws Exception {
        return replyInternal(from,to,body,messageSid,media);
    }
    private String replyInternal(String from,String to,String body,String messageSid,Map<String,String> media) throws Exception {
        if (media.containsKey("MediaUrl0") && (body==null || body.isBlank())) body="COMPROBANTE";
        if (!enabled) return skipped("disabled");
        if (!correctDestination(to)) return skipped("destination_mismatch");
        if (from == null || !PHONE.matcher(from).matches()) return skipped("invalid_sender");
        if (messageSid == null || !SID.matcher(messageSid).matches()
                || body == null || body.isBlank() || body.length()>1200) return skipped("invalid_message");
        String phone = from.substring("whatsapp:".length());
        // Duplicate webhook retries never execute a second action or emit a second reply.
        if (jdbc.update("insert into whatsapp_assistant_message(message_sid) values (?) on conflict do nothing", messageSid) == 0)
            return skipped("duplicate_message");
        jdbc.update("insert into whatsapp_assistant_session(phone) values (?) on conflict do nothing", phone);
        Map<String, Object> session = jdbc.queryForMap("select * from whatsapp_assistant_session where phone=? for update", phone);
        Instant now = Instant.now();
        Object last = session.get("last_turn_at");
        if (last != null && instant(last).plusSeconds(2).isAfter(now)) {
            skipped("rate_limited");
            return "Espera unos segundos y vuelve a enviar tu mensaje para continuar.";
        }
        jdbc.update("update whatsapp_assistant_session set last_turn_at=? where phone=?", Timestamp.from(now), phone);
        String text = body.trim();
        Set<Long> permitted=Arrays.stream(tenantIds.split(",")).map(String::trim).filter(s -> s.matches("[0-9]{1,18}")).map(Long::valueOf).collect(java.util.stream.Collectors.toSet());
        String paymentReply=deposits.inbound(phone,text,messageSid,media,permitted);
        if (paymentReply!=null) return paymentReply;
        ObjectNode oldRoot = draft(session);
        if (session.get("expires_at") == null || instant(session.get("expires_at")).isBefore(now)) oldRoot = mapper.createObjectNode();
        if ("SALIR".equalsIgnoreCase(text)) {
            jdbc.update("update whatsapp_assistant_session set tenant_id=null,draft=null,confirmation_code=null,quote=null,expires_at=null where phone=?", phone);
            return "Conversacion cerrada. Para volver escribe HOLA.";
        }
        if (text.toUpperCase(Locale.ROOT).startsWith("GODS ")) {
            return onboarding.reply(phone,text.substring(5).trim(),mapper.createObjectNode(),now);
        }
        if (normalize(text).matches("^(quiero reservar en|quiero una cita en|reservar en) .+"))
            return onboarding.reply(phone,text,mapper.createObjectNode(),now);
        if (Set.of("cambiar negocio", "otro negocio", "cambiar barberia", "otra barberia").contains(normalize(text))) {
            jdbc.update("update whatsapp_assistant_session set tenant_id=null,draft=null,confirmation_code=null,quote=null,expires_at=null where phone=?",phone);
            return onboarding.reply(phone,"",mapper.createObjectNode(),now);
        }
        if (session.get("tenant_id") == null || session.get("expires_at") == null || instant(session.get("expires_at")).isBefore(now))
            return onboarding(phone,text,oldRoot,now);
        Long tenantId = ((Number) session.get("tenant_id")).longValue();
        Customer customer = customer(tenantId, phone);
        if (!allowed(tenantId) || customer == null) return unavailable();
        subscriptions.validateSubscriptionActive(tenantId);
        Subscription subscription = subscriptions.getCurrentSubscriptionOrThrow(tenantId);
        int limit = policy.monthlyTurns(subscription.getPlan());
        if (limit == 0) return unavailable();
        String month = YearMonth.from(time.today(tenantId)).toString();
        if (!usage.reserve(tenantId, month, limit)) {
            skipped("monthly_quota_exhausted");
            ObjectNode meta=metadata(oldRoot);
            if (month.equals(meta.path("quotaNoticeMonth").asText())) return null;
            meta.put("quotaNoticeMonth",month);
            jdbc.update("update whatsapp_assistant_session set draft=? where phone=?",mapper.writeValueAsString(oldRoot),phone);
            return "Este negocio alcanzo su limite mensual del asistente. Puedes continuar desde la app o contactar al negocio. El cupo se renueva el proximo mes.";
        }
        jdbc.update("update whatsapp_assistant_session set expires_at=? where phone=?", Timestamp.from(now.plusSeconds(1800)), phone);
        String clean = normalize(text);
        if ("menu".equals(clean)) {
            ObjectNode meta=metadata(oldRoot).deepCopy().put("stage","MENU").put("lastPrompt",MENU);
            meta.remove("options");
            save(phone,request(oldRoot),meta);
            return MENU+"\nTu seleccion anterior sigue guardada; elige Reservar para retomarla o NUEVA RESERVA para empezar de nuevo.";
        }        if (Set.of("hola","buenas","buenos dias","buenas tardes","menu","ayuda","como funciona","como reservo").contains(clean)) {
            if (oldRoot.has("serviceId") && !oldRoot.path("serviceId").isNull()) {
                return "Puedo ayudarte con puntos, promociones, servicios y reservas. Tu seleccion sigue guardada. Responde a esta pregunta para continuar: "
                        + metadata(oldRoot).path("lastPrompt").asText("Dime que dato deseas cambiar.")
                        + "\nPara comenzar otra vez escribe NUEVA RESERVA.";
            }
            return menu(phone,customer.getTenant().getNombre());
        }
        if (Set.of("nueva reserva","empezar de nuevo","reiniciar","descartar","cancelar borrador").contains(clean)) {
            clearDraft(phone);
            return menu(phone,customer.getTenant().getNombre());
        }
        if ("CONFIRM".equals(metadata(oldRoot).path("stage").asText()) && Set.of("no","no gracias","cancelar","no confirmo").contains(clean)) {
            clearDraft(phone);
            return "No se creo ninguna cita.\n"+menu(phone,customer.getTenant().getNombre());
        }        if ("CONFIRM".equals(metadata(oldRoot).path("stage").asText())
                && Set.of("si","si confirmo","confirmo","confirmar","dale","de acuerdo").contains(clean)
                && session.get("confirmation_code")!=null) text="CONFIRMAR "+session.get("confirmation_code");
        String customerAction=actions.reply(phone,tenantId,customer.getId(),text,oldRoot);
        if (customerAction!=null) return customerAction;
        int points = loyalty.findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .map(LoyaltyAccount::getPuntosDisponibles).orElse(0);
        String direct = directQuery(text);
        if ("MENU".equals(metadata(oldRoot).path("stage").asText())) {
            if ("2".equals(normalize(text))) direct="POINTS";
            if ("3".equals(normalize(text))) direct="PROMOTIONS";
        }
        if ("POINTS".equals(direct))
            return "Tienes " + points + " puntos disponibles en " + customer.getTenant().getNombre() + ".";
        List<Promotion> eligible = promotions.findActiveClientPromotions(tenantId, points);
        if ("PROMOTIONS".equals(direct)) return promotionMenu(phone,oldRoot,eligible);
        BookingBootstrapResponse catalog = bookings.getBootstrap(tenantId);
        JsonNode local = localIntent(text,oldRoot,catalog,tenantId);
        if (local != null && "POINTS".equals(local.path("intent").asText())) return "Tienes " + points + " puntos disponibles en " + customer.getTenant().getNombre() + ".";
        if (local != null && "PROMOTIONS".equals(local.path("intent").asText())) return promotionMenu(phone,oldRoot,eligible);
        if (text.toUpperCase(Locale.ROOT).startsWith("CONFIRMAR ")) {
            String token = Objects.toString(session.get("confirmation_code"), "");
            if (token.isBlank() || !text.equalsIgnoreCase("CONFIRMAR " + token) || session.get("draft") == null)
                return "No hay una confirmacion vigente con ese codigo. Pideme el horario de nuevo.";
            if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled()) && !deposits.supported()) return depositMessage();
            CreateAppointmentRequest request = request(oldRoot);
            if (!validRequest(request, catalog, eligible)) return "La seleccion ya no esta disponible. Pideme otra reserva.";
            BigDecimal current = bookings.quoteAssistantAppointment(tenantId, request);
            BigDecimal quoted = new BigDecimal(session.get("quote").toString());
            if (current.compareTo(quoted) != 0) {
                clearDraft(phone);
                return "El precio cambio. Pideme la reserva otra vez para confirmar el nuevo importe.";
            }
            if (!available(tenantId, request)) {
                clearDraft(phone);
                return "Ese horario ya no esta disponible. Indica otro horario.";
            }
            if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled())) {
                String response=deposits.begin(tenantId,customer.getId(),phone,request,current,catalog);
                clearDraft(phone);return response;
            }
            CreateAppointmentResponse result = bookings.createAppointment(tenantId, customer.getId(), request);
            clearDraft(phone);
            return "Reserva registrada #" + result.getAppointmentId() + ". Estado: " + result.getEstado()
                    + ". Fecha " + request.getDate() + " a las " + request.getHoraInicio() + ". Importe: " + catalog.getCurrency() + " " + current + ".";
        }
        // Local options must not wait on the model or depend on its context size.
        if (local!=null) {
            if ("HELP".equals(local.path("intent").asText())) return metadata(oldRoot).path("lastPrompt").asText(MENU);
            return prepare(phone,tenantId,local,catalog,eligible,oldRoot);
        }
        ObjectNode context = mapper.createObjectNode();
        context.put("today", time.today(tenantId).toString());
        var branches = context.putArray("branches");
        catalog.getBranches().forEach(b -> branches.addObject().put("id", b.getId()).put("name", b.getNombre()));
        var services = context.putArray("services");
        catalog.getServices().forEach(s -> services.addObject().put("id", s.getId()).put("name", s.getNombre()));
        var barbers = context.putArray("barbers");
        catalog.getBarbers().forEach(b -> {
            var entry = barbers.addObject().put("id", b.getId()).put("name", b.getNombre()).put("surname", b.getApellido());
            entry.set("branchIds", mapper.valueToTree(b.getBranchIds()));
        });
        context.set("promotions", mapper.valueToTree(eligible.stream().map(p -> Map.of("id", p.getId(), "title", p.getTitulo())).toList()));
        context.set("draft", oldRoot);
        if (context.toString().length() > 24000) return "Para este catalogo, realiza tu reserva desde la app de Super Gods.";
        JsonNode intent;
        try { intent = local == null ? interpreter.interpret(text, context.toString()) : local; }
        catch (Exception providerFailure) {
            org.slf4j.LoggerFactory.getLogger(WhatsappAssistantService.class).warn("WhatsApp assistant model unavailable: {}",WhatsappAssistantFailureDiagnostics.summarize(providerFailure));
            String prompt=metadata(oldRoot).path("lastPrompt").asText("");
            return "La interpretacion de texto libre esta tardando. Puedes continuar con el numero de una opcion.\n"
                    + (prompt.isBlank()?menu(phone,customer.getTenant().getNombre()):prompt);
        }
        return switch (intent.path("intent").asText()) {
            case "POINTS" -> "Tienes " + points + " puntos disponibles en " + catalog.getTenantName() + ".";
            case "PROMOTIONS" -> promotionMenu(phone,oldRoot,eligible);
            case "REWARDS" -> actions.reply(phone,tenantId,customer.getId(),"premios",oldRoot);
            case "MY_REDEMPTIONS" -> actions.reply(phone,tenantId,customer.getId(),"mis canjes",oldRoot);
            case "MY_APPOINTMENTS" -> actions.reply(phone,tenantId,customer.getId(),"mis reservas",oldRoot);
            case "BOOKING", "AVAILABILITY", "BARBERS", "SERVICES", "PACKAGES", "TEAM", "CONTINUE" -> prepare(phone, tenantId, intent, catalog, eligible, oldRoot);
            case "HANDOFF" -> "Para cancelar o reprogramar una cita existente, abre tus reservas en la app de Super Gods. Si necesitas otra ayuda, contacta al negocio.";
            default -> "Puedo ayudarte con puntos, promociones, precios y reservas de este negocio. "
                    + (metadata(oldRoot).path("lastPrompt").asText("").isBlank()?MENU:metadata(oldRoot).path("lastPrompt").asText())
                    + "\nPara consultas que no esten en el catalogo, contacta al negocio.";
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(Long tenantId) {
        Subscription sub = subscriptions.getCurrentSubscriptionOrThrow(tenantId);
        int limit = policy.monthlyTurns(sub.getPlan());
        String month = YearMonth.from(time.today(tenantId)).toString();
        boolean active = false;
        try { subscriptions.validateSubscriptionActive(tenantId); active = true; }
        catch (RuntimeException ignored) { /* Return inactive status, never unlock an expired subscription. */ }
        boolean ready = enabled && allowed(tenantId) && limit > 0 && active;
        int used = ready ? usage.used(tenantId, month) : 0;
        var result = new java.util.LinkedHashMap<String,Object>(Map.of("enabled", ready, "plan", sub.getPlan(), "monthlyTurns", limit,
                "usedTurns", used, "remainingTurns", Math.max(0, limit - used), "month", month, "aiConfigured", interpreter.configured()));
        String code=tenants.findById(tenantId).map(Tenant::getCodigo).orElse("");
        String sender=Objects.toString(fromNumber, "").replaceFirst("^whatsapp:", "");
        result.put("businessCode",code);
        result.put("bookingWhatsappUrl",ready && sender.matches("\\+[1-9][0-9]{7,14}") && !code.isBlank()
                ? "https://wa.me/"+sender.substring(1)+"?text="+java.net.URLEncoder.encode("GODS "+code,java.nio.charset.StandardCharsets.UTF_8) : "");
        return result;
    }
    /** Recognize only unambiguous read requests; mixed requests still go through the model. */
    private String directQuery(String text) {
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        for (int i = 0; i < 5; i++) {
            normalized = normalized.replaceFirst("^(hola|a ver|dime|por favor|quiero saber) +", "");
        }
        normalized = normalized.replaceFirst(" +por favor$", "");
        if (Set.of("puntos", "mis puntos", "consultar mis puntos", "cuantos puntos tengo", "saldo de puntos", "cuantos puntos tengo disponibles").contains(normalized)) return "POINTS";
        if (Set.of("promos", "promociones", "ver promociones", "que promociones tienen", "que promos tienen", "que promociones hay", "que promos hay").contains(normalized)) return "PROMOTIONS";
        return null;
    }
    private String skipped(String reason) {
        log.info("WhatsApp assistant skipped: reason={}, dialogueVersion=3", reason);
        return null;
    }
    private boolean correctDestination(String to) {
        if (fromNumber == null || fromNumber.isBlank() || to == null) return false;
        String configured = fromNumber.trim();
        return to.equals(configured.startsWith("whatsapp:") ? configured : "whatsapp:" + configured);
    }

    private Customer customer(Long tenantId, String phone) {
        return customers.findByTenantIdAndTelefonoWithTenant(tenantId, phone)
                .filter(Customer::isPhoneVerified)
                .filter(c -> c.getTenant() != null && !Boolean.FALSE.equals(c.getTenant().getActive()))
                .filter(c -> c.getWhatsappOptedOutAt() == null && Boolean.TRUE.equals(c.getWhatsappTransactionalEnabled()))
                .orElse(null);
    }

    private static final String MENU = WhatsappAssistantMenu.TEXT;

    private ObjectNode draft(Map<String, Object> session) throws Exception {
        Object value = session.get("draft");
        return value == null ? mapper.createObjectNode() : (ObjectNode) mapper.readTree(value.toString());
    }
    private ObjectNode metadata(ObjectNode draft) {
        if (!(draft.get("_assistant") instanceof ObjectNode)) draft.set("_assistant", mapper.createObjectNode());
        return (ObjectNode) draft.get("_assistant");
    }
    private CreateAppointmentRequest request(ObjectNode draft) throws Exception {
        ObjectNode copy = draft.deepCopy(); copy.remove("_assistant");
        return mapper.treeToValue(copy, CreateAppointmentRequest.class);
    }
    private void save(String phone, CreateAppointmentRequest request, ObjectNode meta) throws Exception {
        ObjectNode root = mapper.valueToTree(request); root.set("_assistant", meta);
        jdbc.update("update whatsapp_assistant_session set draft=?,confirmation_code=null,quote=null where phone=?", mapper.writeValueAsString(root), phone);
    }
    private ObjectNode choice(Long id, String label) { return mapper.createObjectNode().put("id", id).put("label", label); }
    private ObjectNode valueChoice(String value, String label) { return mapper.createObjectNode().put("value", value).put("label", label); }
    private String ask(String phone, CreateAppointmentRequest request, ObjectNode meta, String stage, String prompt, List<ObjectNode> options) throws Exception {
        meta.put("stage", stage);
        var choices = meta.putArray("options"); options.forEach(choices::add);
        StringBuilder reply = new StringBuilder(prompt);
        for (int i=0;i<options.size();i++) reply.append("\n").append(i+1).append(". ").append(options.get(i).path("label").asText());
        if (!options.isEmpty()) reply.append("\nResponde con el numero o el nombre.");
        meta.put("lastPrompt", reply.toString());
        save(phone, request, meta);
        return reply.toString();
    }
    private String menu(String phone, String business) throws Exception {
        var meta=mapper.createObjectNode().put("stage","MENU").put("mode","BOOKING").put("lastPrompt",MENU);
        save(phone,new CreateAppointmentRequest(),meta);
        return "Estas en " + business + ".\n" + MENU;
    }
    private List<Customer> businesses(String phone) {
        return customers.findActiveByPhoneAcrossTenants(phone).stream()
                .filter(Customer::isPhoneVerified)
                .filter(c -> c.getWhatsappOptedOutAt()==null && Boolean.TRUE.equals(c.getWhatsappTransactionalEnabled()))
                .filter(c -> allowed(c.getTenant().getId()))
                .filter(c -> {
                    try {
                        subscriptions.validateSubscriptionActive(c.getTenant().getId());
                        return policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(c.getTenant().getId()).getPlan())>0;
                    } catch (RuntimeException failure) { return false; }
                }).toList();
    }
    private String onboarding(String phone, String text, ObjectNode oldDraft, Instant now) throws Exception {
        String stage=metadata(oldDraft).path("stage").asText();
        if (stage.startsWith("REGISTER_") || stage.equals("DISCOVER")) return onboarding.reply(phone,text,oldDraft,now);
        if (!stage.equals("BUSINESS") && !Set.of("hola","buenas","buenos dias","buenas tardes","menu","ayuda","puntos","reservar","quiero reservar").contains(normalize(text)))
            return onboarding.reply(phone,text,oldDraft,now);
        List<Customer> candidates=businesses(phone);
        if (candidates.isEmpty()) return "¡Hola! ¿En que barberia quieres reservar? Escribe su nombre o codigo, o envia el mensaje preparado del enlace o QR del negocio.";
        ObjectNode oldMeta=metadata(oldDraft);
        Customer selected=null;
        if ("BUSINESS".equals(oldMeta.path("stage").asText())) {
            JsonNode option=selectedOption(text,oldMeta.path("options"));
            if (option!=null) selected=candidates.stream().filter(c -> c.getTenant().getId().equals(option.path("id").asLong())).findFirst().orElse(null);
        }
        if (selected==null) {
            List<Customer> matches=candidates.stream().filter(c -> normalize(text).equals(normalize(c.getTenant().getNombre()))
                    || normalize(text).equals(normalize(c.getTenant().getCodigo()))).toList();
            if (matches.size()==1) selected=matches.get(0);
        }
        if (selected==null && "BUSINESS".equals(oldMeta.path("stage").asText())
                && !normalize(text).matches("[0-9]{1,2}")
                && !Set.of("hola","menu","ayuda").contains(normalize(text)))
            return onboarding.reply(phone,text,mapper.createObjectNode(),now);
        if (selected==null && candidates.size()==1) selected=candidates.get(0);
        if (selected!=null) {
            Long id=selected.getTenant().getId();
            if (!usage.reserve(id, YearMonth.from(time.today(id)).toString(),policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(id).getPlan()))) return skipped("monthly_quota_exhausted");
            jdbc.update("update whatsapp_assistant_session set tenant_id=?,expires_at=? where phone=?",id,Timestamp.from(now.plusSeconds(1800)),phone);
            return menu(phone,selected.getTenant().getNombre());
        }
        var meta=mapper.createObjectNode();
        String prompt=ask(phone,new CreateAppointmentRequest(),meta,"BUSINESS","Hola, soy el asistente de Super Gods. ¿En que negocio quieres atenderte?",
                candidates.stream().limit(20).map(c -> choice(c.getTenant().getId(),c.getTenant().getNombre())).toList());
        jdbc.update("update whatsapp_assistant_session set tenant_id=null,expires_at=? where phone=?",Timestamp.from(now.plusSeconds(1800)),phone);
        return prompt;
    }
    private String normalize(String text) {
        return java.text.Normalizer.normalize(Objects.toString(text,""),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 :_-]"," ").replaceAll("\\s+"," ").trim();
    }
    private JsonNode selectedOption(String text, JsonNode options) {
        String clean=normalize(text).replaceFirst("^(el|la|opcion) +","");
        Map<String,Integer> ordinals=Map.of("primero",1,"primera",1,"segundo",2,"segunda",2,"tercero",3,"tercera",3);
        Integer index=ordinals.get(clean);
        if (index==null && clean.matches("[0-9]{1,2}")) index=Integer.parseInt(clean);
        if (index!=null) return index>0 && index<=options.size()?options.get(index-1):null;
        List<JsonNode> matches=new ArrayList<>();
        for (JsonNode option: options) if (normalize(option.path("label").asText()).equals(clean)) matches.add(option);
        return matches.size()==1?matches.get(0):null;
    }
    private ObjectNode localIntent(String text, ObjectNode root, BookingBootstrapResponse catalog, Long tenantId) {
        ObjectNode meta=metadata(root);
        String stage=meta.path("stage").asText();
        String clean=normalize(text);
        var result=mapper.createObjectNode().put("intent","CONTINUE");
        if ("MENU".equals(stage) && clean.matches("[1-6]")) {
            return result.put("intent",List.of("BOOKING","POINTS","PROMOTIONS","AVAILABILITY","BARBERS","SERVICES").get(Integer.parseInt(clean)-1));
        }
        if (("MENU".equals(stage) && "10".equals(clean)) || Set.of("paquetes","ver paquetes","paquetes y precios","combos","ver combos","que paquetes tienen","que incluye el paquete").contains(clean)) return result.put("intent","PACKAGES");
        if (Set.of("reservar","quiero reservar","reservar una cita","quiero una cita").contains(clean)) return result.put("intent","BOOKING");
        if (Set.of("servicios","servicios y precios","que servicios tienen","precios").contains(clean)) return result.put("intent","SERVICES");
        if (Set.of("barberos","ver barberos","quienes son los barberos","que barberos hay").contains(clean)) return result.put("intent","TEAM");
        if (Set.of("barberos disponibles","ver barberos disponibles","que barberos estan disponibles","que barberos hay disponibles","profesionales disponibles","ver profesionales disponibles").contains(clean)) return result.put("intent","BARBERS");
        if (Set.of("horarios","horarios disponibles","ver horarios disponibles").contains(clean)) return result.put("intent","AVAILABILITY");
        JsonNode option=selectedOption(text,meta.path("options"));
        if (option!=null && Set.of("SERVICE","BRANCH","BARBER","TIME","PROMOTION").contains(stage)) {
            switch (stage) {
                case "SERVICE" -> result.put("serviceId",option.path("id").asLong());
                case "BRANCH" -> result.put("branchId",option.path("id").asLong());
                case "BARBER" -> { if (option.path("id").isNumber()) result.put("barberId",option.path("id").asLong()); else result.put("anyBarber",true); }
                case "TIME" -> result.put("time",option.path("value").asText());
                case "PROMOTION" -> result.put("promotionId",option.path("id").asLong()).put("intent","BOOKING");
            }
            return result;
        }
        if (option==null && meta.path("options").size()>0 && clean.matches("(?:el |la |opcion )?(?:[0-9]{1,2}|primero|primera|segundo|segunda|tercero|tercera)")) return result.put("intent","HELP");        if ("BARBER".equals(stage) && Set.of("cualquiera","cualquier barbero","me da igual","sin preferencia").contains(clean)) return result.put("anyBarber",true);
        if (!Set.of("MENU","BUSINESS","").contains(stage)) {
            if (Set.of("hoy","manana","pasado manana").contains(clean)) return result.put("date",time.today(tenantId).plusDays("hoy".equals(clean)?0:"manana".equals(clean)?1:2).toString());
            if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) return result.put("date",clean);
            if (clean.matches("(?:a las )?([01]?\\d|2[0-3]):[0-5]\\d")) {
                String hour=clean.replaceFirst("^a las ","");
                return result.put("time",hour.length()==4?"0"+hour:hour);
            }
            List<ServiceMiniResponse> services=catalog.getServices().stream().filter(s -> normalize(s.getNombre()).equals(clean)).toList();
            if (services.size()==1) return result.put("serviceId",services.get(0).getId());
            List<BarberMiniResponse> barbers=catalog.getBarbers().stream().filter(b -> normalize(b.getNombre()).equals(clean)
                    || normalize(b.getNombre()+" "+Objects.toString(b.getApellido(),"")).equals(clean)).toList();
            if (barbers.size()==1) return result.put("barberId",barbers.get(0).getId());
        }
        return null;
    }

    private List<BarberMiniResponse> eligibleBarbers(CreateAppointmentRequest r, BookingBootstrapResponse catalog) {
        return catalog.getBarbers().stream().filter(b -> b.getBranchIds()!=null && b.getBranchIds().contains(r.getBranchId())
                || b.getBranchIds()==null && Objects.equals(b.getBranchId(),r.getBranchId()))
                .filter(b -> b.getServiceIdsByBranch()==null || !b.getServiceIdsByBranch().containsKey(r.getBranchId())
                        || b.getServiceIdsByBranch().get(r.getBranchId()).contains(r.getServiceId())).toList();
    }
    private List<String> slots(Long tenantId, CreateAppointmentRequest r) {
        var availability=bookings.getAvailability(tenantId,r.getBranchId(),r.getServiceId(),r.getDate(),r.getBarberId());
        return availability==null || availability.getSlots()==null?List.of():availability.getSlots();
    }
    private String prepare(String phone, Long tenantId, JsonNode intent, BookingBootstrapResponse catalog, List<Promotion> eligible, ObjectNode oldRoot) throws Exception {
        String answer=prepareBooking(phone,tenantId,intent,catalog,eligible,oldRoot);
        Long selected=number(intent,"serviceId");
        if (selected!=null && !Objects.equals(selected,number(oldRoot,"serviceId"))) {
            var pack=catalog.getServices().stream().filter(v -> v.getId().equals(selected) && isPackage(v)).findFirst();
            if (pack.isPresent()) return packageDetail(pack.get(),catalog)+"\n\n"+answer;
        }
        return answer;
    }
    private String prepareBooking(String phone, Long tenantId, JsonNode intent, BookingBootstrapResponse catalog, List<Promotion> eligible, ObjectNode oldRoot) throws Exception {
        ObjectNode meta=metadata(oldRoot).deepCopy();
        CreateAppointmentRequest r=request(oldRoot);
        String action=intent.path("intent").asText();
        String mode=Set.of("BOOKING","AVAILABILITY","BARBERS","SERVICES","PACKAGES","TEAM").contains(action)?action:meta.path("mode").asText("BOOKING");
        meta.put("mode",mode);
        Long branchId=number(intent,"branchId"),serviceId=number(intent,"serviceId");
        if (branchId!=null && !branchId.equals(r.getBranchId())) {
            r.setBranchId(branchId); r.setBarberId(null); r.setHoraInicio(null); meta.put("barberChosen",false);
        }
        if (serviceId!=null && !serviceId.equals(r.getServiceId())) {
            r.setServiceId(serviceId); r.setBarberId(null); r.setHoraInicio(null); r.setPromotionId(null); meta.put("barberChosen",false);
        }
        if (intent.path("date").isTextual() && !intent.path("date").asText().equals(r.getDate())) { r.setDate(intent.path("date").asText()); r.setHoraInicio(null); }
        if (intent.path("anyBarber").asBoolean(false)) { r.setBarberId(null); meta.put("barberChosen",true); }
        if (number(intent,"barberId")!=null) { if (!number(intent,"barberId").equals(r.getBarberId())) r.setHoraInicio(null); r.setBarberId(number(intent,"barberId")); meta.put("barberChosen",true); }
        if (intent.path("time").isTextual()) { r.setHoraInicio(intent.path("time").asText()); meta.remove("afterTime"); }
        if (intent.path("afterTime").isTextual()) { meta.put("afterTime",intent.path("afterTime").asText()); r.setHoraInicio(null); }
        if (intent.path("clearPromotion").asBoolean(false)) r.setPromotionId(null);
        if (number(intent,"promotionId")!=null) {
            Long promotionId=number(intent,"promotionId");
            Promotion promotion=eligible.stream().filter(p -> p.getId().equals(promotionId)).findFirst().orElse(null);
            if (promotion==null) return ask(phone,r,meta,"SERVICE","Esa promocion no esta disponible para tu cuenta. ¿Que servicio deseas?",serviceChoices(catalog));
            r.setPromotionId(promotionId);
            if (r.getServiceId()==null && promotion.getRedirectType()!=null && "SERVICE".equals(promotion.getRedirectType().name())) {
                try { r.setServiceId(Long.parseLong(promotion.getRedirectValue())); } catch (RuntimeException ignored) { /* Ask explicitly below. */ }
            }
        }
        r.setDepositRequired(false);
        if (catalog.getServices().isEmpty() && !"TEAM".equals(mode))
            return ask(phone,r,meta,"MENU","El negocio no tiene servicios activos configurados. Contacta al negocio o escribe MENU para otra consulta.",List.of());
        if ("PACKAGES".equals(mode)) {
            var packages=catalog.getServices().stream().filter(this::isPackage).toList();
            meta.put("mode","BOOKING");
            if (packages.isEmpty()) return ask(phone,r,meta,"MENU","Este negocio no tiene paquetes activos configurados. Escribe SERVICIOS para consultar el catalogo o MENU para otras opciones.",List.of());
            return ask(phone,r,meta,"SERVICE","Paquetes de "+catalog.getTenantName()+". Precio del paquete y servicios incluidos. Elige uno para ver su detalle y reservar:",
                    packages.stream().limit(20).map(v -> serviceChoice(v,catalog)).toList());
        }
        if ("SERVICES".equals(mode)) {
            meta.put("mode","BOOKING");
            return ask(phone,r,meta,"SERVICE","Servicios, paquetes y precios de "+catalog.getTenantName()+" ("+catalog.getCurrency()+"). Elige uno si deseas reservar:",serviceChoices(catalog));
        }
        if ("TEAM".equals(mode)) {
            return ask(phone,r,meta,"MENU",(catalog.getBarbers().isEmpty()?"No hay profesionales activos configurados.":"Profesionales del negocio:\n")+String.join("\n",catalog.getBarbers().stream().map(b -> b.getNombre()+" "+Objects.toString(b.getApellido(),"")).toList())
                    +"\nPara conocer disponibilidad necesito servicio, sede y dia. Puedes escribir 'barberos disponibles' o 'reservar'.",List.of());
        }
        if (r.getServiceId()==null || catalog.getServices().stream().noneMatch(s -> s.getId().equals(r.getServiceId()))) {
            r.setServiceId(null);
            return ask(phone,r,meta,"SERVICE","BARBERS".equals(mode)
                    ? "Para consultar barberos disponibles necesito el servicio y el dia. Primero elige un servicio:"
                    : "AVAILABILITY".equals(mode) ? "Para consultar horarios disponibles, primero elige un servicio:" : "¿Para que servicio buscas?",serviceChoices(catalog));
        }
        if (catalog.getBranches().isEmpty()) return ask(phone,r,meta,"MENU","El negocio no tiene sedes activas configuradas para reservar. Contacta al negocio.",List.of());
        if (r.getBranchId()==null && catalog.getBranches().size()==1) r.setBranchId(catalog.getBranches().get(0).getId());
        if (r.getBranchId()==null || catalog.getBranches().stream().noneMatch(b -> b.getId().equals(r.getBranchId()))) {
            r.setBranchId(null);
            return ask(phone,r,meta,"BRANCH","¿En que sede quieres atenderte?",catalog.getBranches().stream().limit(20).map(b -> choice(b.getId(),b.getNombre())).toList());
        }
        var professionals=eligibleBarbers(r,catalog);
        if (r.getBarberId()!=null && professionals.stream().noneMatch(b -> b.getId().equals(r.getBarberId()))) {
            r.setBarberId(null); meta.put("barberChosen",false);
            return ask(phone,r,meta,"BARBER","Ese profesional no atiende ese servicio en esta sede. Elige otro:",barberChoices(professionals));
        }
        if (!"BARBERS".equals(mode) && !meta.path("barberChosen").asBoolean() && professionals.size()>1 && r.getHoraInicio()==null) {
            return ask(phone,r,meta,"BARBER","¿Prefieres algun profesional o cualquiera disponible?",barberChoices(professionals));
        }
        try {
            if (r.getDate()==null || LocalDate.parse(r.getDate()).isBefore(time.today(tenantId))) {
                r.setDate(null); return ask(phone,r,meta,"DATE","¿Para que dia? Puedes decir hoy, mañana o escribir una fecha como "+time.today(tenantId).plusDays(1)+".",List.of());
            }
        } catch (RuntimeException invalidDate) { r.setDate(null); return ask(phone,r,meta,"DATE","No entendi la fecha. ¿Para que dia buscas?",List.of()); }
        if ("BARBERS".equals(mode)) {
            List<ObjectNode> choices=new ArrayList<>();
            StringBuilder description=new StringBuilder("Profesionales disponibles para "+r.getDate()+":");
            for (BarberMiniResponse barber: professionals.stream().filter(b -> r.getBarberId()==null || b.getId().equals(r.getBarberId())).limit(20).toList()) {
                CreateAppointmentRequest probe=mapper.readValue(mapper.writeValueAsString(r),CreateAppointmentRequest.class); probe.setBarberId(barber.getId());
                List<String> availableSlots=filteredSlots(slots(tenantId,probe),meta);
                if (r.getHoraInicio()!=null) availableSlots=availableSlots.stream().filter(r.getHoraInicio()::equals).toList();
                if (!availableSlots.isEmpty()) { choices.add(choice(barber.getId(),barber.getNombre())); description.append("\n").append(barber.getNombre()).append(": ").append(String.join(", ",availableSlots.stream().limit(4).toList())); }
            }
            meta.put("mode","BOOKING");
            if (choices.isEmpty()) { r.setHoraInicio(null); return ask(phone,r,meta,"DATE","No encontre profesionales disponibles para esa seleccion. ¿Buscamos otro dia?",List.of()); }
            return ask(phone,r,meta,"BARBER",description+"\nElige un profesional si deseas reservar.",choices);
        }
        List<String> availableSlots=filteredSlots(slots(tenantId,r),meta);
        if (availableSlots.isEmpty()) { r.setHoraInicio(null); return ask(phone,r,meta,"DATE","No encontre horarios disponibles para esa seleccion. ¿Buscamos otro dia o profesional?",List.of()); }
        boolean validTime=r.getHoraInicio()!=null && r.getHoraInicio().matches("([01][0-9]|2[0-3]):[0-5][0-9]") && availableSlots.contains(r.getHoraInicio());
        if (!validTime || "AVAILABILITY".equals(mode)) {
            String prompt=r.getHoraInicio()!=null&&!validTime?"Ese horario ya no esta disponible. Elige otro:":"Horarios disponibles para "+r.getDate()+". Elige uno si deseas reservar:";
            r.setHoraInicio(null); meta.put("mode","BOOKING");
            return ask(phone,r,meta,"TIME",prompt,availableSlots.stream().limit(16).map(s -> valueChoice(s,s)).toList());
        }
        if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled()) && !deposits.supported()) return ask(phone,r,meta,"MENU",depositMessage(),List.of());
        if (!validRequest(r,catalog,eligible)) return ask(phone,r,meta,"SERVICE","La seleccion requiere revision. Elige un servicio con precio base configurado o contacta al negocio:",serviceChoices(catalog));
        BigDecimal quote=bookings.quoteAssistantAppointment(tenantId,r);
        String service=catalog.getServices().stream().filter(s -> s.getId().equals(r.getServiceId())).findFirst().orElseThrow().getNombre();
        String branch=catalog.getBranches().stream().filter(b -> b.getId().equals(r.getBranchId())).findFirst().orElseThrow().getNombre();
        String barber=r.getBarberId()==null?"cualquier profesional disponible":catalog.getBarbers().stream().filter(b -> b.getId().equals(r.getBarberId())).findFirst().orElseThrow().getNombre();
        String promotion=r.getPromotionId()==null?"":". Promocion: "+eligible.stream().filter(p -> p.getId().equals(r.getPromotionId())).findFirst().orElseThrow().getTitulo();
        String depositSummary="";
        if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled())) {
            WhatsappDepositService.Offer offer=deposits.offer(tenantId,r.getBranchId(),quote,catalog);
            if (offer==null) return ask(phone,r,meta,"MENU","No encontre datos de Yape validos para esta sede. Contacta al negocio o completa la reserva en la app.",List.of());
            r.setDepositRequired(true);r.setDepositAmount(offer.amount());r.setDepositPaymentMethodId(offer.methodId());
            depositSummary=" Adelanto requerido: "+catalog.getCurrency()+" "+offer.amount()+". Al confirmar apartaremos el horario y te mostraremos los datos de Yape; la cita solo quedara confirmada cuando el dueño apruebe el comprobante.";
        }
        String token=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        boolean variablePrice=catalog.getServices().stream().anyMatch(s -> s.getId().equals(r.getServiceId()) && Boolean.TRUE.equals(s.getPrecioVariable()));
        String summary=service+" en "+branch+", con "+barber+", "+r.getDate()+" a las "+r.getHoraInicio()+promotion+(variablePrice?". Precio desde (base de la reserva): ":". Total: ")+catalog.getCurrency()+" "+quote
                +(variablePrice?". Usaremos este precio base para la reserva y el adelanto; el precio final puede variar segun lo acordado con el negocio":"")
                +depositSummary+". ¿Confirmo tu reserva? Responde SI o CONFIRMAR "+token+". Todavia no esta reservada.";
        ask(phone,r,meta,"CONFIRM",summary,List.of());
        jdbc.update("update whatsapp_assistant_session set confirmation_code=?,quote=? where phone=?",token,quote,phone);
        return summary;
    }
    private String promotionMenu(String phone,ObjectNode root,List<Promotion> eligible) throws Exception {
        if (eligible.isEmpty()) return ask(phone,request(root),metadata(root).deepCopy(),"PROMOTION",
                "No encontre promociones vigentes disponibles para tu cuenta. Escribe RESERVAR o MENU para otras opciones.",List.of());
        return ask(phone,request(root),metadata(root).deepCopy(),"PROMOTION","Promociones vigentes de este negocio. Elige una para consultar sus condiciones y reservar:",
                eligible.stream().limit(8).map(p -> choice(p.getId(),p.getTitulo()
                        +(p.getPriceText()==null?"":" — "+p.getPriceText())
                        +(p.getDescripcion()==null?"":". "+p.getDescripcion().substring(0,Math.min(100,p.getDescripcion().length()))))).toList());
    }    private List<String> filteredSlots(List<String> slots, ObjectNode meta) {
        String after=meta.path("afterTime").asText("");
        if (!after.matches("([01][0-9]|2[0-3]):[0-5][0-9]")) return slots;
        return slots.stream().filter(s -> !LocalTime.parse(s).isBefore(LocalTime.parse(after))).toList();
    }
    private boolean isPackage(ServiceMiniResponse service) {
        return service.getPackageServiceIds()!=null && !service.getPackageServiceIds().isEmpty();
    }
    private String shortText(String value,int limit) {
        String clean=Objects.toString(value,"").replaceAll("\\s+"," ").trim();
        int count=clean.codePointCount(0,clean.length());
        return count<=limit?clean:clean.substring(0,clean.offsetByCodePoints(0,limit))+"…";
    }
    private String servicePrice(ServiceMiniResponse service,BookingBootstrapResponse catalog) {
        return service.getPrecio()==null?"precio no configurado"
                :(Boolean.TRUE.equals(service.getPrecioVariable())?"Precio desde ":"")
                +Objects.toString(catalog.getCurrency(),"PEN")+" "
                +BigDecimal.valueOf(service.getPrecio()).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private String packageIncludes(ServiceMiniResponse service,BookingBootstrapResponse catalog) {
        if (!isPackage(service)) return "";
        List<String> names=new ArrayList<>();
        boolean missing=false;
        for (Long id:service.getPackageServiceIds().stream().limit(20).toList()) {
            var match=catalog.getServices().stream().filter(v -> Objects.equals(v.getId(),id)).findFirst();
            if (match.isPresent()) names.add(match.get().getNombre()); else missing=true;
        }
        String result=String.join(", ",names);
        if (missing) result+=(result.isBlank()?"":"; ")+"hay componentes cuyo detalle debe confirmar el negocio";
        return result;
    }
    private ObjectNode serviceChoice(ServiceMiniResponse service,BookingBootstrapResponse catalog) {
        String label=(isPackage(service)?"Paquete: ":"")+service.getNombre()+" — "+servicePrice(service,catalog);
        if (service.getDescripcion()!=null && !service.getDescripcion().isBlank()) label+="\n   "+shortText(service.getDescripcion(),120);
        if (isPackage(service)) label+="\n   Incluye: "+shortText(packageIncludes(service,catalog),220);
        return choice(service.getId(),label);
    }
    private String packageDetail(ServiceMiniResponse service,BookingBootstrapResponse catalog) {
        String detail="Paquete: "+service.getNombre()+"\nPrecio del paquete: "+servicePrice(service,catalog);
        if (service.getDescripcion()!=null && !service.getDescripcion().isBlank()) detail+="\nDescripcion: "+shortText(service.getDescripcion(),800);
        detail+="\nIncluye: "+packageIncludes(service,catalog);
        if (service.getDuracionMinutos()!=null) detail+="\nDuracion: "+service.getDuracionMinutos()+" minutos.";
        return detail;
    }
    private List<ObjectNode> serviceChoices(BookingBootstrapResponse catalog) {
        return catalog.getServices().stream().limit(20).map(v -> serviceChoice(v,catalog)).toList();
    }
    private List<ObjectNode> barberChoices(List<BarberMiniResponse> professionals) {
        List<ObjectNode> choices=new ArrayList<>(professionals.stream().limit(19).map(b -> choice(b.getId(),(b.getNombre()+" "+Objects.toString(b.getApellido(),"")).trim())).toList());
        choices.add(valueChoice("ANY","Cualquiera disponible")); return choices;
    }

    private boolean validRequest(CreateAppointmentRequest r, BookingBootstrapResponse c, List<Promotion> eligible) {
        if (r.getDate() == null || r.getBranchId() == null || r.getServiceId() == null) return false;
        try { LocalDate.parse(r.getDate()); } catch (RuntimeException e) { return false; }
        return c.getBranches().stream().anyMatch(b -> b.getId().equals(r.getBranchId()))
                && c.getServices().stream().anyMatch(s -> s.getId().equals(r.getServiceId()) && s.getPrecio()!=null && s.getPrecio()>0)
                && (r.getBarberId() == null || c.getBarbers().stream().anyMatch(b -> b.getId().equals(r.getBarberId())))
                && (r.getPromotionId() == null || eligible.stream().anyMatch(p -> p.getId().equals(r.getPromotionId())
                    && (p.getBranch() == null || p.getBranch().getId().equals(r.getBranchId()))
                    && (p.getRedirectType() == null || !"SERVICE".equals(p.getRedirectType().name()) || Objects.equals(p.getRedirectValue(), r.getServiceId().toString()))));
    }

    private boolean available(Long tenantId, CreateAppointmentRequest r) {
        return bookings.getAvailability(tenantId, r.getBranchId(), r.getServiceId(), r.getDate(), r.getBarberId()).getSlots().contains(r.getHoraInicio());
    }
    private void clearDraft(String phone) {
        jdbc.update("update whatsapp_assistant_session set draft=null,confirmation_code=null,quote=null where phone=?", phone);
    }
    private boolean allowed(Long id) {
        return Arrays.stream(tenantIds.split(",")).map(String::trim).anyMatch(id.toString()::equals);
    }
    private Long number(JsonNode n, String field) { return n.path(field).isIntegralNumber() ? n.path(field).longValue() : null; }
    private Instant instant(Object value) { return value instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) value).toInstant(); }
    private String unavailable() { return "No puedo activar el asistente para ese negocio y numero. Revisa tu cuenta verificada en la app o consulta al negocio."; }
    private String depositMessage() { return "Este negocio usa adelantos. Completa la reserva y el comprobante desde la app de Super Gods."; }
}
