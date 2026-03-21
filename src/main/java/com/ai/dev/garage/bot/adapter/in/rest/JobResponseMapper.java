package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.domain.Job;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobResponseMapper {
    private final JsonCodec jsonCodec;

    public JobResponse toResponse(Job entity) {
        return JobResponse.builder()
            .jobId(String.valueOf(entity.getId()))
            .intent(entity.getIntent())
            .status(entity.getStatus().name().toLowerCase())
            .taskType(entity.getTaskType().name().toLowerCase())
            .riskLevel(entity.getRiskLevel().name().toLowerCase())
            .approvalState(entity.getApprovalState().name().toLowerCase())
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
