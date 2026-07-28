package com.gods.saas.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Slf4j
public class TwilioWebhookSignatureValidator {

    @Value("${whatsapp.twilio.auth-token:}")
    private String authToken;

    @Value("${whatsapp.twilio.inbound-webhook-url:}")
    private String inboundWebhookUrl;

    @Value("${whatsapp.twilio.validate-inbound-signature:true}")
    private boolean validateSignature;

    public boolean isValid(String signature, Map<String, List<String>> parameters) {
        if (!validateSignature) {
            log.warn("La validacion de firma del webhook Twilio esta desactivada.");
            return true;
        }
        if (!hasText(signature) || !hasText(authToken) || !hasText(inboundWebhookUrl)) {
            return false;
        }

        try {
            StringBuilder payload = new StringBuilder(inboundWebhookUrl.trim());
            Map<String, List<String>> sorted = new TreeMap<>(parameters);
            for (Map.Entry<String, List<String>> entry : sorted.entrySet()) {
                List<String> values = new ArrayList<>(entry.getValue() == null
                        ? List.of("")
                        : entry.getValue());
                values.sort(String::compareTo);
                for (String value : values) {
                    payload.append(entry.getKey()).append(value == null ? "" : value);
                }
            }

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    authToken.trim().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA1"
            ));
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8))
            );

            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            log.error("No se pudo validar la firma del webhook Twilio", exception);
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
