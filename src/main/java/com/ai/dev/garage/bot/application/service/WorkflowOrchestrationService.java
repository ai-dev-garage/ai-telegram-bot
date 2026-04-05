package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.WorkflowOrchestration;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.JobTerminalEvent;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Event-driven workflow orchestrator. Advances parent workflows when child steps complete.
 *
 * <p>The orchestrator is reactive — it does not block threads. It is invoked:
 * <ol>
 *   <li>By {@code WorkflowTaskExecutor} to dispatch the first step</li>
 *   <li>By {@link #onJobTerminal(JobTerminalEvent)} when a child completes</li>
 *   <li>By the approval flow when a critical child step is approved</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOrchestrationService implements WorkflowOrchestration {

    private final JobService jobService;
    private final JobStore jobStore;
    private final JsonCodec jsonCodec;
    private final JobLogAppender logAppender;

    @Override
    public void startExecution(Job parentJob, WorkflowGraph graph) {
        log.info("Starting workflow execution for job {} ({} steps)", parentJob.getId(), graph.steps().size());
        logAppender.append(parentJob.getId(), "Workflow execution started — " + graph.steps().size() + " steps");
        dispatchNextStep(parentJob, graph);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobTerminal(JobTerminalEvent event) {
        jobStore.findById(event.jobId())
            .filter(completedJob -> completedJob.getParentJobId() != null)
            .ifPresent(completedJob -> processCompletedChild(completedJob, event.status()));
    }

    private void processCompletedChild(Job completedJob, JobStatus status) {
        jobStore.findById(completedJob.getParentJobId())
            .filter(parentJob -> parentJob.getTaskType() == TaskType.WORKFLOW_TASK)
            .ifPresent(parentJob -> handleChildCompletion(parentJob, completedJob, status));
    }

    void handleChildCompletion(Job parentJob, Job childJob, JobStatus childStatus) {
        WorkflowGraph graph = parseGraph(parentJob);
        if (graph == null) {
            log.warn("No workflow graph found for parent job {}", parentJob.getId());
            return;
        }

        updateStepResult(parentJob, childJob.getStepId(), childJob.getId(), childStatus);

        if (childStatus == JobStatus.FAILED || childStatus == JobStatus.CANCELLED) {
            logAppender.append(parentJob.getId(), stepMsg(childJob, graph, "FAILED"));
            jobService.markFailed(parentJob, "Workflow failed at step: " + childJob.getStepId(),
                -1, "Child job " + childJob.getId() + " " + childStatus);
            return;
        }

        logAppender.append(parentJob.getId(), stepMsg(childJob, graph, "completed"));
        dispatchNextStep(parentJob, graph);
    }

    private void dispatchNextStep(Job parentJob, WorkflowGraph graph) {
        int nextIndex = findNextPendingStepIndex(parentJob, graph);

        if (nextIndex >= graph.steps().size()) {
            logAppender.append(parentJob.getId(), "Workflow completed — all " + graph.steps().size() + " steps succeeded");
            jobService.markCompleted(parentJob, "Workflow completed successfully", 0);
            return;
        }

        WorkflowStep step = graph.steps().get(nextIndex);

        if (parentJob.getStatus() != JobStatus.RUNNING) {
            parentJob.setStatus(JobStatus.RUNNING);
            parentJob.setStartedAt(OffsetDateTime.now(ZoneId.systemDefault()));
            jobStore.save(parentJob);
        }

        jobService.createChildJob(parentJob, step, nextIndex);

        if (step.critical()) {
            logAppender.append(parentJob.getId(), stepDispatchMsg(nextIndex, graph, step.label() + " [CRITICAL]", "awaiting approval"));
            jobService.markAwaitingInput(parentJob);
        } else {
            logAppender.append(parentJob.getId(), stepDispatchMsg(nextIndex, graph, step.label(), "dispatched"));
        }
    }

    private int findNextPendingStepIndex(Job parentJob, WorkflowGraph graph) {
        List<Job> children = jobService.findChildrenByParentId(parentJob.getId());

        Map<String, JobStatus> completedSteps = children.stream()
            .filter(c -> c.getStepId() != null && isTerminal(c.getStatus()))
            .collect(Collectors.toMap(Job::getStepId, Job::getStatus));

        int stepCount = graph.steps().size();
        OptionalInt firstPending = IntStream.range(0, stepCount)
            .filter(i -> !completedSteps.containsKey(graph.steps().get(i).id()))
            .findFirst();

        if (firstPending.isEmpty()) {
            return stepCount;
        }

        int idx = firstPending.getAsInt();
        String stepId = graph.steps().get(idx).id();
        boolean alreadyDispatched = children.stream()
            .anyMatch(c -> stepId.equals(c.getStepId()) && !isTerminal(c.getStatus()));
        return alreadyDispatched ? stepCount : idx;
    }

    private void updateStepResult(Job parentJob, String stepId, Long childJobId, JobStatus status) {
        Map<String, Object> result = parentJob.getResultJson() != null
            ? jsonCodec.fromJson(parentJob.getResultJson()) : new HashMap<>();

        @SuppressWarnings("unchecked") // JSON codec returns untyped maps; safe cast for known key
        var stepResults = (Map<String, Object>) result.computeIfAbsent("stepResults", k -> new HashMap<>());

        stepResults.put(stepId, Map.of(
            "childJobId", childJobId,
            "status", status.name(),
            "completedAt", OffsetDateTime.now(ZoneId.systemDefault()).toString()
        ));

        parentJob.setResultJson(jsonCodec.toJson(result));
        jobStore.save(parentJob);
    }

    @Override
    public WorkflowGraph parseGraph(Job parentJob) {
        Map<String, Object> payload = jsonCodec.fromJson(parentJob.getTaskPayloadJson());
        Object workflowRaw = payload.get("workflow");
        if (workflowRaw == null) {
            return null;
        }
        String workflowJson = jsonCodec.toJson(workflowRaw);
        return WorkflowGraphDeserializer.fromJson(workflowJson, jsonCodec);
    }

    private static boolean isTerminal(JobStatus status) {
        return status == JobStatus.SUCCESS || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }

    private static String stepMsg(Job childJob, WorkflowGraph graph, String action) {
        return "Step " + (childJob.getStepIndex() + 1) + "/" + graph.steps().size() + " " + action + ": " + childJob.getStepId();
    }

    private static String stepDispatchMsg(int index, WorkflowGraph graph, String label, String action) {
        return "Step " + (index + 1) + "/" + graph.steps().size() + " " + action + ": " + label;
    }
}
