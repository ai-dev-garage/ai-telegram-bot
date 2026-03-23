package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.TaskExecutor;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Guard: agent tasks should never reach the worker queue; if they do, fail fast with a clear message.
 * This indicates a routing bug — agent tasks should go through AgentTaskRuntime → pending JSON → external CLI → result.
 */
@Component
public class AgentTaskSkipExecutor implements TaskExecutor {
    private static final Logger AGENT_LOG = LoggerFactory.getLogger("com.ai.dev.garage.bot.agent");

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.AGENT_TASK;
    }

    @Override
    public TaskExecutionResult execute(Job job, TaskExecutionContext context) {
        AGENT_LOG.warn("AGENT_TASK job {} reached worker queue unexpectedly — possible routing bug. "
                + "Agent tasks should be handled via the Cursor file-based flow, not the poll queue.",
            job.getId());
        return new TaskExecutionResult(false, "", -1, "Agent task should be handled by Cursor flow");
    }
}
