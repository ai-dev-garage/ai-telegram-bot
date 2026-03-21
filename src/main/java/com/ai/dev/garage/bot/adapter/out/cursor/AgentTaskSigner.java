package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.config.RunnerProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Prefixes intent for trusted-bot hints and optionally signs task file fields (HMAC-SHA256).
 */
@Component
@RequiredArgsConstructor
public class AgentTaskSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";
    static final String SIGN_VERSION = "v1";

    private final RunnerProperties runnerProperties;

    public String buildIntentForTaskFile(String intentBody) {
        if (intentBody == null) {
            intentBody = "";
        }
        String phrase = runnerProperties.getTaskAuthSecretPhrase();
        if (phrase == null || phrase.isBlank()) {
            return intentBody;
        }
        String marker = runnerProperties.getTaskTrustMarkerLine();
        if (marker == null || marker.isBlank()) {
            marker = "[TASK_RUNNER_TRUSTED]";
        }
        return marker.trim() + "\n" + phrase.trim() + "\n\n" + intentBody;
    }

    /**
     * @return hex signature, or empty if HMAC secret not configured
     */
    public Optional<String> signPayload(
        String jobId,
        String agentHint,
        String createdAt,
        String intentBody,
        String nonce,
        String issuedAt,
        String workspace
    ) {
        String secret = runnerProperties.getTaskAuthHmacSecret();
        if (secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        String payload = String.join(
            "|",
            SIGN_VERSION,
            nullToEmpty(jobId),
            nullToEmpty(agentHint),
            nullToEmpty(createdAt),
            nullToEmpty(intentBody),
            nullToEmpty(nonce),
            nullToEmpty(issuedAt),
            nullToEmpty(workspace)
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
