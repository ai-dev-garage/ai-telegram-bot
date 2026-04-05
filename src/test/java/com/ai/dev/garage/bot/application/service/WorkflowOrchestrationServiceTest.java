package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.service.support.JsonService;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowOrchestrationServiceTest {

    @Mock
    private JobService jobService;
    @Mock
    private JobStore jobStore;
    @Mock
    private JobLogAppender logAppender;

    // Use real JsonService to avoid mocking complex serialization/deserialization
    private final JsonService jsonCodec = new JsonService(new ObjectMapper());

    private WorkflowOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowOrchestrationService(jobService, jobStore, jsonCodec, logAppender);
    }

    // ── parseGraph ───────────────────────────────────────────────────────────

    @Test
    void shouldReturnNullWhenPayloadHasNoWorkflowKey() {
        var job = Job.builder().id(1L).taskPayloadJson("{\"input\":\"test\"}").build();

        assertThat(service.parseGraph(job)).isNull();
    }

    @Test
    void shouldReturnGraphWhenPayloadContainsWorkflow() {
        String payload = buildPayloadWithWorkflow(2);
        var job = Job.builder().id(1L).taskPayloadJson(payload).build();

        WorkflowGraph graph = service.parseGraph(job);

        assertThat(graph).isNotNull();
        assertThat(graph.steps()).hasSize(2);
        assertThat(graph.steps().get(0).id()).isEqualTo("step-1");
        assertThat(graph.steps().get(1).id()).isEqualTo("step-2");
    }

    // ── handleChildCompletion — failure ──────────────────────────────────────

    @Test
    void shouldMarkParentFailedWhenChildFailed() {
        var parent = parentJobWithWorkflow(2);
        var child = childJob(2L, 1L, "step-1", 0, JobStatus.FAILED);

        when(jobStore.save(any())).thenReturn(parent);

        service.handleChildCompletion(parent, child, JobStatus.FAILED);

        verify(jobService).markFailed(eq(parent), contains("step-1"), eq(-1), any());
        verify(jobService, never()).markCompleted(any(), any(), any(int.class));
    }

    @Test
    void shouldMarkParentFailedWhenChildCancelled() {
        var parent = parentJobWithWorkflow(2);
        var child = childJob(2L, 1L, "step-1", 0, JobStatus.CANCELLED);

        when(jobStore.save(any())).thenReturn(parent);

        service.handleChildCompletion(parent, child, JobStatus.CANCELLED);

        verify(jobService).markFailed(eq(parent), contains("step-1"), eq(-1), any());
    }

    // ── handleChildCompletion — success, more steps remain ───────────────────

    @Test
    void shouldDispatchNextStepWhenFirstOfTwoSucceeds() {
        var parent = parentJobWithWorkflow(2);
        var completedChild = childJob(2L, 1L, "step-1", 0, JobStatus.SUCCESS);

        when(jobStore.save(any())).thenReturn(parent);
        // After step-1 completes, findChildrenByParentId returns it as COMPLETED
        when(jobService.findChildrenByParentId(1L)).thenReturn(List.of(completedChild));

        service.handleChildCompletion(parent, completedChild, JobStatus.SUCCESS);

        // step-2 should be dispatched via createChildJob
        verify(jobService).createChildJob(eq(parent), any(), eq(1));
        verify(jobService, never()).markCompleted(any(), any(), any(int.class));
    }

    // ── handleChildCompletion — last step success ─────────────────────────────

    @Test
    void shouldMarkParentCompletedWhenLastStepSucceeds() {
        var parent = parentJobWithWorkflow(1);
        var completedChild = childJob(2L, 1L, "step-1", 0, JobStatus.SUCCESS);

        when(jobStore.save(any())).thenReturn(parent);
        // After the only step completes, it is returned as SUCCESS
        when(jobService.findChildrenByParentId(1L)).thenReturn(List.of(completedChild));

        service.handleChildCompletion(parent, completedChild, JobStatus.SUCCESS);

        verify(jobService).markCompleted(parent, "Workflow completed successfully", 0);
        verify(jobService, never()).createChildJob(any(), any(), any(int.class));
    }

    // ── handleChildCompletion — multi-step dispatch ────────────────────────────

    @Test
    void shouldDispatchStepAtCorrectIndexInMultiStepWorkflow() {
        // 3-step workflow; step-1 and step-2 already done, step-3 should be dispatched
        var parent = parentJobWithWorkflow(3);
        var completedStep1 = childJob(2L, 1L, "step-1", 0, JobStatus.SUCCESS);
        var completedStep2 = childJob(3L, 1L, "step-2", 1, JobStatus.SUCCESS);

        when(jobStore.save(any())).thenReturn(parent);
        when(jobService.findChildrenByParentId(1L))
            .thenReturn(List.of(completedStep1, completedStep2));

        service.handleChildCompletion(parent, completedStep2, JobStatus.SUCCESS);

        // step-3 is at index 2
        verify(jobService).createChildJob(eq(parent), any(), eq(2));
        verify(jobService, never()).markCompleted(any(), any(), any(int.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Job parentJobWithWorkflow(int stepCount) {
        return Job.builder()
            .id(1L)
            .taskType(TaskType.WORKFLOW_TASK)
            .status(JobStatus.RUNNING)
            .taskPayloadJson(buildPayloadWithWorkflow(stepCount))
            .build();
    }

    private static Job childJob(long id, long parentId, String stepId, int stepIndex, JobStatus status) {
        return Job.builder()
            .id(id)
            .parentJobId(parentId)
            .stepId(stepId)
            .stepIndex(stepIndex)
            .status(status)
            .build();
    }

    private static String buildPayloadWithWorkflow(int stepCount) {
        var steps = new StringBuilder("[");
        for (int i = 1; i <= stepCount; i++) {
            if (i > 1) {
                steps.append(',');
            }
            steps.append(String.format(Locale.ROOT, """
                {"id":"step-%d","label":"Step %d","taskType":"SHELL_COMMAND",\
                "intent":"echo step%d","critical":false,"dependsOn":[]}
                """, i, i, i));
        }
        steps.append(']');
        return String.format(Locale.ROOT, """
            {"input":"test intent","workflow":{"version":1,"steps":%s}}
            """, steps);
    }
}
