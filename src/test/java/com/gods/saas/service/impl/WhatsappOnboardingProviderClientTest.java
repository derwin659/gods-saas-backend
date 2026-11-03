package com.gods.saas.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.config.WhatsappOnboardingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhatsappOnboardingProviderClientTest {
    final WhatsappOnboardingProperties properties=new WhatsappOnboardingProperties();
    final WhatsappOnboardingProviderClient provider=new WhatsappOnboardingProviderClient(properties,new ObjectMapper());
    final HttpClient http=mock(HttpClient.class);
    WhatsappOnboardingProviderClientTest() {
        properties.setAppId("123456");properties.setAppSecret("test-only-app-secret");
        ReflectionTestUtils.setField(provider,"http",http);
        ReflectionTestUtils.setField(provider,"parentSid","AC"+"a".repeat(32));
        ReflectionTestUtils.setField(provider,"parentToken","1".repeat(32));
    }
    @SuppressWarnings("unchecked") HttpResponse<String> response(int status,String body) {
        var response=(HttpResponse<String>)mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(body);return response;
    }
    @Test void metaAuthorizationMustIncludeExactPhoneIdAndPhoneInTheAuthorizedWaba() throws Exception {
        List<HttpRequest> requests=new ArrayList<>();
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(i->{
            HttpRequest request=i.getArgument(0);requests.add(request);
            return requests.size()==1?response(200,"{\"access_token\":\"temporary-test-token\"}"):
                response(200,"{\"data\":[{\"id\":\"987654\",\"display_phone_number\":\"+51 911 111 111\"}]}");
        });
        provider.verifyAuthorization("test-code","123456","987654","+51911111111");
        assertEquals("POST",requests.get(0).method());assertNull(requests.get(0).uri().getQuery());
        assertEquals("/v24.0/123456/phone_numbers",requests.get(1).uri().getPath());
        assertEquals("Bearer temporary-test-token",requests.get(1).headers().firstValue("Authorization").orElseThrow());
        assertTrue(requests.stream().allMatch(request->request.uri().getHost().equals("graph.facebook.com")));
    }
    @Test void anotherNumberReturnedByMetaCannotAuthorizeTheSubmittedNumber() throws Exception {
        var exchange=response(200,"{\"access_token\":\"temporary\"}");
        var numbers=response(200,"{\"data\":[{\"id\":\"987654\",\"display_phone_number\":\"+51922222222\"}]}");
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(exchange).thenReturn(numbers);
        var failure=assertThrows(WhatsappOnboardingProvider.Failure.class,()->provider.verifyAuthorization("code","123456","987654","+51911111111"));
        assertFalse(failure.uncertain());
    }
    @Test void timeoutAfterCreateRequestIsUncertainButExplicitRejectionIsNot() throws Exception {
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("test timeout"));
        assertTrue(assertThrows(WhatsappOnboardingProvider.Failure.class,()->provider.createAccount(1L)).uncertain());
        doReturn(response(400,"{\"message\":\"rejected\"}")).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertFalse(assertThrows(WhatsappOnboardingProvider.Failure.class,()->provider.createAccount(1L)).uncertain());
    }
}
