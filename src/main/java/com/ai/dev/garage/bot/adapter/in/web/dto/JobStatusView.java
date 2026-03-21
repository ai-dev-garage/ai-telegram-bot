package com.ai.dev.garage.bot.adapter.in.web.dto;

public record JobStatusView(
    long id,
    String status,
    String badgeClass,
    String lastError,
    boolean hasError,
    boolean canRetry,
    boolean canCancel
) {}
