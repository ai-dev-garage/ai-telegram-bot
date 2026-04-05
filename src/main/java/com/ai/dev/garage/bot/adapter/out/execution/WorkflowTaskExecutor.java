package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.TaskExecutor;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.in.WorkflowOrchestration;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.WorkflowPlannerRuntime;
import com.ai.dev.garage.bot.application.service.JobService;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowPlanReadyEvent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes {@link TaskType#WORKFLOW_TASK} jobs in two phases:
 * <ol>
 *   <li><b>Planning</b> — if no workflow graph exists in the payload, invokes the
 *       {@link WorkflowPlannerRuntime} to decompose the intent into an execution graph,
 *       saves it, and puts the job into {@code AWAITING_INPUT} for user review.</li>
 *   <li><b>Execution</b> — if a graph already exists (user approved), delegates to
 *       {@link WorkflowOrchestration} which dispatches child jobs event-driven.</li>
 * </ol>
 *
 * <p>Returns {@code selfManaged=true} so {@code RunnerWorker} does not call
 * markCompleted/markFailed — the orchestration service handles terminal state transitions.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WorkflowTaskExecutor implements TaskExecutor {

    private final JsonCodec jsonCodec;
    private final WorkflowPlannerRuntime plannerRuntime;
    private final WorkflowOrchestration orchestrationService;
    private final JobService jobService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.WORKFLOW_TASK;
    }

    @Override
    public TaskExecutionResult execute(Job job, TaskExecutionContext context) {
        try {
            WorkflowGraph existingGraph = orchestrationService.parseGraph(job);

            if (existingGraph == null) {
                return planWorkflow(job, context);
            } else {
                return executeWorkflow(job, existingGraph);
            }
        } catch (Exception e) {
            log.error("Workflow execution failed for job {}", job.getId(), e);
            jobService.markFailed(job, "Workflow error", -1, e.getMessage());
            return new TaskExecutionResult(false, "Workflow error", -1, e.getMessage(), true);
        }
    }

    private TaskExecutionResult planWorkflow(Job job, TaskExecutionContext context) {
        Map<String, Object> payload = jsonCodec.fromJson(job.getTaskPayloadJson());
        String intent = String.valueOf(payload.getOrDefault("input", ""));
        String workspace = payload.containsKey("workspace") ? String.valueOf(payload.get("workspace")) : null;

        context.logAppender().append(job.getId(), "Planning workflow for: " + intent);

        WorkflowGraph graph = plannerRuntime.decompose(intent, workspace);

        if (graph == null || graph.steps().isEmpty()) {
            jobService.markFailed(job, "Planner returned empty workflow", -1, "No executable steps produced");
            return new TaskExecutionResult(false, "Empty workflow", -1, "No executable steps produced", true);
        }

        // Save the graph into the job's task payload
        Map<String, Object> updatedPayload = new HashMap<>(payload);
        updatedPayload.put("workflow", serializeGraph(graph));
        job.setTaskPayloadJson(jsonCodec.toJson(updatedPayload));

        // Park the job for user review
        jobService.markAwaitingInput(job);

        // Notify (Telegram) that plan is ready for review
        eventPublisher.publishEvent(new WorkflowPlanReadyEvent(job.getId(), graph));

        context.logAppender().append(job.getId(), "Workflow planned — " + graph.steps().size() + " steps. Awaiting approval.");
        log.info("Workflow job {} planned with {} steps — awaiting user approval", job.getId(), graph.steps().size());

        return new TaskExecutionResult(true, "Workflow planned with " + graph.steps().size() + " steps", 0, null, true);
    }

    private TaskExecutionResult executeWorkflow(Job job, WorkflowGraph graph) {
        log.info("Starting workflow execution for job {} with {} steps", job.getId(), graph.steps().size());
        orchestrationService.startExecution(job, graph);
        return new TaskExecutionResult(true, "Workflow execution started", 0, null, true);
    }

    private static Map<String, Object> serializeGraph(WorkflowGraph graph) {
        Map<String, Object> graphMap = new HashMap<>();
        graphMap.put("version", graph.version());
        graphMap.put("steps", graph.steps().stream().map(step -> {
            Map<String, Object> stepMap = new HashMap<>();
            stepMap.put("id", step.id());
            stepMap.put("label", step.label());
            stepMap.put("taskType", step.taskType().name());
            stepMap.put("intent", step.intent());
            stepMap.put("critical", step.critical());
            stepMap.put("dependsOn", step.dependsOn());
            return stepMap;
        }).toList());
        return graphMap;
    }
}
