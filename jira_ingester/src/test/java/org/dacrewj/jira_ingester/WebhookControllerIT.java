package org.dacrewj.jira_ingester;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import org.dacrewj.contract.DacrewWork;
import org.dacrewj.contract.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.webhook.secret=test-secret", // supply secret for HMAC interceptor
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
class WebhookControllerIT {

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        RabbitTemplate rabbitTemplate() {
            return Mockito.mock(RabbitTemplate.class);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RabbitTemplate rabbitTemplate; // mock to capture enqueued messages without real RabbitMQ

    private static final String SIG_HEADER = "X-Hub-Signature";
    private static final String SECRET = "test-secret";

    @BeforeEach
    void setup() {
        reset(rabbitTemplate);
    }

    @Test
    @DisplayName("Valid payload 1 should be accepted and enqueued")
    void validPayload1_enqueued() throws Exception {
        String body = new String(getContent("/jira-webhook-payload-valid.json"), StandardCharsets.UTF_8);
        String signature = sign(body, SECRET);

        mockMvc.perform(post("/webhook/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIG_HEADER, signature)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<DacrewWork> payloadCaptor = ArgumentCaptor.forClass(DacrewWork.class);
        verify(rabbitTemplate).convertAndSend(eq("dacrew.work"), payloadCaptor.capture());

        DacrewWork work = payloadCaptor.getValue();
        assertThat(work).isNotNull();
        assertThat(work.source()).isEqualTo(Source.JIRA);
        assertThat(work.id()).contains("BTS-");
        assertThat(work.payload()).isNotNull();
    }

    @Test
    @DisplayName("Valid payload 2 with leading log text should still be rejected by JSON parse (invalid for controller)")
    void payload2_isInvalid_andNotEnqueued() throws Exception {
        // This file contains non-JSON preamble text; controller should fail and return 500
        String body = new String(getContent("/jira-webhook-payload-invalid.json"), StandardCharsets.UTF_8);
        String signature = sign(body, SECRET);

        mockMvc.perform(post("/webhook/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIG_HEADER, signature)
                        .content(body))
                .andExpect(status().is5xxServerError());

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Invalid signature should yield 401 and not enqueue")
    void invalidSignature_unauthorized() throws Exception {
        String body = new String(getContent("/jira-webhook-payload-valid.json"), StandardCharsets.UTF_8);
        String badSignature = "sha256=deadbeef";

        mockMvc.perform(post("/webhook/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SIG_HEADER, badSignature)
                        .content(body))
                .andExpect(status().isUnauthorized());

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

	private static byte[] getContent(String resourceName) throws IOException {
		return Objects.requireNonNull(WebhookControllerIT.class.getResourceAsStream(resourceName)).readAllBytes();
	}
}
