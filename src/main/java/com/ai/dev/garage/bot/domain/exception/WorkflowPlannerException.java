package com.ai.dev.garage.bot.domain.exception;

/**
 * Thrown when the workflow planner CLI fails to produce a valid execution graph.
 */
public class WorkflowPlannerException extends RuntimeException {

    public WorkflowPlannerException(String message) {
        super(message);
    }

    public WorkflowPlannerException(String message, Throwable cause) {
        super(message, cause);
    }
}
