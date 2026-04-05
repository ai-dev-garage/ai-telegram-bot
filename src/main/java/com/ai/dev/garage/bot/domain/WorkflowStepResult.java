package com.ai.dev.garage.bot.domain;

import java.time.OffsetDateTime;

/**
 * Tracks the execution outcome of a single workflow step.
 *
 * @param childJobId  the id of the child job created for this step
 * @param status      current status of the child job
 * @param completedAt when the child job reached a terminal state (null if still running)
 */
public record WorkflowStepResult(
    Long childJobId,
    JobStatus status,
    OffsetDateTime completedAt
) {}
