package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.Job;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobResponseMapper {
    private final JsonCodec jsonCodec;

    public JobResponse toResponse(Job entity) {
        return JobResponse.builder()
            .jobId(String.valueOf(entity.getId()))
            .intent(entity.getIntent())
            .status(entity.getStatus().name().toLowerCase(Locale.ROOT))
            .taskType(entity.getTaskType().name().toLowerCase(Locale.ROOT))
            .riskLevel(entity.getRiskLevel().name().toLowerCase(Locale.ROOT))
            .approvalState(entity.getApprovalState().name().toLowerCase(Locale.ROOT))
            .taskPayload(jsonCodec.fromJson(entity.getTaskPayloadJson()))
            .result(jsonCodec.fromJson(entity.getResultJson()))
            .executorId(entity.getExecutorId())
            .attempt(entity.getAttempt())
            .maxAttempts(entity.getMaxAttempts())
            .lastError(entity.getLastError())
            .startedAt(entity.getStartedAt() != null ? entity.getStartedAt().toString() : null)
            .finishedAt(entity.getFinishedAt() != null ? entity.getFinishedAt().toString() : null)
            .createdAt(entity.getCreatedAt().toString())
            .updatedAt(entity.getUpdatedAt().toString())
            .progressMessages(List.of())
            .agentCliInvoked(entity.getAgentCliInvoked())
            .build();
    }
}
