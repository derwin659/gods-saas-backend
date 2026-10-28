package com.gods.saas.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class WhatsappAssistantFailureDiagnosticsTest {
    @Test void nestedTimeoutIsDistinguishedWithoutLeakingExceptionText() {
        String result = WhatsappAssistantFailureDiagnostics.summarize(new RestClientException("secret-key", new SocketTimeoutException("customer phone")));
        assertTrue(result.contains("category=TIMEOUT"));
        assertTrue(result.contains("RestClientException>SocketTimeoutException"));
        assertFalse(result.contains("secret-key")); assertFalse(result.contains("customer phone"));
    }
    @Test void quotaErrorIncludesStatusAndSafeCodeButNotProviderMessage() {
        var failure = HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"secret status",HttpHeaders.EMPTY,
                "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"private key and customer data\"}}".getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
        String result=WhatsappAssistantFailureDiagnostics.summarize(new RestClientException("private",failure));
        assertTrue(result.contains("httpStatus=429")); assertTrue(result.contains("providerCode=insufficient_quota"));
        assertFalse(result.contains("private")); assertFalse(result.contains("secret status"));
    }
    @Test void unrecognizedErrorCodeAndHtmlBodyAreNeverLogged() {
        for (String body : new String[]{"{\"error\":{\"code\":\"private_customer_value\"}}","<html>private_customer_value</html>"}) {
            var failure=HttpClientErrorException.create(HttpStatus.FORBIDDEN,"secret",HttpHeaders.EMPTY,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
            String result=WhatsappAssistantFailureDiagnostics.summarize(failure);
            assertTrue(result.contains("httpStatus=403")); assertTrue(result.contains("providerCode=unclassified"));
            assertFalse(result.contains("private_customer_value"));
        }
    }
    @Test void dnsFailureIsNotMisreportedAsBadApiKey() {
        String result=WhatsappAssistantFailureDiagnostics.summarize(new RestClientException("private",new UnknownHostException("private.host")));
        assertTrue(result.contains("category=DNS")); assertTrue(result.contains("httpStatus=none"));
        assertFalse(result.contains("private.host"));
    }
}
