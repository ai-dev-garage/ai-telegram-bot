package com.ai.dev.garage.bot.application.execution;

public record TaskExecutionResult(boolean success, String summary, int exitCode, String error) {
}
