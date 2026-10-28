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
    @Value("${whatsapp.assistant.enabled:false}") private boolean enabled;
    @Value("${whatsapp.assistant.tenant-ids:}") private String tenantIds;
    @Value("${whatsapp.twilio.from-number:}") private String fromNumber;

    @Transactional(rollbackFor = Exception.class)
    public String reply(String from, String to, String body, String messageSid) throws Exception {
        if (!enabled || !interpreter.configured() || !correctDestination(to) || from == null || !PHONE.matcher(from).matches()
                || messageSid == null || !SID.matcher(messageSid).matches()
                || body == null || body.isBlank() || body.length() > 1200) return null;
        String phone = from.substring("whatsapp:".length());
        // Duplicate webhook retries never execute a second action or emit a second reply.
        if (jdbc.update("insert into whatsapp_assistant_message(message_sid) values (?) on conflict do nothing", messageSid) == 0)
            return null;
        jdbc.update("insert into whatsapp_assistant_session(phone) values (?) on conflict do nothing", phone);
        Map<String, Object> session = jdbc.queryForMap("select * from whatsapp_assistant_session where phone=? for update", phone);
        Instant now = Instant.now();
        Object last = session.get("last_turn_at");
        if (last != null && instant(last).plusSeconds(2).isAfter(now)) return null;
        jdbc.update("update whatsapp_assistant_session set last_turn_at=? where phone=?", Timestamp.from(now), phone);
        String text = body.trim();
        if ("SALIR".equalsIgnoreCase(text)) {
            jdbc.update("update whatsapp_assistant_session set tenant_id=null,draft=null,confirmation_code=null,quote=null,expires_at=null where phone=?", phone);
            return "Conversacion cerrada. Para volver escribe GODS seguido del codigo del negocio.";
        }
        if (text.toUpperCase(Locale.ROOT).startsWith("GODS ")) {
            String code = text.substring(5).trim();
            var tenant = tenants.findByCodigoIgnoreCaseAndActiveTrue(code);
            if (tenant.isEmpty() || !allowed(tenant.get().getId())) return unavailable();
            Customer customer = customer(tenant.get().getId(), phone);
            if (customer == null) return unavailable();
            subscriptions.validateSubscriptionActive(tenant.get().getId());
            if (policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(tenant.get().getId()).getPlan()) == 0)
                return unavailable();
            Long selectedId = tenant.get().getId();
            int selectedLimit = policy.monthlyTurns(subscriptions.getCurrentSubscriptionOrThrow(selectedId).getPlan());
            if (!usage.reserve(selectedId, YearMonth.from(time.today(selectedId)).toString(), selectedLimit)) return null;
            jdbc.update("update whatsapp_assistant_session set tenant_id=?,draft=null,confirmation_code=null,quote=null,expires_at=? where phone=?",
                    tenant.get().getId(), Timestamp.from(now.plusSeconds(1800)), phone);
            return "Estas en " + tenant.get().getNombre() + ". Puedes consultar tus puntos, promociones o reservar. Para cambiar de negocio escribe GODS y su codigo. Para cerrar escribe SALIR.";
        }
        if (session.get("tenant_id") == null || session.get("expires_at") == null || instant(session.get("expires_at")).isBefore(now))
            return "Hola, soy el asistente de Super Gods. Escribe GODS seguido del codigo de tu negocio. Tu numero debe estar verificado en la app.";
        Long tenantId = ((Number) session.get("tenant_id")).longValue();
        Customer customer = customer(tenantId, phone);
        if (!allowed(tenantId) || customer == null) return unavailable();
        subscriptions.validateSubscriptionActive(tenantId);
        Subscription subscription = subscriptions.getCurrentSubscriptionOrThrow(tenantId);
        int limit = policy.monthlyTurns(subscription.getPlan());
        if (limit == 0) return unavailable();
        String month = YearMonth.from(time.today(tenantId)).toString();
        if (!usage.reserve(tenantId, month, limit))
            return null; // Quiet at quota: no repeated paid fallback messages.
        jdbc.update("update whatsapp_assistant_session set expires_at=? where phone=?", Timestamp.from(now.plusSeconds(1800)), phone);
        int points = loyalty.findByTenant_IdAndCustomer_Id(tenantId, customer.getId())
                .map(LoyaltyAccount::getPuntosDisponibles).orElse(0);
        String direct = directQuery(text);
        if ("POINTS".equals(direct))
            return "Tienes " + points + " puntos disponibles en " + customer.getTenant().getNombre() + ".";
        List<Promotion> eligible = promotions.findActiveClientPromotions(tenantId, points);
        if ("PROMOTIONS".equals(direct)) return promotionReply(eligible);
        BookingBootstrapResponse catalog = bookings.getBootstrap(tenantId);
        if (text.toUpperCase(Locale.ROOT).startsWith("CONFIRMAR ")) {
            String token = Objects.toString(session.get("confirmation_code"), "");
            if (token.isBlank() || !text.equalsIgnoreCase("CONFIRMAR " + token) || session.get("draft") == null)
                return "No hay una confirmacion vigente con ese codigo. Pideme el horario de nuevo.";
            if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled())) return depositMessage();
            CreateAppointmentRequest request = mapper.readValue(session.get("draft").toString(), CreateAppointmentRequest.class);
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
            CreateAppointmentResponse result = bookings.createAppointment(tenantId, customer.getId(), request);
            clearDraft(phone);
            return "Reserva registrada #" + result.getAppointmentId() + ". Estado: " + result.getEstado()
                    + ". Fecha " + request.getDate() + " a las " + request.getHoraInicio() + ". Importe: " + catalog.getCurrency() + " " + current + ".";
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
        if (session.get("draft") != null) context.set("draft", mapper.readTree(session.get("draft").toString()));
        if (context.toString().length() > 24000) return "Para este catalogo, realiza tu reserva desde la app de Super Gods.";
        JsonNode intent = interpreter.interpret(text, context.toString());
        return switch (intent.path("intent").asText()) {
            case "POINTS" -> "Tienes " + points + " puntos disponibles en " + catalog.getTenantName() + ".";
            case "PROMOTIONS" -> promotionReply(eligible);
            case "BOOKING" -> prepare(phone, tenantId, intent, catalog, eligible);
            default -> "Puedo consultar tus puntos, mostrar promociones y ayudarte a reservar. Dime servicio, sede, dia y hora. Para cerrar escribe SALIR.";
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
        boolean ready = enabled && interpreter.configured() && allowed(tenantId) && limit > 0 && active;
        int used = ready ? usage.used(tenantId, month) : 0;
        return Map.of("enabled", ready, "plan", sub.getPlan(), "monthlyTurns", limit,
                "usedTurns", used, "remainingTurns", Math.max(0, limit - used), "month", month);
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

    private String prepare(String phone, Long tenantId, JsonNode intent, BookingBootstrapResponse catalog, List<Promotion> eligible) throws Exception {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setBranchId(number(intent, "branchId"));
        request.setServiceId(number(intent, "serviceId"));
        request.setBarberId(number(intent, "barberId"));
        request.setPromotionId(number(intent, "promotionId"));
        request.setDate(intent.path("date").isTextual() ? intent.path("date").asText() : null);
        request.setHoraInicio(intent.path("time").isTextual() ? intent.path("time").asText() : null);
        if (request.getBranchId() == null && catalog.getBranches().size() == 1) request.setBranchId(catalog.getBranches().get(0).getId());
        request.setDepositRequired(false);
        // Every changed selection invalidates the old confirmation, including incomplete drafts.
        jdbc.update("update whatsapp_assistant_session set draft=?,confirmation_code=null,quote=null where phone=?", mapper.writeValueAsString(request), phone);
        if (Boolean.TRUE.equals(catalog.getBookingDepositEnabled())) return depositMessage();
        if (!validRequest(request, catalog, eligible)) return "Indica un servicio y sede del negocio, y una fecha como 2026-11-02. Puedes preguntar por promociones.";
        if (request.getHoraInicio() == null || !request.getHoraInicio().matches("([01][0-9]|2[0-3]):[0-5][0-9]")) {
            var slots = bookings.getAvailability(tenantId, request.getBranchId(), request.getServiceId(), request.getDate(), request.getBarberId()).getSlots();
            return "Horarios disponibles para " + request.getDate() + ": " + String.join(", ", slots.stream().limit(12).toList()) + ". Dime cual prefieres.";
        }
        if (!available(tenantId, request)) return "Ese horario no esta disponible. Indica otra hora o pregunta por horarios.";
        BigDecimal quote = bookings.quoteAssistantAppointment(tenantId, request);
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("update whatsapp_assistant_session set confirmation_code=?,quote=? where phone=?", token, quote, phone);
        String service = catalog.getServices().stream().filter(s -> s.getId().equals(request.getServiceId())).findFirst().orElseThrow().getNombre();
        String branch = catalog.getBranches().stream().filter(b -> b.getId().equals(request.getBranchId())).findFirst().orElseThrow().getNombre();
        String barber = request.getBarberId() == null ? "cualquier profesional disponible" : catalog.getBarbers().stream()
                .filter(b -> b.getId().equals(request.getBarberId())).findFirst().orElseThrow().getNombre();
        String promotion = request.getPromotionId() == null ? "" : ". Promocion: " + eligible.stream()
                .filter(p -> p.getId().equals(request.getPromotionId())).findFirst().orElseThrow().getTitulo();
        return service + " en " + branch + ", con " + barber + ", " + request.getDate() + " a las " + request.getHoraInicio()
                + promotion + ". Total: " + catalog.getCurrency() + " " + quote + ". Para reservar responde CONFIRMAR " + token
                + ". Sujeto a disponibilidad al confirmar.";
    }

    private boolean validRequest(CreateAppointmentRequest r, BookingBootstrapResponse c, List<Promotion> eligible) {
        if (r.getDate() == null || r.getBranchId() == null || r.getServiceId() == null) return false;
        try { LocalDate.parse(r.getDate()); } catch (RuntimeException e) { return false; }
        return c.getBranches().stream().anyMatch(b -> b.getId().equals(r.getBranchId()))
                && c.getServices().stream().anyMatch(s -> s.getId().equals(r.getServiceId()) && !Boolean.TRUE.equals(s.getPrecioVariable()))
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
    private String promotionReply(List<Promotion> eligible) {
        if (eligible.isEmpty()) return "No encontre promociones vigentes disponibles para tu cuenta.";
        return "Promociones disponibles:\n" + String.join("\n", eligible.stream().limit(8)
                .map(p -> p.getTitulo() + (p.getPriceText() == null ? "" : " - " + p.getPriceText())
                        + (p.getDescripcion() == null ? "" : ": " + p.getDescripcion().substring(0, Math.min(120, p.getDescripcion().length())))).toList())
                + "\nDime cual te interesa, servicio, sede y fecha. Validare las condiciones antes de reservar.";
    }
}
