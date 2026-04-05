package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.WorkflowGraph;

/**
 * SPI for decomposing a user intent into a structured {@link WorkflowGraph} (execution plan).
 * Implementations invoke an external CLI agent (Cursor or Claude) with a planner prompt.
 */
@FunctionalInterface
public interface WorkflowPlannerRuntime {

    /**
     * Decompose a natural-language intent into an executable workflow graph.
     *
     * @param intent    the user's original request text
     * @param workspace absolute path to the workspace directory for context
     * @return a validated workflow graph ready for execution
     */
    WorkflowGraph decompose(String intent, String workspace);
}
