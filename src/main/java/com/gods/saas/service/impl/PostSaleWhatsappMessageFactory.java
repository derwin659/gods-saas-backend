package com.gods.saas.service.impl;

import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

/** A frozen, shared message contract for manual links and approved WhatsApp templates. */
@Service @RequiredArgsConstructor
public class PostSaleWhatsappMessageFactory {
    public static final String FULL_VARIANT = "points_app_code_booking_assistant";
    private final TenantSettingsRepository settings;
    private final LoyaltyMovementRepository movements;
    private final LoyaltyAccountRepository accounts;
    private final PostSaleWhatsappAssistantInvitation invitation;
    @Value("${app.mobile.download-url:https://play.google.com/store/apps/details?id=com.gods.barberia}")
    private String appUrl;
    @Value("${app.public.base-url:https://www.supergodsapp.com}")
    private String baseUrl;

    public record Snapshot(String message, String template, Map<String,String> variables, String variant, boolean marketing) {
        public Map<String,Object> stored() {
            return Map.of("version",2,"message",message,"template",template,"variables",variables,"variant",variant,"marketing",marketing);
        }
    }
    public Snapshot forSale(Sale sale, int earnedFallback) {
        if (sale.getWhatsappReceiptSnapshot() != null) return restore(sale.getWhatsappReceiptSnapshot());
        Map<String,Object> config = settings.findByTenant_Id(sale.getTenant().getId()).map(TenantSettings::getScheduleConfig).orElse(Map.of());
        var movement = movements.findFirstByTenantIdAndCustomerIdAndOrigenAndReferenciaIdAndTipoOrderByIdAsc(
            sale.getTenant().getId(),sale.getCustomer().getId(),"SALE",sale.getId(),"EARN").orElse(null);
        int earned = movement == null ? Math.max(0,earnedFallback) : safe(movement.getPuntos());
        int after = movement != null && movement.getSaldoResultante() != null ? movement.getSaldoResultante() : accounts
            .findByTenant_IdAndCustomer_Id(sale.getTenant().getId(),sale.getCustomer().getId()).map(LoyaltyAccount::getPuntosDisponibles).map(this::safe).orElse(0);
        int before = after - earned;
        String customer = clean(sale.getCustomer().getNombres(),clean(sale.getCustomer().getApellidos(),"cliente")).split("\\s+")[0];
        String tenant = clean(sale.getTenant().getNombre(),"Super Gods");
        String code = clean(sale.getTenant().getCodigo(),"");
        boolean app = enabled(config,OwnerWhatsappSettingsService.INCLUDE_APP_DOWNLOAD_LINK_KEY);
        boolean booking = enabled(config,OwnerWhatsappSettingsService.INCLUDE_BOOKING_LINK_KEY) && !code.isBlank();
        String assistant = "";
        if (enabled(config,OwnerWhatsappSettingsService.INCLUDE_BOOKING_LINK_KEY)) {
            try { assistant = Objects.toString(invitation.build(sale.getTenant().getId()),""); }
            catch (RuntimeException unavailable) { /* Optional assistant cannot fail a sale. */ }
        }
        var branch=sale.getBranch();
        String google = branch != null && branch.getTenant() != null && Objects.equals(branch.getTenant().getId(),sale.getTenant().getId())
            && Boolean.TRUE.equals(branch.getActivo()) && Boolean.TRUE.equals(branch.getGoogleReviewWhatsappEnabled())
            ? Objects.toString(GoogleReviewLinks.safe(branch.getGoogleReviewUrl()),"") : "";
        return compose(customer,tenant,before,earned,after,
            app ? clean(Objects.toString(config == null ? null : config.get(OwnerWhatsappSettingsService.APP_DOWNLOAD_URL_KEY),null),clean(appUrl,"https://play.google.com/store/apps/details?id=com.gods.barberia")) : "",
            app ? code : "", booking ? clean(baseUrl,"https://www.supergodsapp.com").replaceAll("/+$","")+"/reservar/"+code : "", assistant, google);
    }
    public static Snapshot compose(String customer,String tenant,int before,int earned,int after,String app,String code,String booking,String assistant) {
        return compose(customer,tenant,before,earned,after,app,code,booking,assistant,"");
    }
    public static Snapshot compose(String customer,String tenant,int before,int earned,int after,String app,String code,String booking,String assistant,String google) {
        var b = new Builder();
        b.fixed("Hola ").value(customer).fixed(", gracias por tu visita a ").value(tenant).fixed(".");
        b.fixed("\n\nPuntos antes de esta compra: ").value(String.valueOf(before)).fixed(".");
        b.fixed("\nGanaste +").value(String.valueOf(earned)).fixed(" puntos por tu compra.");
        b.fixed("\nAhora tienes ").value(String.valueOf(after)).fixed(" puntos disponibles.");
        String variant="points";
        if (app != null && !app.isBlank()) {
            variant+="_app";
            b.fixed("\n\nDescarga la app movil de Super Gods para ver tus puntos, premios y reservas:\n").value(app);
            if (code != null && !code.isBlank()) {
                variant+="_code";
                b.fixed("\n\nPara ver tus puntos en la app:\n1. Ingresa como cliente.\n2. Coloca el codigo del negocio: ").value(code)
                 .fixed("\n3. Revisa tus puntos, premios y proximas reservas.");
            }
        }
        if (booking != null && !booking.isBlank()) {
            variant+="_booking";
            b.fixed("\n\nTambien puedes reservar tu proxima cita desde la app o por este link y seguir ganando puntos:\n").value(booking);
        }
        // Only the known assistant invitation shape is converted into template variables.
        var matcher=Pattern.compile("^\\s*Tambien puedes reservar por WhatsApp con nuestro asistente[.] Abre este enlace y envia: ([^\\r\\n]+)\\r?\\n(https://[^\\s]+)\\s*$").matcher(Objects.toString(assistant,""));
        if(matcher.matches()) {
            variant+="_assistant";
            b.fixed("\n\nTambien puedes reservar por WhatsApp con nuestro asistente. Abre este enlace y envia: ").value(matcher.group(1))
             .fixed("\n").value(matcher.group(2));
        }
        String googleUrl=GoogleReviewLinks.safe(google);
        if(googleUrl!=null) {
            variant+="_google";
            b.fixed("\n\nSi deseas compartir tu experiencia, puedes dejar una resena en Google:\n").value(googleUrl)
             .fixed("\nTu opinion es voluntaria.");
        }
        b.fixed("\n\nTe esperamos pronto.");
        return new Snapshot(b.message.toString(),b.template.toString(),Map.copyOf(b.vars),variant,!variant.equals("points"));
    }
    public static Snapshot restore(Map<String,Object> stored) {
        if (stored == null || !Integer.valueOf(2).equals(stored.get("version"))) throw new IllegalStateException("Falta la copia completa del mensaje postventa; no se envia una plantilla distinta.");
        var vars=new LinkedHashMap<String,String>();
        if (!(stored.get("variables") instanceof Map<?,?> raw)) throw new IllegalStateException("Mensaje postventa sin variables.");
        raw.forEach((key,value)->vars.put(String.valueOf(key),String.valueOf(value)));
        return new Snapshot(Objects.toString(stored.get("message"),""),Objects.toString(stored.get("template"),""),vars,
            Objects.toString(stored.get("variant"),""),Boolean.TRUE.equals(stored.get("marketing")));
    }
    public static boolean marketing(Notification n) {
        return n != null && n.getWhatsappTemplateSnapshot() != null && Boolean.TRUE.equals(n.getWhatsappTemplateSnapshot().get("marketing"));
    }
    private static class Builder {
        final StringBuilder message=new StringBuilder(),template=new StringBuilder();
        final Map<String,String> vars=new LinkedHashMap<>();
        Builder fixed(String text) {message.append(text);template.append(text);return this;}
        Builder value(String raw) {
            String value=Objects.toString(raw,"").replaceAll("[\\r\\n\\t]+"," ").replaceAll(" {2,}"," ").trim();
            if(value.isEmpty())throw new IllegalArgumentException("Variable postventa vacia");
            String key=String.valueOf(vars.size()+1);vars.put(key,value);message.append(value);template.append("{{").append(key).append("}}");return this;
        }
    }
    private int safe(Integer value) {return value == null ? 0 : value;}
    private static String clean(String text,String fallback) {return text == null || text.isBlank() ? fallback : text.trim();}
    private boolean enabled(Map<String,Object> config,String key) {
        if(config==null || !config.containsKey(key))return true;
        return Boolean.TRUE.equals(config.get(key)) || "true".equalsIgnoreCase(String.valueOf(config.get(key)));
    }
}
