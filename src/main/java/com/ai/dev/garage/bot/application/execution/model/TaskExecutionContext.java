package com.ai.dev.garage.bot.application.execution.model;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;

public record TaskExecutionContext(Long jobId, JobLogAppender logAppender) {
}
