package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.CreateAppointmentRequest;
import com.gods.saas.domain.dto.response.BookingBootstrapResponse;
import com.gods.saas.domain.model.*;
import com.gods.saas.domain.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WhatsappDepositService {
    private final JdbcTemplate jdbc;
    private final ClientBookingService bookings;
    private final AppointmentRepository appointments;
    private final CustomerRepository customers;
    private final TenantPaymentMethodRepository methods;
    private final TenantTimeService time;
    private final TwilioDepositMediaDownloader downloader;
    @PersistenceContext private EntityManager entityManager;
    @Value("${whatsapp.assistant.deposits-enabled:false}") private boolean enabled;
    @Value("${whatsapp.assistant.deposit-hold-minutes:15}") private int holdMinutes=15;
    public boolean supported(){return enabled;}
    public record Offer(Long methodId,BigDecimal amount,String details) {}

    public Offer offer(Long tenantId,Long branchId,BigDecimal total,BookingBootstrapResponse catalog) {
        if (!enabled || total==null || total.signum()<=0) return null;
        List<TenantPaymentMethod> eligible=methods.findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId).stream()
                .filter(m -> "YAPE".equalsIgnoreCase(m.getCode()))
                .filter(m -> m.getBranch()==null || m.getBranch().getId().equals(branchId))
                .sorted(Comparator.comparing(m -> m.getBranch()==null?1:0)).toList();
        if (eligible.isEmpty()) return null;
        TenantPaymentMethod method=eligible.get(0);
        if ((method.getAccountValue()==null || method.getAccountValue().isBlank()) && (method.getQrImageUrl()==null || method.getQrImageUrl().isBlank())) return null;
        BigDecimal amount=catalog.getBookingDepositDefaultPercent()!=null
                ? total.multiply(BigDecimal.valueOf(catalog.getBookingDepositDefaultPercent())).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP)
                : catalog.getBookingDepositDefaultAmount();
        if (amount==null || amount.signum()<=0) return null;
        amount=amount.min(total).setScale(2,RoundingMode.HALF_UP);
        String details="Adelanto: "+catalog.getCurrency()+" "+amount+" por Yape."
                +line(method.getAccountLabel())+line(method.getAccountValue())+line(method.getInstructions())
                +(method.getQrImageUrl()==null?"":"\nQR: "+method.getQrImageUrl());
        return new Offer(method.getId(),amount,details);
    }
    private String line(String value){return value==null || value.isBlank()?"":"\n"+value.trim();}
    private int minutes(){return Math.max(5,Math.min(60,holdMinutes));}

    @Transactional
    public String begin(Long tenantId,Long customerId,String phone,CreateAppointmentRequest request,BigDecimal quote,BookingBootstrapResponse catalog) {
        if (!enabled) throw new IllegalStateException("DEPOSITS_DISABLED");
        var open=jdbc.queryForList("select appointment_id from whatsapp_assistant_deposit where phone=? and state in ('AWAITING_EVIDENCE','AWAITING_REFERENCE','PENDING_VALIDATION','REJECTED')",phone);
        if (!open.isEmpty()) return "Ya tienes un adelanto pendiente para la reserva #"+open.get(0).get("appointment_id")+". Escribe ESTADO para consultarlo. No realices otro pago.";
        Offer offer=offer(tenantId,request.getBranchId(),quote,catalog);
        if (offer==null) return "No encontre datos de Yape validos para esta sede. Contacta al negocio o completa la reserva en la app.";
        if (request.getDepositAmount()!=null && request.getDepositAmount().compareTo(offer.amount())!=0) return "El adelanto configurado cambio. Consulta nuevamente la reserva antes de pagar.";
        TenantPaymentMethod method=methods.findByIdAndTenant_IdAndActiveTrue(offer.methodId(),tenantId).orElseThrow();
        request.setDepositRequired(true);request.setDepositAmount(offer.amount());request.setDepositPaymentMethodId(offer.methodId());
        var result=bookings.createWhatsappDepositHold(tenantId,customerId,request);
        Appointment appointment=appointments.findByIdAndTenant_IdAndCustomer_Id(result.getAppointmentId(),tenantId,customerId).orElseThrow();
        if (appointment.getTotalAmount()==null || appointment.getTotalAmount().compareTo(quote)!=0) throw new IllegalStateException("PRICE_CHANGED");
        Instant deadline=Instant.now().plusSeconds(minutes()*60L);
        Instant start=appointment.getFecha().atTime(appointment.getHoraInicio()).atZone(time.getZone(tenantId)).toInstant();
        if (start.isBefore(deadline)) deadline=start;
        jdbc.update("insert into whatsapp_assistant_deposit(appointment_id,tenant_id,customer_id,phone,state,expires_at,last_inbound_at,requires_reference,instructions) values (?,?,?,?,'AWAITING_EVIDENCE',?,?,?,?)",
                appointment.getId(),tenantId,customerId,phone,Timestamp.from(deadline),Timestamp.from(Instant.now()),Boolean.TRUE.equals(method.getRequiresOperationCode()),offer.details());
        return "Horario apartado para la reserva #"+appointment.getId()+" hasta "+deadline.atZone(time.getZone(tenantId)).toLocalTime().withSecond(0).withNano(0)
                +". Aun no esta confirmada.\n"+offer.details()
                +(Boolean.TRUE.equals(method.getRequiresOperationCode())
                    ?"\n\nPaso 1: realiza el adelanto por Yape.\nPaso 2: envia primero el numero de operacion en un mensaje, por ejemplo: OPERACION 123456 (usa el numero real de tu pago).\nCuando lo registremos, te pediremos la foto del comprobante para completar el paso 3."
                    :"\n\nPaso 1: realiza el adelanto por Yape.\nPaso 2: adjunta la captura del comprobante como foto en este chat.")
                +"\nEl dueño revisara el comprobante antes de confirmar tu reserva. No vuelvas a pagar si ya realizaste el adelanto.";
    }

    /** Called only after signature, destination and MessageSid validation. */
    @Transactional
    public String inbound(String phone,String text,String sid,Map<String,String> media,Set<Long> allowedTenants) {
        if (!enabled) return null;
        boolean hasMedia=media!=null && media.containsKey("MediaUrl0");
        String clean=Objects.toString(text,"").trim();
        String reference=reference(clean);
        boolean status=Set.of("ESTADO","MI ADELANTO","ESTADO DE MI RESERVA").contains(clean.toUpperCase(Locale.ROOT));
        if (!hasMedia && reference==null && !status) return null;
        var rows=jdbc.queryForList("select * from whatsapp_assistant_deposit where phone=? order by created_at desc limit 1 for update",phone);
        if (rows.isEmpty()) return hasMedia?"Primero elige y confirma el resumen de una reserva con adelanto. No puedo vincular esta imagen a una cita.":null;
        var row=rows.get(0);Long tenantId=id(row,"tenant_id"),customerId=id(row,"customer_id"),appointmentId=id(row,"appointment_id");
        if (!allowedTenants.contains(tenantId) || verifiedCustomer(tenantId,customerId,phone)==null) return "No puedo vincular el comprobante a tu cuenta verificada. Contacta al negocio.";
        jdbc.update("update whatsapp_assistant_deposit set last_inbound_at=? where appointment_id=?",Timestamp.from(Instant.now()),appointmentId);
        String state=row.get("state").toString();
        if (!hasMedia && !status && Set.of("PAID","EXPIRED").contains(state) && !explicitReference(clean)) return null;
        if ("PAID".equals(state)) return "Tu adelanto ya fue aprobado y la reserva #"+appointmentId+" esta confirmada. No necesitas otro pago.";
        if ("EXPIRED".equals(state)) return "El plazo de la reserva #"+appointmentId+" termino. Si ya pagaste, contacta al negocio para revisar tu adelanto; no pagues otra vez.";
        Appointment appointment=appointments.findByIdAndTenant_IdAndCustomer_Id(appointmentId,tenantId,customerId).orElseThrow();
        if (Set.of("CANCELADO","CANCELLED","ATENDIDO","COMPLETADO","FINALIZADO").contains(Objects.toString(appointment.getEstado(),"").toUpperCase(Locale.ROOT))) return "La reserva ya no admite comprobantes. Contacta al negocio si realizaste un pago.";
        if (status) return "Reserva #"+appointmentId+": "+human(state)+". "+("AWAITING_EVIDENCE".equals(state)?row.get("instructions"):"No necesitas volver a pagar.");
        if ("PENDING_VALIDATION".equals(state)) return "Tu comprobante ya esta en la agenda para revision. Te avisaremos cuando el dueño lo valide.";
        if (Set.of("AWAITING_EVIDENCE","REJECTED").contains(state) && instant(row.get("expires_at")).isBefore(Instant.now())) return "El plazo para enviar el comprobante termino. Escribe ESTADO o contacta al negocio si ya pagaste.";
        if (reference!=null) {
            appointment.setDepositOperationCode(reference);
            jdbc.update("update whatsapp_assistant_deposit set reference_supplied=true where appointment_id=?",appointmentId);
        }
        if (hasMedia) {
            String type=media.get("MediaContentType0"),url=media.get("MediaUrl0");
            if (!Set.of("image/jpeg","image/png","image/webp").contains(Objects.toString(type,"")) || !downloader.validSource(url,sid)) return "Envia el comprobante como una foto JPG, PNG o WEBP, de hasta 5 MB.";
            Integer pending=jdbc.queryForObject("select count(*) from whatsapp_assistant_deposit_media where appointment_id=? and state in ('PENDING','PROCESSING')",Integer.class,appointmentId);
            if (pending!=null && pending>0) return "Tu captura anterior todavia se esta procesando. Espera la confirmacion antes de enviar otra.";
            jdbc.update("insert into whatsapp_assistant_deposit_media(message_sid,appointment_id,media_url,content_type) values (?,?,?,?) on conflict do nothing",sid,appointmentId,url,type);
            jdbc.update("update whatsapp_assistant_deposit set revision=revision+1 where appointment_id=?",appointmentId);
            appointments.save(appointment);
            return "Recibi tu captura para la reserva #"+appointmentId+". La estamos adjuntando; te avisaremos cuando quede pendiente de revision. Esto aun no confirma el pago.";
        }
        appointments.save(appointment);
        if ("AWAITING_REFERENCE".equals(state) && appointment.getDepositEvidenceUrl()!=null && !appointment.getDepositEvidenceUrl().isBlank()) {
            ready(row,appointment); return "Numero de operacion registrado. El comprobante ya esta pendiente de revision del dueño.";
        }
        return "Numero de operacion registrado para la reserva #"+appointmentId+".\nAhora completa el paso final: adjunta la captura del comprobante de Yape como foto en este chat. Espera el aviso de que quedo adjuntada a la agenda. La reserva se confirmara cuando el dueño apruebe el adelanto. No vuelvas a pagar.";
    }
    public Customer verifiedCustomer(Long tenantId,Long customerId,String phone) {
        return customers.findByIdAndTenantIdWithTenant(customerId,tenantId)
                .filter(Customer::isPhoneVerified).filter(c -> c.getTenant()!=null && !Boolean.FALSE.equals(c.getTenant().getActive())).filter(c -> phone.equals(c.getTelefono()))
                .filter(c -> c.getWhatsappOptedOutAt()==null && Boolean.TRUE.equals(c.getWhatsappTransactionalEnabled())).orElse(null);
    }
    static boolean explicitReference(String text) { return text.matches("(?i)^(OPERACION|OPERACIÓN|REFERENCIA|CODIGO|CÓDIGO)[ :#-].*"); }
    static String reference(String text) {
        if (!explicitReference(text) && !text.matches("[0-9]{4,20}")) return null;
        String value=text.replaceFirst("(?i)^(OPERACION|OPERACIÓN|REFERENCIA|CODIGO|CÓDIGO)\\s*[:#-]?\\s*","").trim();
        return value.matches("(?=.*[0-9])[A-Za-z0-9-]{4,40}")?value:null;
    }
    @Transactional
    public void attach(String sid,String evidenceUrl) {
        var job=jdbc.queryForMap("select * from whatsapp_assistant_deposit_media where message_sid=? for update",sid);
        if (!"PROCESSING".equals(job.get("state"))) throw new IllegalStateException("MEDIA_NOT_PROCESSING");
        Long id=id(job,"appointment_id");var row=jdbc.queryForMap("select * from whatsapp_assistant_deposit where appointment_id=? for update",id);
        if (!Set.of("AWAITING_EVIDENCE","AWAITING_REFERENCE","REJECTED").contains(row.get("state").toString())) throw new IllegalStateException("DEPOSIT_NOT_OPEN");
        Appointment appointment=appointments.findByIdAndTenant_IdAndCustomer_Id(id,id(row,"tenant_id"),id(row,"customer_id")).orElseThrow();
        if (verifiedCustomer(id(row,"tenant_id"),id(row,"customer_id"),row.get("phone").toString())==null) throw new IllegalStateException("CUSTOMER_NOT_VERIFIED");
        if (Set.of("CANCELADO","CANCELLED","ATENDIDO","COMPLETADO","FINALIZADO").contains(Objects.toString(appointment.getEstado(),"").toUpperCase(Locale.ROOT))) throw new IllegalStateException("APPOINTMENT_CLOSED");
        if (!Boolean.TRUE.equals(appointment.getDepositRequired()) || "PAID".equals(appointment.getDepositStatus())) throw new IllegalStateException("DEPOSIT_ALREADY_RESOLVED");
        appointment.setDepositEvidenceUrl(evidenceUrl);appointment.setDepositPaidAt(time.now(id(row,"tenant_id")));
        appointment.setEstado("PENDING_DEPOSIT_VALIDATION");
        appointment.setDepositValidatedAt(null);appointment.setDepositValidatedByUserId(null);
        if (Boolean.TRUE.equals(row.get("requires_reference")) && (!Boolean.TRUE.equals(row.get("reference_supplied")) || appointment.getDepositOperationCode()==null || appointment.getDepositOperationCode().isBlank())) {
            appointment.setDepositStatus("AWAITING_REFERENCE");
            jdbc.update("update whatsapp_assistant_deposit set state='AWAITING_REFERENCE' where appointment_id=?",id);
            notice(row,"REFERENCE","Captura adjuntada a la reserva #"+id+". Falta el numero de operacion: envialo como OPERACION 123456. No vuelvas a pagar.");
        } else ready(row,appointment);
        appointments.save(appointment);
        jdbc.update("update whatsapp_assistant_deposit_media set state='DONE',stored_url=?,operation_reference=? where message_sid=?",evidenceUrl,appointment.getDepositOperationCode(),sid);
    }
    private void ready(Map<String,Object> row,Appointment appointment) {
        appointment.setDepositStatus("PENDING_VALIDATION");appointment.setEstado("PENDING_DEPOSIT_VALIDATION");
        jdbc.update("update whatsapp_assistant_deposit set state='PENDING_VALIDATION' where appointment_id=?",appointment.getId());
        notice(row,"READY","Comprobante de la reserva #"+appointment.getId()+" adjuntado a la agenda. Pendiente de aprobacion del dueño; te avisaremos cuando lo revise.");
    }
    @Transactional
    public void beforeDecision(Appointment appointment) {
        beforeDecision(appointment,true,null);
    }
    @Transactional
    public void beforeDecision(Appointment appointment,boolean approved,String operationCode) {
        if (!enabled) return;
        var rows=jdbc.queryForList("select * from whatsapp_assistant_deposit where appointment_id=? for update",appointment.getId());
        if (rows.isEmpty()) return;
        var row=rows.get(0);
        entityManager.refresh(appointment);
        if (!Set.of("PENDING_VALIDATION","AWAITING_REFERENCE").contains(row.get("state").toString())
                || appointment.getDepositEvidenceUrl()==null || appointment.getDepositEvidenceUrl().isBlank())
            throw new IllegalStateException("El comprobante de WhatsApp todavia no esta listo para validar o la reserva ya fue resuelta");
        if (Set.of("CANCELADO","CANCELLED","ATENDIDO","COMPLETADO","FINALIZADO").contains(Objects.toString(appointment.getEstado(),"").toUpperCase(Locale.ROOT)))
            throw new IllegalStateException("La cita ya no admite aprobacion de adelanto");
        String supplied=operationCode==null?null:operationCode.trim();
        if (supplied!=null && !supplied.isBlank()) {
            String parsed=reference("OPERACION "+supplied);
            if (parsed==null) throw new IllegalArgumentException("El numero de operacion debe tener entre 4 y 40 caracteres e incluir digitos");
            appointment.setDepositOperationCode(parsed);
            jdbc.update("update whatsapp_assistant_deposit set reference_supplied=true where appointment_id=?",appointment.getId());
        }
        boolean hasReference=Boolean.TRUE.equals(row.get("reference_supplied")) || supplied!=null&&!supplied.isBlank();
        if (approved && Boolean.TRUE.equals(row.get("requires_reference"))
                && (!hasReference || appointment.getDepositOperationCode()==null || appointment.getDepositOperationCode().isBlank()))
            throw new IllegalStateException("Falta el numero de operacion. Registralo desde el comprobante antes de aprobar el adelanto");
    }
    @Transactional
    public void decision(Appointment appointment,boolean approved) {
        if (!enabled) return;
        var rows=jdbc.queryForList("select * from whatsapp_assistant_deposit where appointment_id=? for update",appointment.getId());
        if (rows.isEmpty()) return;
        var row=rows.get(0);
        String state=approved?"PAID":"REJECTED";
        if (!approved) jdbc.update("update whatsapp_assistant_deposit set reference_supplied=false where appointment_id=?",appointment.getId());
        jdbc.update("update whatsapp_assistant_deposit set state=?,expires_at=? where appointment_id=?",state,Timestamp.from(Instant.now().plusSeconds(minutes()*60L)),appointment.getId());
        String note=approved?"Adelanto aprobado. Tu reserva #"+appointment.getId()+" esta confirmada para "+appointment.getFecha()+" a las "+appointment.getHoraInicio()+".":
                "El dueño no pudo validar el adelanto de la reserva #"+appointment.getId()+". Motivo: "+Objects.toString(appointment.getDepositNote(),"Contacta al negocio para revisar el pago")+". Puedes enviar una nueva captura. No realices otro pago sin revisar el anterior con el negocio.";
        notice(row,state,note);
    }
    @Transactional
    public void expireDue() {
        if (!enabled) return;
        var rows=jdbc.queryForList("select * from whatsapp_assistant_deposit p where state in ('AWAITING_EVIDENCE','REJECTED') and expires_at<now() and not exists (select 1 from whatsapp_assistant_deposit_media m where m.appointment_id=p.appointment_id and m.state in ('PENDING','PROCESSING')) limit 100 for update skip locked");
        for (var row:rows) {
            var appointment=appointments.findByIdAndTenant_Id(id(row,"appointment_id"),id(row,"tenant_id")).orElseThrow();
            if (!"PAID".equals(appointment.getDepositStatus())) {
                appointment.setEstado("CANCELADO");appointment.setDepositStatus("EXPIRED");appointments.save(appointment);
                jdbc.update("update whatsapp_assistant_deposit set state='EXPIRED' where appointment_id=?",appointment.getId());
                notice(row,"EXPIRED","El plazo para enviar el comprobante de la reserva #"+appointment.getId()+" termino y se libero el horario. Si ya pagaste, contacta al negocio; no vuelvas a pagar.");
            }
        }
    }
    @Transactional
    public void mediaFailed(String sid,String reason) {
        jdbc.update("update whatsapp_assistant_deposit_media set state='FAILED',error_code=? where message_sid=?",reason,sid);
        var row=jdbc.queryForMap("select p.* from whatsapp_assistant_deposit p join whatsapp_assistant_deposit_media m on m.appointment_id=p.appointment_id where m.message_sid=?",sid);
        notice(row,"MEDIA_ERROR","No pude adjuntar la captura de la reserva #"+row.get("appointment_id")+". Reenviala como foto JPG, PNG o WEBP de hasta 5 MB. Si ya pagaste, no repitas el pago.");
    }    void notice(Map<String,Object> row,String kind,String body) {
        String key=row.get("appointment_id")+":"+kind+":"+row.get("revision");
        jdbc.update("insert into whatsapp_assistant_deposit_notice(event_key,appointment_id,body) values (?,?,?) on conflict do nothing",key,id(row,"appointment_id"),body);
    }
    static Long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    static Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((OffsetDateTime)value).toInstant();}
    private String human(String state){return switch(state){case "AWAITING_EVIDENCE" -> "esperando captura del adelanto";case "AWAITING_REFERENCE" -> "captura recibida, falta numero de operacion";case "PENDING_VALIDATION" -> "comprobante pendiente de revision";case "REJECTED" -> "comprobante rechazado; puedes enviar otro";default -> state;};}
}
