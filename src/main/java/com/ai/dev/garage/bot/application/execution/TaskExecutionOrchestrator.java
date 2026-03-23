package com.ai.dev.garage.bot.application.execution;

import com.ai.dev.garage.bot.application.execution.model.TaskExecutionContext;
import com.ai.dev.garage.bot.application.execution.model.TaskExecutionResult;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Chooses the appropriate {@link TaskExecutor} (Strategy registry / composition root).
 */
@Slf4j
@Service
public class TaskExecutionOrchestrator {

    private final Map<TaskType, TaskExecutor> executorsByTaskType;

    /**
     * Explicit ctor: builds {@code TaskType} → {@link TaskExecutor} registry from all executor beans.
     * If multiple executors support the same task type, first one wins and a warning is logged.
     */
    public TaskExecutionOrchestrator(List<TaskExecutor> executors) {
        Map<TaskType, List<TaskExecutor>> candidatesByTaskType = executors.stream()
            .flatMap(executor -> Arrays.stream(TaskType.values())
                .filter(executor::supports)
                .map(taskType -> Map.entry(taskType, executor)))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                () -> new EnumMap<>(TaskType.class),
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));

        this.executorsByTaskType = candidatesByTaskType.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> chooseExecutor(entry.getKey(), entry.getValue()),
                (left, right) -> left,
                () -> new EnumMap<>(TaskType.class)
            ));
    }

    private static TaskExecutor chooseExecutor(TaskType taskType, List<TaskExecutor> candidates) {
        if (candidates.size() > 1) {
            String executorNames = candidates.stream()
                .map(executor -> executor.getClass().getName())
                .collect(Collectors.joining(", "));
            log.warn("Multiple TaskExecutor beans for taskType={}; using first: {}. Candidates: {}",
                taskType,
                candidates.get(0).getClass().getName(),
                executorNames);
        }
        return candidates.get(0);
    }

    public TaskExecutionResult execute(Job job, JobLogAppender logAppender) {
        var context = new TaskExecutionContext(job.getId(), logAppender);
        TaskExecutor executor = executorsByTaskType.get(job.getTaskType());
        if (executor == null) {
            log.warn("No TaskExecutor for job id={} taskType={}", job.getId(), job.getTaskType());
            return new TaskExecutionResult(false, "", -1, "No executor for task type: " + job.getTaskType());
        }
        return executor.execute(job, context);
    }
}
