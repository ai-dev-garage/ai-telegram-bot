package com.ai.dev.garage.bot.adapter.out.cursor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.dev.garage.bot.config.RunnerProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AgentTaskSignerTest {

    @Test
    void shouldIncludeMarkerAndPhraseInIntentWhenPhraseConfigured() {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthSecretPhrase("my-phrase");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        assertThat(signer.buildIntentForTaskFile("hello"))
            .isEqualTo("[TASK_RUNNER_TRUSTED]\nmy-phrase\n\nhello");
    }

    @Test
    void shouldReturnIntentBodyOnlyWhenPhraseNotConfigured() {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthSecretPhrase("");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        assertThat(signer.buildIntentForTaskFile("hello")).isEqualTo("hello");
    }

    @Test
    void shouldMatchExpectedHmacHexWhenSigningPayload() throws Exception {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        String jobId = "1";
        String agentHint = "confluence";
        String createdAt = "2020-01-01T00:00:00Z";
        String intentBody = "do thing";
        String nonce = "n1";
        String issuedAt = "2020-01-01T00:01:00Z";
        String workspace = "/projects/myapp";
        String expected = signer.signPayload(jobId, agentHint, createdAt, intentBody, nonce, issuedAt, workspace).orElseThrow();
        String payload = "v1|" + jobId + "|" + agentHint + "|" + createdAt + "|" + intentBody + "|" + nonce + "|" + issuedAt + "|" + workspace;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(expected).isEqualTo(HexFormat.of().formatHex(raw));
    }

    @Test
    void shouldMatchExpectedHmacWhenWorkspaceIsNull() throws Exception {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        String expected = signer.signPayload("1", "agent", "2020-01-01T00:00:00Z", "task", "n1", "2020-01-01T00:01:00Z", null).orElseThrow();
        String payload = "v1|1|agent|2020-01-01T00:00:00Z|task|n1|2020-01-01T00:01:00Z|";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(expected).isEqualTo(HexFormat.of().formatHex(raw));
    }

    @Test
    void shouldProduceDifferentSignatureWhenWorkspaceChanges() {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        String sigA = signer.signPayload("1", "agent", "t", "body", "n", "i", "/workspace/a").orElseThrow();
        String sigB = signer.signPayload("1", "agent", "t", "body", "n", "i", "/workspace/b").orElseThrow();
        assertThat(sigA).isNotEqualTo(sigB);
    }

    @Test
    void shouldReturnEmptyWhenSigningPayloadAndHmacSecretEmpty() {
        RunnerProperties p = new RunnerProperties();
        p.setTaskAuthHmacSecret("");
        AgentTaskSigner signer = new AgentTaskSigner(p);
        assertThat(signer.signPayload("1", "", "", "", "", "", null)).isEmpty();
    }
}
