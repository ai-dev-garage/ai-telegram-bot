package com.ai.dev.garage.bot.adapter.out.agenttask;

import com.ai.dev.garage.bot.adapter.out.cursor.AgentTaskSigner;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes pending JSON task files for the active {@link com.ai.dev.garage.bot.domain.TaskType#AGENT_TASK} handoff.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskFileWriter {

    private static final Logger AGENT_LOG = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    private final RunnerProperties runnerProperties;
    private final JsonCodec jsonCodec;
    private final AgentTaskSigner agentTaskSigner;

    @PostConstruct
    void ensureDirectories() {
        if (runnerProperties.getAgentTasksDir() == null) {
            log.warn("agent-tasks-dir is not configured; agent task file writing will be skipped");
            return;
        }
        try {
            Files.createDirectories(pendingDir());
            Files.createDirectories(resultsDir());
        } catch (IOException e) {
            AGENT_LOG.error("Failed to create agent-tasks directories: {}", e.getMessage(), e);
        }
    }

    public void writePendingTaskFile(Job job) {
        if (runnerProperties.getAgentTasksDir() == null) {
            return;
        }
        try {
            Path taskFile = pendingDir().resolve("task-" + job.getId() + ".json");
            String intentBody = job.getIntent();
            String createdAt = job.getCreatedAt().toString();
            String agentHint = String.valueOf(jsonCodec.fromJson(job.getTaskPayloadJson()).getOrDefault("agent_or_command", ""));
            String jobId = String.valueOf(job.getId());
            String nonce = UUID.randomUUID().toString();
            String issuedAt = OffsetDateTime.now(ZoneId.systemDefault()).toString();
            Map<String, Object> jobPayload = jsonCodec.fromJson(job.getTaskPayloadJson());
            Object workspace = jobPayload.get("workspace");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("job_id", jobId);
            payload.put("intent_body", intentBody);
            payload.put("intent", agentTaskSigner.buildIntentForTaskFile(intentBody));
            payload.put("agent_hint", agentHint);
            payload.put("created_at", createdAt);
            payload.put("source", runnerProperties.getTaskSourceId());
            payload.put("nonce", nonce);
            payload.put("issued_at", issuedAt);
            String normalizedWorkspace = null;
            if (workspace instanceof String ws && !ws.isBlank()) {
                normalizedWorkspace = ws.trim();
                payload.put("workspace", normalizedWorkspace);
            }
            var signPayload = new AgentTaskSigner.SignPayload(
                jobId,
                agentHint,
                createdAt,
                intentBody,
                nonce,
                issuedAt,
                normalizedWorkspace
            );
            agentTaskSigner.signPayload(signPayload).ifPresent(sig -> {
                payload.put("algo", "HMAC-SHA256");
                payload.put("signature", sig);
            });
            Files.writeString(taskFile, jsonCodec.toJson(payload));
            log.debug("Wrote agent task file: {}", taskFile);
        } catch (Exception e) {
            AGENT_LOG.error("Failed to write agent task file for job {}: {}", job.getId(), e.getMessage(), e);
        }
    }

    public Path pendingDir() {
        return Path.of(runnerProperties.getAgentTasksDir(), "pending");
    }

    public Path resultsDir() {
        return Path.of(runnerProperties.getAgentTasksDir(), "results");
    }
}
