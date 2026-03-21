package com.ai.dev.garage.bot.adapter.in.web.dto;

public record JobLogLineView(
    int seq,
    String level,
    String line,
    String createdAt,
    String levelClass
) {}
