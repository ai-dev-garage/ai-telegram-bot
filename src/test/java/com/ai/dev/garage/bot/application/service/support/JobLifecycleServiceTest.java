package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobLifecycleServiceTest {

    @Mock
    private JobStore jobStore;

    @Mock
    private AgentTaskRuntime agentTaskRuntime;

    @InjectMocks
    private JobLifecycleService jobLifecycleService;

    @Test
    void shouldApproveAgentTaskAndReturnCliInvocationWhenFinalized() {
        var job = Job.builder().id(1L).intent("task").build();
        var classified = new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of(),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        );
        when(agentTaskRuntime.startForJob(job)).thenReturn(true);

        var outcome = jobLifecycleService.finalizeNewJob(job, classified);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
        verify(jobStore).save(job);
        verify(agentTaskRuntime).startForJob(job);
        assertThat(outcome.agentCliInvoked()).isTrue();
    }

    @Test
    void shouldAutoApproveLowRiskShellWhenFinalized() {
        var job = Job.builder().id(2L).intent("x").build();
        var classified = new ClassificationResult(
            TaskType.SHELL_COMMAND,
            Map.of(),
            RiskLevel.LOW,
            ApprovalState.APPROVED
        );

        var outcome = jobLifecycleService.finalizeNewJob(job, classified);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
        verify(jobStore).save(any(Job.class));
        assertThat(outcome.agentCliInvoked()).isNull();
    }

    @Test
    void shouldLeaveNonLowRiskShellPendingApprovalWhenFinalized() {
        var job = Job.builder().id(3L).intent("x").build();
        var classified = new ClassificationResult(
            TaskType.SHELL_COMMAND,
            Map.of(),
            RiskLevel.MEDIUM,
            ApprovalState.PENDING
        );

        jobLifecycleService.finalizeNewJob(job, classified);

        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getApprovalState()).isEqualTo(ApprovalState.PENDING);
    }
}
