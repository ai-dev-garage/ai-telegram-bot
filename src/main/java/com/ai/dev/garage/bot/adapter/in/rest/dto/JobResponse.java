package com.ai.dev.garage.bot.adapter.in.rest.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private String jobId;
    private String intent;
    private String status;
    private String taskType;
    private String riskLevel;
    private String approvalState;
    private Map<String, Object> taskPayload;
    private Map<String, Object> result;
    private String executorId;
    private Integer attempt;
    private Integer maxAttempts;
    private String lastError;
    private String startedAt;
    private String finishedAt;
    private String createdAt;
    private String updatedAt;
    private Boolean agentCliInvoked;
    @Default
    private List<String> progressMessages = List.of();
}
