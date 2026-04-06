package com.ai.dev.garage.bot.adapter.out.cli;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.Job;

import org.springframework.stereotype.Component;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the workspace directory for a job.
 * Shared by all CLI adapters (Cursor agent, Cursor plan, Claude, etc.).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliWorkspaceResolver {

    private final JsonCodec jsonCodec;

    /**
     * Resolves workspace from job payload, then falls back to the provided
     * default and finally to the AGENT_TASK_WORKSPACE env var.
     */
    public String resolve(Job job, String propertyDefault) {
        try {
            Map<String, Object> payload = jsonCodec.fromJson(job.getTaskPayloadJson());
            Object w = payload.get("workspace");
            if (w instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        } catch (Exception e) {
            log.debug("No workspace in job payload for job {}: {}", job.getId(), e.getMessage());
        }
        if (propertyDefault != null && !propertyDefault.isBlank()) {
            return propertyDefault.trim();
        }
        return System.getenv().getOrDefault("AGENT_TASK_WORKSPACE", System.getProperty("user.home"));
    }
}
