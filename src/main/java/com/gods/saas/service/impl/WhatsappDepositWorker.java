package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Durable background work keeps media transfers and notification sends out of the webhook. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappDepositWorker {
    private final JdbcTemplate jdbc;
    private final WhatsappDepositService deposits;
    private final TwilioDepositMediaDownloader downloader;
    private final CloudinaryStorageService storage;
    private final TwilioDepositNoticeSender sender;

    @Scheduled(fixedDelayString="${whatsapp.assistant.deposit-poll-ms:5000}")
    public void poll() {
        if (!deposits.supported()) return;
        try {recoverInterrupted();deposits.expireDue();processMedia();processNotices();}
        catch (Exception failure) {log.warn("WhatsApp deposit worker failed: {}",WhatsappAssistantFailureDiagnostics.summarize(failure));}
    }
    void recoverInterrupted() {
        var cutoff=java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600));
        for (var row:jdbc.queryForList("select message_sid from whatsapp_assistant_deposit_media where state='PROCESSING' and processing_at<?",cutoff)) {
            String sid=row.get("message_sid").toString();
            if (jdbc.update("update whatsapp_assistant_deposit_media set state='RECOVERING' where message_sid=? and state='PROCESSING' and processing_at<?",sid,cutoff)==1)
                deposits.mediaFailed(sid,"PROCESSING_TIMEOUT");
        }
        jdbc.update("update whatsapp_assistant_deposit_notice set state='UNKNOWN',error_code='PROCESSING_INTERRUPTED' where state='PROCESSING' and processing_at<?",cutoff);
    }    void processMedia() {
        for (var row:jdbc.queryForList("select * from whatsapp_assistant_deposit_media where state='PENDING' order by created_at limit 3")) {
            String sid=row.get("message_sid").toString();
            if (jdbc.update("update whatsapp_assistant_deposit_media set state='PROCESSING',processing_at=now() where message_sid=? and state='PENDING'",sid)!=1) continue;
            CloudinaryStorageService.UploadResult upload=null;
            try {
                var payment=jdbc.queryForMap("select * from whatsapp_assistant_deposit where appointment_id=?",row.get("appointment_id"));
                var image=downloader.download(row.get("media_url").toString(),sid,row.get("content_type").toString());
                upload=storage.uploadAppointmentDepositEvidence(WhatsappDepositService.id(payment,"tenant_id"),WhatsappDepositService.id(payment,"customer_id"),image);
                deposits.attach(sid,upload.getSecureUrl());
            } catch (Exception failure) {
                if (upload!=null) try {storage.deleteImage(upload.getPublicId());} catch (Exception cleanup) {log.warn("WhatsApp deposit orphan cleanup failed: {}",cleanup.getClass().getSimpleName());}
                deposits.mediaFailed(sid,failure.getClass().getSimpleName());
                log.warn("WhatsApp deposit media failed: {}",WhatsappAssistantFailureDiagnostics.summarize(failure));
            }
        }
    }
    void processNotices() {
        for (var row:jdbc.queryForList("select n.*,p.phone,p.tenant_id,p.customer_id,p.last_inbound_at,p.state as payment_state from whatsapp_assistant_deposit_notice n join whatsapp_assistant_deposit p on p.appointment_id=n.appointment_id where n.state='PENDING' order by n.created_at limit 10")) {
            String key=row.get("event_key").toString();
            if (jdbc.update("update whatsapp_assistant_deposit_notice set state='PROCESSING',processing_at=now() where event_key=? and state='PENDING'",key)!=1) continue;
            try {
                if (obsoleteNotice(key,row.get("payment_state").toString())) {
                    jdbc.update("update whatsapp_assistant_deposit_notice set state='SKIPPED',error_code='OBSOLETE_STATE' where event_key=?",key);continue;
                }
                if (deposits.verifiedCustomer(WhatsappDepositService.id(row,"tenant_id"),WhatsappDepositService.id(row,"customer_id"),row.get("phone").toString())==null) {
                    jdbc.update("update whatsapp_assistant_deposit_notice set state='SKIPPED',error_code='CUSTOMER_NOT_VERIFIED' where event_key=?",key);continue;
                }
                String sid=sender.send(row.get("phone").toString(),row.get("body").toString(),WhatsappDepositService.instant(row.get("last_inbound_at")));
                jdbc.update("update whatsapp_assistant_deposit_notice set state='SENT',provider_sid=? where event_key=?",sid,key);
            } catch (Exception failure) {
                // An HTTP timeout can mean the provider accepted the message. Do not blindly resend.
                                String code=failure instanceof IllegalStateException && java.util.Set.of("TWILIO_NOT_CONFIGURED","DEPOSIT_TEMPLATE_REQUIRED","INVALID_TWILIO_RESPONSE").contains(java.util.Objects.toString(failure.getMessage(),""))
                        ?failure.getMessage():failure.getClass().getSimpleName();
                jdbc.update("update whatsapp_assistant_deposit_notice set state='FAILED',error_code=? where event_key=?",code,key);
                log.warn("WhatsApp deposit notice failed: {}",WhatsappAssistantFailureDiagnostics.summarize(failure));
            }
        }
    }
    private boolean obsoleteNotice(String key,String state) {
        if (key.contains(":REFERENCE:")) return !"AWAITING_REFERENCE".equals(state);
        if (key.contains(":READY:")) return !"PENDING_VALIDATION".equals(state);
        if (key.contains(":PAID:")) return !"PAID".equals(state);
        if (key.contains(":REJECTED:")) return !"REJECTED".equals(state);
        if (key.contains(":EXPIRED:")) return !"EXPIRED".equals(state);
        if (key.contains(":MEDIA_ERROR:")) return java.util.Set.of("PAID","EXPIRED","PENDING_VALIDATION").contains(state);
        return false;
    }
}
