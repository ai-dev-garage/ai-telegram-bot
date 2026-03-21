package com.ai.dev.garage.bot.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobResponseMapperTest {

    @Mock
    private JsonCodec jsonCodec;

    private JobResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new JobResponseMapper(jsonCodec);
    }

    @Test
    void shouldMapJobFieldsWhenEntityHasResultAndTimestamps() {
        OffsetDateTime created = OffsetDateTime.parse("2025-01-01T10:00:00Z");
        OffsetDateTime updated = OffsetDateTime.parse("2025-01-01T11:00:00Z");
        OffsetDateTime started = OffsetDateTime.parse("2025-01-01T10:05:00Z");
        OffsetDateTime finished = OffsetDateTime.parse("2025-01-01T10:30:00Z");
        Job job = Job.builder()
            .id(42L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(2L).build())
            .intent("run tests")
            .taskType(TaskType.SHELL_COMMAND)
            .riskLevel(RiskLevel.LOW)
            .approvalState(ApprovalState.APPROVED)
            .status(JobStatus.SUCCESS)
            .taskPayloadJson("{\"cmd\":\"ls\"}")
            .resultJson("{\"exit\":0}")
            .executorId("exec-1")
            .attempt(2)
            .maxAttempts(5)
            .lastError(null)
            .startedAt(started)
            .finishedAt(finished)
            .createdAt(created)
            .updatedAt(updated)
            .agentCliInvoked(true)
            .build();

        Map<String, Object> payloadMap = Map.of("cmd", "ls");
        Map<String, Object> resultMap = Map.of("exit", 0);
        when(jsonCodec.fromJson("{\"cmd\":\"ls\"}")).thenReturn(payloadMap);
        when(jsonCodec.fromJson("{\"exit\":0}")).thenReturn(resultMap);

        var response = mapper.toResponse(job);

        assertThat(response.getJobId()).isEqualTo("42");
        assertThat(response.getIntent()).isEqualTo("run tests");
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getTaskType()).isEqualTo("shell_command");
        assertThat(response.getRiskLevel()).isEqualTo("low");
        assertThat(response.getApprovalState()).isEqualTo("approved");
        assertThat(response.getTaskPayload()).isSameAs(payloadMap);
        assertThat(response.getResult()).isSameAs(resultMap);
        assertThat(response.getExecutorId()).isEqualTo("exec-1");
        assertThat(response.getAttempt()).isEqualTo(2);
        assertThat(response.getMaxAttempts()).isEqualTo(5);
        assertThat(response.getStartedAt()).isEqualTo(started.toString());
        assertThat(response.getFinishedAt()).isEqualTo(finished.toString());
        assertThat(response.getCreatedAt()).isEqualTo(created.toString());
        assertThat(response.getUpdatedAt()).isEqualTo(updated.toString());
        assertThat(response.getAgentCliInvoked()).isTrue();
        assertThat(response.getProgressMessages()).isEmpty();

        verify(jsonCodec).fromJson("{\"cmd\":\"ls\"}");
        verify(jsonCodec).fromJson("{\"exit\":0}");
    }

    @Test
    void shouldUseEmptyJsonDecodeWhenResultJsonIsNull() {
        OffsetDateTime ts = OffsetDateTime.parse("2025-06-01T12:00:00Z");
        Job job = Job.builder()
            .id(1L)
            .requester(Requester.builder().telegramUserId(1L).telegramChatId(1L).build())
            .intent("x")
            .taskType(TaskType.AGENT_TASK)
            .riskLevel(RiskLevel.HIGH)
            .approvalState(ApprovalState.PENDING)
            .status(JobStatus.QUEUED)
            .taskPayloadJson("{}")
            .resultJson(null)
            .createdAt(ts)
            .updatedAt(ts)
            .build();

        when(jsonCodec.fromJson("{}")).thenReturn(Map.of());
        when(jsonCodec.fromJson(null)).thenReturn(Map.of());

        var response = mapper.toResponse(job);

        assertThat(response.getResult()).isEmpty();
        assertThat(response.getStartedAt()).isNull();
        assertThat(response.getFinishedAt()).isNull();
        verify(jsonCodec).fromJson("{}");
        verify(jsonCodec).fromJson(null);
    }
}
