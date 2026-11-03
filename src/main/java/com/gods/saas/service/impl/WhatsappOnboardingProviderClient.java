package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.*;
import com.gods.saas.config.WhatsappOnboardingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Service @RequiredArgsConstructor
public class WhatsappOnboardingProviderClient implements WhatsappOnboardingProvider {
    private final WhatsappOnboardingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Value("${whatsapp.twilio.account-sid:}") private String parentSid;
    @Value("${whatsapp.twilio.auth-token:}") private String parentToken;
    @Value("${whatsapp.twilio.inbound-webhook-url:}") private String webhook;
    @Value("${whatsapp.twilio.enabled:false}") private boolean twilioEnabled;
    @Value("${whatsapp.twilio.validate-inbound-signature:true}") private boolean signedInbound;
    public boolean ready() {
        try { var url=URI.create(webhook);return properties.configured() && twilioEnabled && signedInbound
            && parentSid.matches("AC[0-9a-fA-F]{32}") && parentToken.matches("[0-9a-fA-F]{32}")
            && "https".equals(url.getScheme()) && url.getHost()!=null && url.getUserInfo()==null; }
        catch(RuntimeException invalid){return false;}
    }
    public void verifyAuthorization(String code,String waba,String phoneId,String phone) {
        String graph="https://graph.facebook.com/"+properties.getGraphVersion();
        JsonNode exchange=request(graph+"/oauth/access_token","POST","application/x-www-form-urlencoded",
            form(Map.of("client_id",properties.getAppId(),"client_secret",properties.getAppSecret(),"code",code)),null,false);
        String token=exchange.path("access_token").asText();
        if(token.isBlank())throw new Failure(false);
        JsonNode numbers=request(graph+"/"+waba+"/phone_numbers?fields=id,display_phone_number&limit=100","GET",null,null,"Bearer "+token,false);
        boolean authorized=false;
        for(var number:numbers.path("data")) {
            String digits=number.path("display_phone_number").asText().replaceAll("[^0-9]","");
            if(phoneId.equals(number.path("id").asText()) && phone.equals("+"+digits))authorized=true;
        }
        if(!authorized)throw new Failure(false);
        // The short-lived customer token is discarded; only the scoped Twilio credentials are persisted.
    }
    public Account createAccount(Long tenant) {
        JsonNode response=request("https://api.twilio.com/2010-04-01/Accounts.json","POST","application/x-www-form-urlencoded",
            form(Map.of("FriendlyName","SuperGods negocio "+tenant)),basic(parentSid,parentToken),true);
        String sid=response.path("sid").asText(),token=response.path("auth_token").asText();
        if(!sid.matches("AC[0-9a-fA-F]{32}") || !token.matches("[0-9a-fA-F]{32}") || sid.equals(parentSid))throw new Failure(true);
        return new Account(sid,token);
    }
    public Sender register(Account account,String phone,String waba) {
        try {
            String body=mapper.writeValueAsString(Map.of("sender_id","whatsapp:"+phone,
                "configuration",Map.of("waba_id",waba),"webhook",Map.of("callback_url",webhook,"callback_method","POST")));
            return sender(request("https://messaging.twilio.com/v2/Channels/Senders","POST","application/json",body,basic(account.sid(),account.token()),true));
        }catch(Failure failure){throw failure;}catch(Exception invalid){throw new Failure(true);}
    }
    public Sender refresh(Account account,String sid) {
        if(sid==null || !sid.matches("XE[0-9a-fA-F]{32}"))throw new Failure(false);
        return sender(request("https://messaging.twilio.com/v2/Channels/Senders/"+sid,"GET",null,null,basic(account.sid(),account.token()),false));
    }
    private Sender sender(JsonNode response) {
        String sid=response.path("sid").asText();
        if(!sid.matches("XE[0-9a-fA-F]{32}"))throw new Failure(true);
        return new Sender(sid,response.path("status").asText(),WhatsappBusinessSenderService.normalizePhone(response.path("sender_id").asText()),response.path("configuration").path("waba_id").asText());
    }
    private JsonNode request(String url,String method,String contentType,String body,String authorization,boolean createsResource) {
        try {
            var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15));
            if(authorization!=null)builder.header("Authorization",authorization);
            if(contentType!=null)builder.header("Content-Type",contentType);
            if("GET".equals(method))builder.GET();else builder.POST(HttpRequest.BodyPublishers.ofString(body));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300)
                throw new Failure(createsResource && (response.statusCode()>=500 || response.statusCode()==408));
            return mapper.readTree(response.body());
        } catch(Failure failure){throw failure;}catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new Failure(createsResource);}
    }
    private String basic(String sid,String token){return "Basic "+Base64.getEncoder().encodeToString((sid+":"+token).getBytes(StandardCharsets.UTF_8));}
    private String form(Map<String,String> parameters){return parameters.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
}
