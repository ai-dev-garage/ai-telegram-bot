package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStatusFormatterTest {

    @Test
    void shouldIncludeNoExecutionPlanMessageWhenGraphIsNull() {
        var job = Job.builder().id(42L).status(JobStatus.QUEUED).intent("do something").build();

        String result = WorkflowStatusFormatter.format(job, null, List.of());

        assertThat(result).contains("Workflow #42");
        assertThat(result).contains("No execution plan yet");
    }

    @Test
    void shouldShowPendingHourglassForStepsWithNoChildJob() {
        var job = Job.builder().id(1L).status(JobStatus.RUNNING).intent("test").build();
        var graph = new WorkflowGraph(1, List.of(
            step("s1", "Build", false),
            step("s2", "Deploy", false)
        ));

        String result = WorkflowStatusFormatter.format(job, graph, List.of());

        // Both steps pending — should show hourglass icon
        assertThat(result).contains("⏳ 1. Build");
        assertThat(result).contains("⏳ 2. Deploy");
        assertThat(result).contains("PENDING");
    }

    @Test
    void shouldShowCorrectIconForCompletedStep() {
        var job = Job.builder().id(1L).status(JobStatus.RUNNING).intent("test").build();
        var graph = new WorkflowGraph(1, List.of(
            step("s1", "Build", false),
            step("s2", "Deploy", false)
        ));
        var completedChild = Job.builder()
            .id(10L).stepId("s1").status(JobStatus.SUCCESS).build();

        String result = WorkflowStatusFormatter.format(job, graph, List.of(completedChild));

        assertThat(result).contains("✅ 1. Build — SUCCESS");
        assertThat(result).contains("⏳ 2. Deploy");
    }

    @Test
    void shouldShowFailedIconForFailedStep() {
        var job = Job.builder().id(1L).status(JobStatus.FAILED).intent("test").build();
        var graph = new WorkflowGraph(1, List.of(step("s1", "Build", false)));
        var failedChild = Job.builder()
            .id(10L).stepId("s1").status(JobStatus.FAILED).build();

        String result = WorkflowStatusFormatter.format(job, graph, List.of(failedChild));

        assertThat(result).contains("❌ 1. Build — FAILED");
    }

    @Test
    void shouldAppendCriticalLabelForCriticalStep() {
        var job = Job.builder().id(1L).status(JobStatus.AWAITING_INPUT).intent("test").build();
        var graph = new WorkflowGraph(1, List.of(step("s1", "Deploy to prod", true)));

        String result = WorkflowStatusFormatter.format(job, graph, List.of());

        assertThat(result).contains("[CRITICAL]");
        assertThat(result).contains("Deploy to prod");
    }

    @Test
    void shouldTruncateLongIntent() {
        String longIntent = "a".repeat(150);
        var job = Job.builder().id(1L).status(JobStatus.QUEUED).intent(longIntent).build();
        var graph = new WorkflowGraph(1, List.of(step("s1", "Step", false)));

        String result = WorkflowStatusFormatter.format(job, graph, List.of());

        assertThat(result).contains("...");
        // Intent in output should not exceed 100 + "..." characters
        int intentLine = result.indexOf("Intent: ");
        int newline = result.indexOf('\n', intentLine);
        String intentInOutput = result.substring(intentLine + "Intent: ".length(), newline);
        assertThat(intentInOutput).hasSizeLessThanOrEqualTo(103); // 100 chars + "..."
    }

    @Test
    void statusIconReturnsHourglassForNull() {
        assertThat(WorkflowStatusFormatter.statusIcon(null)).isEqualTo("⏳");
    }

    @Test
    void statusIconReturnsMappedIconForKnownStatuses() {
        assertThat(WorkflowStatusFormatter.statusIcon(JobStatus.SUCCESS)).isEqualTo("✅");
        assertThat(WorkflowStatusFormatter.statusIcon(JobStatus.FAILED)).isEqualTo("❌");
        assertThat(WorkflowStatusFormatter.statusIcon(JobStatus.CANCELLED)).isEqualTo("⛔");
        assertThat(WorkflowStatusFormatter.statusIcon(JobStatus.RUNNING)).isEqualTo("▶");
    }

    private static WorkflowStep step(String id, String label, boolean critical) {
        return new WorkflowStep(id, label, TaskType.SHELL_COMMAND, "echo " + id, critical, List.of());
    }
}
