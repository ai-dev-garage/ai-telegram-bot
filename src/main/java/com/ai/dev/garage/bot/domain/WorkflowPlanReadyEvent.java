package com.ai.dev.garage.bot.domain;

/**
 * Published when a workflow planner completes and the execution graph is ready for user review.
 */
public record WorkflowPlanReadyEvent(Long jobId, WorkflowGraph graph) {
}
