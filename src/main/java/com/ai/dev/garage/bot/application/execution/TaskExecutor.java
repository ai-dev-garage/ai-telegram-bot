package com.ai.dev.garage.bot.application.execution;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;

/**
 * Strategy: execute one kind of task (SRP / Open-Closed via new strategies).
 */
public interface TaskExecutor {

    boolean supports(TaskType taskType);

    TaskExecutionResult execute(Job job, TaskExecutionContext context);
}
