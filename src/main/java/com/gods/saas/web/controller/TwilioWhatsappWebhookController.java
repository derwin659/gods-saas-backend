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
import java.nio.charset.StandardCharsets;
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

    private static final MediaType TWIML_TYPE = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private final TwilioWebhookSignatureValidator signatureValidator;
    private final OwnerWhatsappPhoneVerificationService verificationService;
    private final WhatsappAssistantService assistantService;

    @PostMapping(
            value = "/inbound",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8"
    )
    public ResponseEntity<String> inbound(
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            @RequestParam MultiValueMap<String, String> form
    ) {
        if (!signatureValidator.isValid(signature, form)) {
            log.warn("Webhook Twilio rechazado por firma invalida.");
            return ResponseEntity.status(403)
                    .contentType(TWIML_TYPE)
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
                .contentType(TWIML_TYPE)
                .body(twiml(resolveReply(from, form.getFirst("To"), body, messageSid, result.reply(), form.toSingleValueMap())));
    }

    private String resolveReply(String from, String to, String body, String messageSid, String verificationReply, java.util.Map<String,String> form) {
        if (verificationReply != null || (body != null && body.stripLeading().toUpperCase(java.util.Locale.ROOT).startsWith("VERIFICAR GODS"))) {
            return verificationReply;
        }
        try {
            String reply = form.containsKey("MediaUrl0")
                    ? assistantService.replyWithMedia(from,to,body,messageSid,form)
                    : assistantService.reply(from,to,body,messageSid);
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
        // XML 1.0 permits Unicode but not HTML named entities such as &iquest; or &ntilde;.
        StringBuilder valid = new StringBuilder();
        message.codePoints().filter(cp -> cp == 9 || cp == 10 || cp == 13
                || cp >= 0x20 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD
                || cp >= 0x10000 && cp <= 0x10FFFF).forEach(valid::appendCodePoint);
        String safe = valid.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<Response><Message>" + safe + "</Message></Response>";
    }
}
