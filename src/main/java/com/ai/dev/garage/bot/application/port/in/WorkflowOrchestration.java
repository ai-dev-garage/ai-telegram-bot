package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.WorkflowGraph;

/**
 * Primary port for workflow orchestration: starting multi-step execution and reading
 * the persisted execution graph.
 */
public interface WorkflowOrchestration {

    void startExecution(Job parentJob, WorkflowGraph graph);

    WorkflowGraph parseGraph(Job parentJob);
}
