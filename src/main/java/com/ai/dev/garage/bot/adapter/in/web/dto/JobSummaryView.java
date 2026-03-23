package com.ai.dev.garage.bot.adapter.in.web.dto;

public record JobSummaryView(
    long id,
    String intent,
    String status,
    String badgeClass,
    String taskType,
    String riskLevel,
    String executor,
    int attempt,
    String createdAt,
    String updatedAt
) {
}
