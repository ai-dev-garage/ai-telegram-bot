package com.ai.dev.garage.bot.adapter.in.web.dto;

import java.util.List;

public record JobDetailView(
    long id,
    String intent,
    String status,
    String badgeClass,
    String taskType,
    String riskLevel,
    String approvalState,
    String executor,
    String executorId,
    String approvedBy,
    String requesterUsername,
    int attempt,
    int maxAttempts,
    String lastError,
    String startedAt,
    String finishedAt,
    String createdAt,
    String updatedAt,
    String payloadJson,
    String resultJson,
    List<JobEventView> events,
    List<JobLogLineView> logLines,
    int lastLogSeq,
    boolean isPlanTask,
    String planText,
    String planState,
    String planStateBadge,
    List<PlanQuestionView> planQuestions
) {

    public record PlanQuestionView(
        int round,
        int seq,
        String questionText,
        String answer,
        boolean answered
    ) {
    }
}
