package com.ai.dev.garage.bot.adapter.out.cursor;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.config.CursorCliProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the value to pass to Cursor CLI {@code --model}: job payload {@code cliModel} wins, else
 * {@link CursorCliProperties#getDefaultModel()}. Empty means omit the flag.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime", havingValue = "cursor", matchIfMissing = true)
public class CursorCliModelResolver {

    public static final String CLI_MODEL_PAYLOAD_KEY = "cliModel";

    private final JsonCodec jsonCodec;
    private final CursorCliProperties cursorCliProperties;

    /**
     * Resolves the model id for a job from payload or configured default.
     *
     * @return non-empty model id to pass after {@code --model}, or empty to omit the flag
     */
    public Optional<String> resolveModelForJob(Job job) {
        String fromPayload = readCliModelFromPayload(job);
        if (fromPayload != null && !fromPayload.isBlank()) {
            return Optional.of(fromPayload.trim());
        }
        String def = cursorCliProperties.getDefaultModel();
        if (def != null && !def.isBlank()) {
            return Optional.of(def.trim());
        }
        return Optional.empty();
    }

    private String readCliModelFromPayload(Job job) {
        if (job == null || job.getTaskPayloadJson() == null || job.getTaskPayloadJson().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = jsonCodec.fromJson(job.getTaskPayloadJson());
            Object raw = payload.get(CLI_MODEL_PAYLOAD_KEY);
            if (raw == null) {
                return null;
            }
            if (raw instanceof String s) {
                return s;
            }
            return String.valueOf(raw);
        } catch (Exception e) {
            log.debug("No cliModel in job {} payload: {}", job.getId(), e.getMessage());
            return null;
        }
    }
}
