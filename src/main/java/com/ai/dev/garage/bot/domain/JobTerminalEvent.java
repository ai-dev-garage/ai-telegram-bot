package com.ai.dev.garage.bot.domain;

/**
 * Published when a job reaches a terminal state ({@code SUCCESS}, {@code FAILED}, {@code CANCELLED}).
 * The workflow orchestrator listens for these events to advance parent workflows when child steps complete.
 */
public record JobTerminalEvent(
    Long jobId,
    JobStatus status
) {}
