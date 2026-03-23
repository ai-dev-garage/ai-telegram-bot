package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.config.RunnerProperties;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.RequiredArgsConstructor;

/**
 * Prefixes intent for trusted-bot hints and optionally signs task file fields (HMAC-SHA256).
 */
@Component
@RequiredArgsConstructor
public class AgentTaskSigner {

    static final String SIGN_VERSION = "v1";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RunnerProperties runnerProperties;

    public record SignPayload(
        String jobId,
        String agentHint,
        String createdAt,
        String intentBody,
        String nonce,
        String issuedAt,
        String workspace
    ) {
    }

    public String buildIntentForTaskFile(String intentBody) {
        String body = intentBody == null ? "" : intentBody;
        String phrase = runnerProperties.getTaskAuthSecretPhrase();
        if (phrase == null || phrase.isBlank()) {
            return body;
        }
        String marker = runnerProperties.getTaskTrustMarkerLine();
        if (marker == null || marker.isBlank()) {
            marker = "[TASK_RUNNER_TRUSTED]";
        }
        return marker.trim() + "\n" + phrase.trim() + "\n\n" + body;
    }

    /**
     * Computes HMAC-SHA256 hex for the signed task payload, or empty when no HMAC secret is configured.
     *
     * @return hex signature, or empty if HMAC secret not configured
     */
    public Optional<String> signPayload(SignPayload signPayload) {
        String secret = runnerProperties.getTaskAuthHmacSecret();
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        String payload = String.join(
            "|",
            SIGN_VERSION,
            nullToEmpty(signPayload.jobId()),
            nullToEmpty(signPayload.agentHint()),
            nullToEmpty(signPayload.createdAt()),
            nullToEmpty(signPayload.intentBody()),
            nullToEmpty(signPayload.nonce()),
            nullToEmpty(signPayload.issuedAt()),
            nullToEmpty(signPayload.workspace())
        );
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Optional.of(HexFormat.of().formatHex(raw));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign agent task payload", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
