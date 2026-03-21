package com.ai.dev.garage.bot.adapter.in.web.dto;

public record StatusCountView(
    String status,
    long count,
    String badgeClass
) {}
