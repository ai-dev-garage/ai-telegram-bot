package com.ai.dev.garage.bot.adapter.out.execution;

import com.ai.dev.garage.bot.application.execution.TaskExecutor;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ShellTaskExecutor implements TaskExecutor {

    private final JsonCodec jsonCodec;
    private final ShellProcessRunner shellProcessRunner;
    private final AllowedPathValidator allowedPathValidator;

    @Override
    public boolean supports(TaskType taskType) {
        return taskType == TaskType.SHELL_COMMAND;
    }

    @Override
    public TaskExecutionResult execute(Job job, TaskExecutionContext context) {
        Map<String, Object> payload = jsonCodec.fromJson(job.getTaskPayloadJson());
        String command = String.valueOf(payload.getOrDefault("command", ""));
        Object cwdRaw = payload.get("cwd");
        String cwdExpanded = cwdRaw == null ? null : String.valueOf(cwdRaw);
        String cwd = (cwdExpanded == null || Objects.equals(cwdExpanded, "null") || cwdExpanded.isBlank()) ? null : cwdExpanded;
        String cwdErr = allowedPathValidator.validationFailureReason(cwd);
        if (cwdErr != null) {
            return new TaskExecutionResult(false, "", -1, cwdErr);
        }
        return shellProcessRunner.run(context.jobId(), command, cwd, context.logAppender());
    }
}
