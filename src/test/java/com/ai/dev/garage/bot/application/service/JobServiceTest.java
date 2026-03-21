package com.ai.dev.garage.bot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.ai.dev.garage.bot.application.port.in.support.IntentClassification;
import com.ai.dev.garage.bot.application.port.in.support.JobLifecycle;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobStore jobStore;

    @Mock
    private IntentClassification intentClassification;

    @Mock
    private JsonCodec jsonCodec;

    @Mock
    private JobLifecycle jobLifecycle;

    @Mock
    private AllowedPathValidator allowedPathValidator;

    @InjectMocks
    private JobService jobService;

    @Test
    void shouldPersistAndFinalizeWhenCreateJob() {
        Requester requester = Requester.builder().telegramUserId(1L).telegramChatId(2L).build();
        ClassificationResult classified = new ClassificationResult(
            TaskType.SHELL_COMMAND,
            Map.of("command", "git status"),
            RiskLevel.LOW,
            ApprovalState.APPROVED
        );
        when(intentClassification.classify("  git status  ")).thenReturn(classified);
        when(jsonCodec.toJson(anyMap())).thenReturn("{\"command\":\"git status\"}");

        when(jobStore.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(10L);
            return j;
        });
        when(jobLifecycle.finalizeNewJob(any(Job.class), eq(classified))).thenReturn(new JobLifecycle.FinalizeOutcome(null));

        Job result = jobService.createJob("  git status  ", requester, null);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(JobStatus.QUEUED);
        verify(jobStore).save(any(Job.class));
        verify(jobLifecycle).finalizeNewJob(any(Job.class), eq(classified));
    }

    @Test
    void shouldStoreClassificationInputAsIntentWhenAgentTask() {
        Requester requester = Requester.builder().telegramUserId(1L).telegramChatId(2L).build();
        ClassificationResult classified = new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of(
                "agent_or_command", "agent",
                "input", "brief status of the projects",
                "context", Map.of()),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        );
        when(intentClassification.classify("agent brief status of the projects")).thenReturn(classified);
        when(jsonCodec.toJson(anyMap())).thenReturn("{}");
        when(jobStore.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(42L);
            return j;
        });
        when(jobLifecycle.finalizeNewJob(any(Job.class), eq(classified))).thenReturn(new JobLifecycle.FinalizeOutcome(null));

        Job result = jobService.createJob("agent brief status of the projects", requester, null);

        assertThat(result.getIntent()).isEqualTo("brief status of the projects");
        assertThat(result.getTaskType()).isEqualTo(TaskType.AGENT_TASK);
    }

    @Test
    void shouldMergePreferredShellCwdWhenShellCommand() {
        Requester requester = Requester.builder().telegramUserId(1L).telegramChatId(2L).build();
        Map<String, Object> shellPayload = new HashMap<>();
        shellPayload.put("command", "ls");
        shellPayload.put("cwd", null);
        ClassificationResult classified = new ClassificationResult(
            TaskType.SHELL_COMMAND,
            shellPayload,
            RiskLevel.LOW,
            ApprovalState.APPROVED
        );
        when(intentClassification.classify("ls")).thenReturn(classified);
        when(jobStore.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(11L);
            return j;
        });
        when(jobLifecycle.finalizeNewJob(any(Job.class), eq(classified))).thenReturn(new JobLifecycle.FinalizeOutcome(null));

        ArgumentCaptor<java.util.Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        when(jsonCodec.toJson(payloadCaptor.capture())).thenReturn("{}");

        jobService.createJob("ls", requester, "/allowed/path");

        assertThat(payloadCaptor.getValue().get("cwd")).isEqualTo("/allowed/path");
    }

    @Test
    void shouldPutWorkspaceInPayloadWhenAgentTaskAndPreferredPathAllowed() {
        Requester requester = Requester.builder().telegramUserId(1L).telegramChatId(2L).build();
        ClassificationResult classified = new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of(
                "agent_or_command", "agent",
                "input", "do something",
                "context", Map.of()),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        );
        when(intentClassification.classify("agent do something")).thenReturn(classified);
        when(allowedPathValidator.validationFailureReason("/projects/app")).thenReturn(null);
        when(jobStore.save(any(Job.class))).thenAnswer(inv -> {
            Job j = inv.getArgument(0);
            j.setId(99L);
            return j;
        });
        when(jobLifecycle.finalizeNewJob(any(Job.class), eq(classified))).thenReturn(new JobLifecycle.FinalizeOutcome(null));

        ArgumentCaptor<java.util.Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        when(jsonCodec.toJson(payloadCaptor.capture())).thenReturn("{}");

        jobService.createJob("agent do something", requester, "/projects/app");

        assertThat(payloadCaptor.getValue().get("workspace")).isEqualTo("/projects/app");
    }

    @Test
    void shouldThrowWhenAgentTaskWorkspaceNotAllowlisted() {
        Requester requester = Requester.builder().telegramUserId(1L).telegramChatId(2L).build();
        ClassificationResult classified = new ClassificationResult(
            TaskType.AGENT_TASK,
            Map.of("agent_or_command", "agent", "input", "x", "context", Map.of()),
            RiskLevel.MEDIUM,
            ApprovalState.APPROVED
        );
        when(intentClassification.classify("agent x")).thenReturn(classified);
        when(allowedPathValidator.validationFailureReason("/bad")).thenReturn("cwd is not under an allowlisted path: /bad");

        assertThatThrownBy(() -> jobService.createJob("agent x", requester, "/bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("allowlisted");
    }
}
