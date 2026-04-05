package com.ai.dev.garage.bot.domain;

import java.util.List;

/**
 * An ordered execution graph produced by the workflow planner. MVP executes steps sequentially
 * in list order; {@link WorkflowStep#dependsOn()} is reserved for Phase 2 parallel execution.
 *
 * @param version schema version (currently {@code 1})
 * @param steps   ordered list of steps to execute
 */
public record WorkflowGraph(
    int version,
    List<WorkflowStep> steps
) {

    public WorkflowGraph {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow graph must have at least one step");
        }
        steps = List.copyOf(steps);
    }
}
