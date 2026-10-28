package com.gods.saas.web.controller;

import com.gods.saas.service.impl.OwnerWhatsappPhoneVerificationService;
import com.gods.saas.service.impl.TwilioWebhookSignatureValidator;
import com.gods.saas.service.impl.WhatsappAssistantService;
import com.gods.saas.service.impl.WhatsappAssistantFailureDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/whatsapp/twilio")
@RequiredArgsConstructor
@Slf4j
public class TwilioWhatsappWebhookController {

    private final TwilioWebhookSignatureValidator signatureValidator;
    private final OwnerWhatsappPhoneVerificationService verificationService;
    private final WhatsappAssistantService assistantService;

    @PostMapping(
            value = "/inbound",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> inbound(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam MultiValueMap<String, String> form
    ) {
        if (!signatureValidator.isValid(signature, form)) {
            log.warn("Webhook Twilio rechazado por firma invalida.");
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_XML)
                    .body("<Response></Response>");
        }

        String from = form.getFirst("From");
        String body = form.getFirst("Body");
        String messageSid = form.getFirst("MessageSid");
        OwnerWhatsappPhoneVerificationService.InboundVerificationResult result =
                verificationService.verifyInbound(from, body);

        log.info(
                "TWILIO WHATSAPP INBOUND => messageSid={}, verified={}, dialogueVersion=2",
                messageSid,
                result.verified()
        );
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(twiml(resolveReply(from, form.getFirst("To"), body, messageSid, result.reply())));
    }

    private String resolveReply(String from, String to, String body, String messageSid, String verificationReply) {
        if (verificationReply != null || (body != null && body.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("VERIFICAR GODS"))) {
            return verificationReply;
        }
        try {
            String reply = assistantService.reply(from, to, body, messageSid);
            log.info("WhatsApp assistant result: replyPresent={}, dialogueVersion=2", reply != null && !reply.isBlank());
            return reply;
        } catch (Exception exception) {
            // Do not log customer text, phone, provider bodies, tokens or credentials.
            log.warn("WhatsApp assistant failed: {}", WhatsappAssistantFailureDiagnostics.summarize(exception));
            return "No pude completar tu solicitud. Revisa tus reservas en la app antes de intentar otra vez.";
        }
    }

    private String twiml(String message) {
        if (message == null || message.isBlank()) {
            return "<Response></Response>";
        }
        String safe = HtmlUtils.htmlEscape(message == null ? "" : message);
        return "<Response><Message>" + safe + "</Message></Response>";
    }
}
