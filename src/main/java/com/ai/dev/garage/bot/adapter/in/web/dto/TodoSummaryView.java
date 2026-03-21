package com.ai.dev.garage.bot.adapter.in.web.dto;

public record TodoSummaryView(
    long id,
    String title,
    String description,
    String status,
    String badgeClass,
    String source,
    String workspace,
    Long linkedJobId,
    String createdAt,
    String updatedAt,
    boolean open,
    boolean actionable
) {}
