package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.config.RunnerProperties;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTaskSignerTest {

    @Test
    void shouldIncludeMarkerAndPhraseInIntentWhenPhraseConfigured() {
        var p = new RunnerProperties();
        p.setTaskAuthSecretPhrase("my-phrase");
        var signer = new AgentTaskSigner(p);
        assertThat(signer.buildIntentForTaskFile("hello"))
            .isEqualTo("[TASK_RUNNER_TRUSTED]\nmy-phrase\n\nhello");
    }

    @Test
    void shouldReturnIntentBodyOnlyWhenPhraseNotConfigured() {
        var p = new RunnerProperties();
        p.setTaskAuthSecretPhrase("");
        var signer = new AgentTaskSigner(p);
        assertThat(signer.buildIntentForTaskFile("hello")).isEqualTo("hello");
    }

    @Test
    void shouldMatchExpectedHmacHexWhenSigningPayload() throws Exception {
        var p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        var signer = new AgentTaskSigner(p);
        var jobId = "1";
        var agentHint = "confluence";
        var createdAt = "2020-01-01T00:00:00Z";
        var intentBody = "do thing";
        var nonce = "n1";
        var issuedAt = "2020-01-01T00:01:00Z";
        var workspace = "/projects/myapp";
        var signPayload =
            new AgentTaskSigner.SignPayload(jobId, agentHint, createdAt, intentBody, nonce, issuedAt, workspace);
        var expected = signer.signPayload(signPayload).orElseThrow();
        var payload = "v1|" + jobId + "|" + agentHint + "|" + createdAt + "|" + intentBody + "|" + nonce + "|" + issuedAt + "|" + workspace;
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(expected).isEqualTo(HexFormat.of().formatHex(raw));
    }

    @Test
    void shouldMatchExpectedHmacWhenWorkspaceIsNull() throws Exception {
        var p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        var signer = new AgentTaskSigner(p);
        var signPayload = new AgentTaskSigner.SignPayload(
            "1",
            "agent",
            "2020-01-01T00:00:00Z",
            "task",
            "n1",
            "2020-01-01T00:01:00Z",
            null
        );
        var expected = signer.signPayload(signPayload).orElseThrow();
        var payload = "v1|1|agent|2020-01-01T00:00:00Z|task|n1|2020-01-01T00:01:00Z|";
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        assertThat(expected).isEqualTo(HexFormat.of().formatHex(raw));
    }

    @Test
    void shouldProduceDifferentSignatureWhenWorkspaceChanges() {
        var p = new RunnerProperties();
        p.setTaskAuthHmacSecret("test-secret-key");
        var signer = new AgentTaskSigner(p);
        var sigA = signer.signPayload(new AgentTaskSigner.SignPayload("1", "agent", "t", "body", "n", "i", "/workspace/a"))
            .orElseThrow();
        var sigB = signer.signPayload(new AgentTaskSigner.SignPayload("1", "agent", "t", "body", "n", "i", "/workspace/b"))
            .orElseThrow();
        assertThat(sigA).isNotEqualTo(sigB);
    }

    @Test
    void shouldReturnEmptyWhenSigningPayloadAndHmacSecretEmpty() {
        var p = new RunnerProperties();
        p.setTaskAuthHmacSecret("");
        var signer = new AgentTaskSigner(p);
        assertThat(signer.signPayload(new AgentTaskSigner.SignPayload("1", "", "", "", "", "", null))).isEmpty();
    }
}
