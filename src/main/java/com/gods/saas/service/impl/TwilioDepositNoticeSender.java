package com.gods.saas.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TwilioDepositNoticeSender {
    private final ObjectMapper mapper;
    @Value("${whatsapp.twilio.account-sid:}") private String accountSid;
    @Value("${whatsapp.twilio.auth-token:}") private String token;
    @Value("${whatsapp.twilio.from-number:}") private String from;
    @Value("${whatsapp.assistant.deposit-status-content-sid:}") private String contentSid;
    public String send(String phone,String body,Instant lastInbound) throws Exception {
        var form=messageForm(phone,body,lastInbound);
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(8000);
        JsonNode response=RestClient.builder().requestFactory(factory).baseUrl("https://api.twilio.com").build().post()
                .uri("/2010-04-01/Accounts/"+accountSid+"/Messages.json").headers(h -> h.setBasicAuth(accountSid,token))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
        if (response==null || !response.path("sid").asText().matches("SM[0-9a-fA-F]{32}")) throw new IllegalStateException("INVALID_TWILIO_RESPONSE");
        return response.path("sid").asText();
    }

    org.springframework.util.LinkedMultiValueMap<String,String> messageForm(String phone,String body,Instant lastInbound) throws Exception {
        if (accountSid==null || !accountSid.matches("AC[0-9a-fA-F]{32}") || token==null || token.isBlank() || from==null || from.isBlank()) throw new IllegalStateException("TWILIO_NOT_CONFIGURED");
        var form=new LinkedMultiValueMap<String,String>();
        form.add("To","whatsapp:"+phone);form.add("From",from.startsWith("whatsapp:")?from:"whatsapp:"+from);
        if (lastInbound.plus(Duration.ofHours(23)).isAfter(Instant.now())) form.add("Body",body);
        else {
            if (contentSid==null || !contentSid.matches("HX[0-9a-fA-F]{32}")) throw new IllegalStateException("DEPOSIT_TEMPLATE_REQUIRED");
            form.add("ContentSid",contentSid);
            form.add("ContentVariables",mapper.writeValueAsString(Map.of("1",body.replaceAll("[\\r\\n\\t]+"," ").substring(0,Math.min(900,body.replaceAll("[\\r\\n\\t]+"," ").length())))));
        }
        return form;
    }
}
