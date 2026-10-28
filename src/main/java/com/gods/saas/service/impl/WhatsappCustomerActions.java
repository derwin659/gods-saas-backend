package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Identity and quota are checked by WhatsappAssistantService before entering this flow. */
@Service
@RequiredArgsConstructor
public class WhatsappCustomerActions {
    private final RewardItemRepository rewards;
    private final RewardRedemptionRepository redemptions;
    private final LoyaltyAccountRepository loyalty;
    private final AppointmentRepository appointments;
    private final ClientRewardService redemptionService;
    private final TenantTimeService time;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private String clean(String value) {
        return java.text.Normalizer.normalize(Objects.toString(value,""),java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }
    private int points(Long tenant,Long customer) {
        return loyalty.findByTenant_IdAndCustomer_Id(tenant,customer).map(LoyaltyAccount::getPuntosDisponibles).orElse(0);
    }
    private void save(String phone,ObjectNode root,ObjectNode meta) throws Exception {
        root.set("_assistant",meta);
        jdbc.update("update whatsapp_assistant_session set draft=?,confirmation_code=null,quote=null where phone=?",mapper.writeValueAsString(root),phone);
    }
    private void reset(String phone,ObjectNode root) throws Exception {
        save(phone,root,mapper.createObjectNode().put("stage","MENU").put("mode","BOOKING").put("lastPrompt",WhatsappAssistantMenu.TEXT));
    }
    public String reply(String phone,Long tenant,Long customer,String text,ObjectNode saved) throws Exception {
        ObjectNode root=saved.deepCopy();
        ObjectNode meta=root.path("_assistant") instanceof ObjectNode node?node.deepCopy():mapper.createObjectNode();
        String stage=meta.path("stage").asText();
        String input=clean(text);
        boolean menu=stage.equals("MENU");
        if (Set.of("mis canjes","ver mis canjes","mis premios canjeados","mis cupones","ver mis cupones").contains(input) || menu&&input.equals("8")) return myRedemptions(tenant,customer);
        if (Set.of("mis reservas","ver mis reservas","mis citas","mis proximas reservas","proximas citas").contains(input) || menu&&input.equals("9")) return myAppointments(tenant,customer);
        if (Set.of("premios","ver premios","canjear premios","ver y canjear premios","quiero canjear premios","quiero canjear puntos","canjear puntos","recompensas","ver recompensas").contains(input) || menu&&input.equals("7"))
            return list(phone,tenant,customer,root);
        if (stage.equals("REWARD_CONFIRM")) {
            if (Set.of("no","cancelar","no confirmo","no gracias").contains(input)) {
                reset(phone,root); return "Canje cancelado. No descontamos puntos. Escribe PREMIOS para verlos o MENU para otras opciones.";
            }
            if (Set.of("si","si confirmo","confirmo","confirmar canje","si canjear","canjear").contains(input)) {
                try {
                    var result=redemptionService.redeemForWhatsapp(tenant,customer,meta.path("rewardId").asLong(),meta.path("rewardPoints").asInt());
                    reset(phone,root);
                    return "Canje generado. Codigo: "+result.getCodigo()+".\nPuntos descontados: "+meta.path("rewardPoints").asInt()+". Saldo: "+result.getPuntosDisponibles()+" puntos.\nPresenta el codigo al personal del negocio para recibir tu premio. Todavia no figura como entregado. Escribe MIS CANJES para recuperarlo.";
                } catch (ClientRewardService.RewardUnavailableException failure) {
                    reset(phone,root);return failure.getMessage()+" No realizamos el canje. Escribe PREMIOS para consultar de nuevo.";
                }
            }
        }
        if (stage.equals("REWARD")) {
            JsonNode selected=null;
            if (input.matches("[0-9]{1,2}")) {
                int index=Integer.parseInt(input)-1;
                if (index>=0 && index<meta.path("options").size()) selected=meta.path("options").get(index);
            } else {
                for (JsonNode option:meta.path("options")) if (clean(option.path("label").asText()).equals(input)) {selected=option;break;}
            }
            if (selected!=null) {
                RewardItem reward=rewards.findByIdAndTenant_IdAndActivoTrue(selected.path("id").asLong(),tenant).orElse(null);
                if (reward==null || reward.getPuntosRequeridos()==null || reward.getPuntosRequeridos()<0 || reward.getStock()!=null&&reward.getStock()<=0)
                    return list(phone,tenant,customer,root);
                int balance=points(tenant,customer);
                if (balance<reward.getPuntosRequeridos()) return "Este premio requiere "+reward.getPuntosRequeridos()+" puntos y tienes "+balance+". Te faltan "+(reward.getPuntosRequeridos()-balance)+" puntos. Elige otro premio o escribe MENU.";
                String prompt="¿Confirmas el canje de "+reward.getNombre()+" por "+reward.getPuntosRequeridos()+" puntos?\nTu saldo es "+balance+" y quedaria en "+(balance-reward.getPuntosRequeridos())+". Responde SI o NO. Aun no hemos descontado puntos.";
                meta.remove("options");meta.put("stage","REWARD_CONFIRM").put("rewardId",reward.getId()).put("rewardPoints",reward.getPuntosRequeridos()).put("lastPrompt",prompt);
                save(phone,root,meta);return prompt;
            }
        }
        if (stage.startsWith("REWARD") && (input.matches("[0-9]{1,2}") || Set.of("si","no","confirmo","canjear").contains(input)))
            return meta.path("lastPrompt").asText("Escribe PREMIOS para consultar el catalogo.");
        return null;
    }
    private String list(String phone,Long tenant,Long customer,ObjectNode root) throws Exception {
        int balance=points(tenant,customer);
        var available=rewards.findByTenant_IdAndActivoTrueOrderByPuntosRequeridosAsc(tenant).stream()
                .filter(r->r.getPuntosRequeridos()!=null&&r.getPuntosRequeridos()>=0&&(r.getStock()==null||r.getStock()>0)).limit(10).toList();
        ObjectNode meta=mapper.createObjectNode().put("stage","REWARD");var options=meta.putArray("options");
        StringBuilder text=new StringBuilder("Tienes "+balance+" puntos.\n");
        if (available.isEmpty()) text.append("No hay premios activos con stock para canjear. Escribe MENU para otras opciones.");
        else {
            text.append("Premios del negocio:");
            for (RewardItem reward:available) {
                options.addObject().put("id",reward.getId()).put("label",reward.getNombre());
                text.append("\n").append(options.size()).append(". ").append(reward.getNombre()).append(" — ").append(reward.getPuntosRequeridos()).append(" puntos");
                if (balance<reward.getPuntosRequeridos()) text.append(" (te faltan ").append(reward.getPuntosRequeridos()-balance).append(")");
            }
            text.append("\nElige el numero o nombre del premio. Te pedire confirmar antes de descontar puntos.");
        }
        meta.put("lastPrompt",text.toString());save(phone,root,meta);return text.toString();
    }
    private String myRedemptions(Long tenant,Long customer) {
        var items=redemptions.findByTenantIdAndCustomerIdOrderByFechaCreacionDesc(tenant,customer).stream().limit(10).toList();
        if (items.isEmpty()) return "Aun no tienes canjes en este negocio. Escribe PREMIOS para ver que puedes canjear.";
        StringBuilder text=new StringBuilder("Tus ultimos canjes en este negocio:");
        for (RewardRedemption item:items) text.append("\n").append(item.getCodigo()).append(" — ").append(item.getPuntosUsados()).append(" puntos — ")
                .append("GENERATED".equalsIgnoreCase(item.getEstado())?"pendiente de entrega":"USED".equalsIgnoreCase(item.getEstado())?"entregado":item.getEstado());
        return text.append("\nPresenta un codigo pendiente al personal del negocio. Consultarlo no descuenta puntos otra vez.").toString();
    }
    private String myAppointments(Long tenant,Long customer) {
        var items=appointments.findUpcomingForCustomer(tenant,customer,time.today(tenant),org.springframework.data.domain.PageRequest.of(0,10));
        if (items.isEmpty()) return "No tienes proximas reservas en este negocio. Escribe RESERVAR para crear una.";
        StringBuilder text=new StringBuilder("Tus proximas reservas en este negocio:");
        for (Appointment item:items) {
            text.append("\n#").append(item.getId()).append(" — ").append(item.getFecha()).append(" ").append(item.getHoraInicio());
            if (item.getService()!=null) text.append(" — ").append(item.getService().getNombre());
            if (item.getUser()!=null) text.append(" — ").append(item.getUser().getNombre());
            text.append(" — Estado: ").append(item.getEstado());
            if (Boolean.TRUE.equals(item.getDepositRequired())) text.append("; adelanto: ").append(item.getDepositStatus());
        }
        return text.append("\nPara cancelar o reprogramar, abre tus reservas en la app o contacta al negocio.").toString();
    }
}
