package com.ai.dev.garage.bot.application.execution;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.TaskType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Chooses the appropriate {@link TaskExecutor} (Strategy registry / composition root).
 */
@Slf4j
@Service
public class TaskExecutionOrchestrator {

    private final Map<TaskType, TaskExecutor> executorsByTaskType;

    /**
     * Explicit ctor: builds {@code TaskType} → {@link TaskExecutor} registry from all executor beans (validates
     * uniqueness per type).
     */
    public TaskExecutionOrchestrator(List<TaskExecutor> executors) {
        this.executorsByTaskType = new EnumMap<>(TaskType.class);
        for (TaskExecutor executor : executors) {
            for (TaskType taskType : TaskType.values()) {
                if (!executor.supports(taskType)) {
                    continue;
                }
                TaskExecutor previous = executorsByTaskType.putIfAbsent(taskType, executor);
                if (previous != null) {
                    throw new IllegalStateException(
                        "Multiple TaskExecutor beans for task type " + taskType + ": "
                            + previous.getClass().getName() + ", " + executor.getClass().getName()
                    );
                }
            }
        }
    }

    public TaskExecutionResult execute(Job job, JobLogAppender logAppender) {
        TaskExecutionContext context = new TaskExecutionContext(job.getId(), logAppender);
        TaskExecutor executor = executorsByTaskType.get(job.getTaskType());
        if (executor == null) {
            log.warn("No TaskExecutor for job id={} taskType={}", job.getId(), job.getTaskType());
            return new TaskExecutionResult(false, "", -1, "No executor for task type: " + job.getTaskType());
        }
        return executor.execute(job, context);
    }
}
