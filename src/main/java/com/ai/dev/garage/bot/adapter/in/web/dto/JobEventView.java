package com.ai.dev.garage.bot.adapter.in.web.dto;

public record JobEventView(
    String eventType,
    String dataJson,
    String createdAt,
    boolean hasData
) {}
