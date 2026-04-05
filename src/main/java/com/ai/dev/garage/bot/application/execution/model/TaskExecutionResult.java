package com.ai.dev.garage.bot.application.execution.model;

/**
 * Result of a {@link com.ai.dev.garage.bot.application.execution.TaskExecutor} invocation.
 *
 * @param selfManaged when {@code true}, the executor already handled all job state transitions
 *                    (e.g. workflow orchestration) — the caller should NOT call markCompleted/markFailed.
 */
public record TaskExecutionResult(boolean success, String summary, int exitCode, String error,
                                  boolean selfManaged) {

    /** Convenience constructor for executors that do NOT manage job state themselves. */
    public TaskExecutionResult(boolean success, String summary, int exitCode, String error) {
        this(success, summary, exitCode, error, false);
    }
}
