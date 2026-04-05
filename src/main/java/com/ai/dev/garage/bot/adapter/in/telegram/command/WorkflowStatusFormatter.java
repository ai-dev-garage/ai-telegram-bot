package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

/**
 * Formats workflow status messages for Telegram display.
 */
@UtilityClass
class WorkflowStatusFormatter {

    private static final int INTENT_MAX_LEN = 100;

    static String format(Job job, WorkflowGraph graph, List<Job> children) {
        var sb = new StringBuilder();
        sb.append("Workflow #").append(job.getId())
            .append(" — ").append(job.getStatus()).append('\n');
        sb.append("Intent: ").append(truncate(job.getIntent(), INTENT_MAX_LEN)).append('\n');

        if (graph == null) {
            sb.append("\nNo execution plan yet (still planning or failed).");
            return sb.toString();
        }

        sb.append("\nSteps (").append(graph.steps().size()).append("):\n");
        Map<String, Job> childByStep = indexChildrenByStepId(children);

        for (int i = 0; i < graph.steps().size(); i++) {
            appendStepLine(sb, graph.steps().get(i), childByStep, i);
        }
        return sb.toString();
    }

    private static Map<String, Job> indexChildrenByStepId(List<Job> children) {
        return children.stream()
            .filter(child -> child.getStepId() != null)
            .collect(Collectors.toMap(Job::getStepId, Function.identity()));
    }

    private static void appendStepLine(StringBuilder sb, WorkflowStep step,
                                        Map<String, Job> childByStep, int index) {
        Job child = childByStep.get(step.id());
        String status = child != null ? child.getStatus().name() : "PENDING";
        String icon = statusIcon(child != null ? child.getStatus() : null);
        sb.append(icon).append(' ').append(index + 1).append(". ").append(step.label());
        if (step.critical()) {
            sb.append(" [CRITICAL]");
        }
        sb.append(" — ").append(status).append('\n');
    }

    static String statusIcon(JobStatus status) {
        if (status == null) {
            return "⏳"; // hourglass
        }
        return switch (status) {
            case SUCCESS -> "✅";
            case FAILED -> "❌";
            case CANCELLED -> "⛔";
            case RUNNING -> "▶";
            case AWAITING_INPUT -> "✋";
            case PAUSED -> "⏸";
            case QUEUED -> "⏳";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
