package com.gods.saas.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gods.saas.config.RunpodProperties;
import com.gods.saas.domain.dto.request.GenerarImagenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RunpodServerlessClientTest {
    RunpodProperties props;
    RunpodServerlessClient client;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        props = new RunpodProperties();
        props.setEndpointId("test-endpoint");
        props.setApiKey("test-only");
        props.setHealthPollIntervalMillis(1);
        props.setServerlessWaitMillis(1000);
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new RunpodServerlessClient(rest, props, new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false));
    }
    static String json(String value) { return value.replace((char) 39, (char) 34); }
    void submitted(String status) {
        server.expect(requestTo("https://api.runpod.ai/v2/test-endpoint/run"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(json("{'id':'job-1','status':'STATUS'}".replace("STATUS", status)), MediaType.APPLICATION_JSON));
    }
    void status(String json) {
        server.expect(requestTo("https://api.runpod.ai/v2/test-endpoint/status/job-1"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    @Test void followsQueuedAndRunningToCompletion() {
        submitted("IN_QUEUE");
        status(json("{'id':'job-1','status':'IN_PROGRESS'}"));
        status(json("{'id':'job-1','status':'COMPLETED','output':{'success':true,'sesionId':'s1','imagenes':{'frontal':'https://example.test/result.png'}}}"));
        var result = client.runSync(new GenerarImagenRequest(), 1L);
        assertEquals("job-1", result.providerJobId());
        assertEquals("s1", result.response().getSesionId());
        server.verify();
    }
    @Test void timeoutDoesNotPretendJobFailed() {
        props.setServerlessWaitMillis(1);
        submitted("IN_QUEUE");
        server.expect(requestTo("https://api.runpod.ai/v2/test-endpoint/status/job-1"))
            .andRespond(withSuccess(json("{'id':'job-1','status':'IN_PROGRESS'}"), MediaType.APPLICATION_JSON));
        var error = assertThrows(RunpodServerlessClient.UnconfirmedJobException.class,
            () -> client.runSync(new GenerarImagenRequest(), 1L));
        assertEquals("job-1", error.getJobId());
    }
    @Test void rejectsWorkerFailure() {
        submitted("IN_PROGRESS");
        status(json("{'id':'job-1','status':'COMPLETED','output':{'success':false,'message':'GPU failed'}}"));
        assertThrows(IllegalStateException.class, () -> client.runSync(new GenerarImagenRequest(), 1L));
        server.verify();
    }
    @Test void terminalFailureIsNotPending() {
        submitted("FAILED");
        assertThrows(IllegalStateException.class, () -> client.runSync(new GenerarImagenRequest(), 1L));
        server.verify();
    }
}