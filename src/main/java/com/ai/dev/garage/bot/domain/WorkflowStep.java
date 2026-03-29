package com.ai.dev.garage.bot.domain;

import java.util.List;

/**
 * A single step in a workflow execution graph.
 *
 * @param id         unique identifier within the workflow (e.g. "analyze-repo")
 * @param label      human-readable description shown in Telegram progress messages
 * @param taskType   what kind of job to create ({@code SHELL_COMMAND} or {@code AGENT_TASK})
 * @param intent     the prompt or command to execute
 * @param critical   if true, the step requires explicit user approval before execution
 * @param dependsOn  step ids that must complete before this step can start (reserved for Phase 2 parallel execution)
 */
public record WorkflowStep(
    String id,
    String label,
    TaskType taskType,
    String intent,
    boolean critical,
    List<String> dependsOn
) {

    private static final int LABEL_MAX_LEN = 80;

    public WorkflowStep {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step id must not be blank");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("Step taskType must not be null");
        }
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("Step intent must not be blank");
        }
        if (dependsOn == null) {
            dependsOn = List.of();
        }
        if (label == null || label.isBlank()) {
            label = intent.length() > LABEL_MAX_LEN ? intent.substring(0, LABEL_MAX_LEN) + "..." : intent;
        }
    }
}
