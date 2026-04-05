package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.WorkflowCommand;
import com.ai.dev.garage.bot.application.port.in.WorkflowOrchestration;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.JobTerminalEvent;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowGraph;
import com.ai.dev.garage.bot.domain.WorkflowPlanReadyEvent;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends proactive Telegram notifications for workflow events:
 * <ul>
 *   <li>Plan ready (awaiting approval) — shows steps with approve/reject buttons</li>
 *   <li>Step completion progress</li>
 *   <li>Workflow completion or failure</li>
 *   <li>Critical step awaiting approval</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WorkflowTelegramNotifier {

    private final TelegramBotClient telegramBotClient;
    private final JobStore jobStore;
    private final WorkflowOrchestration orchestrationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPlanReady(WorkflowPlanReadyEvent event) {
        jobStore.findById(event.jobId())
            .ifPresent(job -> notifyPlanReady(job, event.graph()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobTerminal(JobTerminalEvent event) {
        jobStore.findById(event.jobId())
            .ifPresent(job -> routeTerminalNotification(job, event.status()));
    }

    private void routeTerminalNotification(Job job, JobStatus status) {
        if (job.getParentJobId() != null) {
            notifyStepProgress(job, status);
        } else if (job.getTaskType() == TaskType.WORKFLOW_TASK) {
            notifyWorkflowTerminal(job, status);
        }
    }

    /**
     * Called when a workflow enters AWAITING_INPUT after planning completes.
     * Sends the plan summary with approve/reject buttons.
     */
    private void notifyPlanReady(Job workflowJob, WorkflowGraph graph) {
        Long chatId = getChatId(workflowJob);
        if (chatId == null) {
            return;
        }

        var sb = new StringBuilder();
        sb.append("Workflow #").append(workflowJob.getId()).append(" — plan ready\n\n");
        sb.append("Steps (").append(graph.steps().size()).append("):\n");
        IntStream.range(0, graph.steps().size()).forEach(i -> {
            WorkflowStep step = graph.steps().get(i);
            sb.append(i + 1).append(". ").append(step.label())
                .append(" [").append(step.taskType().name()).append("]");
            if (step.critical()) {
                sb.append(" ⚠️ CRITICAL");
            }
            sb.append('\n');
        });

        var keyboard = InlineKeyboardBuilder.create()
            .row(List.of(
                new InlineKeyboardBuilder.Button("Approve", WorkflowCommand.CALLBACK_PREFIX + "approve:" + workflowJob.getId()),
                new InlineKeyboardBuilder.Button("Reject", WorkflowCommand.CALLBACK_PREFIX + "reject:" + workflowJob.getId())
            ))
            .build();

        telegramBotClient.sendWithInlineKeyboard(chatId, sb.toString(), keyboard);
    }

    private void notifyStepProgress(Job childJob, JobStatus status) {
        if (childJob.getParentJobId() == null) {
            return;
        }
        jobStore.findById(childJob.getParentJobId())
            .ifPresent(parentJob -> sendStepProgressMessage(parentJob, childJob, status));
    }

    private void sendStepProgressMessage(Job parentJob, Job childJob, JobStatus status) {
        Long chatId = getChatId(parentJob);
        if (chatId == null) {
            return;
        }
        WorkflowGraph graph = orchestrationService.parseGraph(parentJob);
        int totalSteps = graph != null ? graph.steps().size() : 0;
        int stepNum = childJob.getStepIndex() != null ? childJob.getStepIndex() + 1 : 0;

        String icon = status == JobStatus.SUCCESS ? "✅" : "❌";
        String message = icon + " Workflow #" + parentJob.getId()
            + " — step " + stepNum + "/" + totalSteps
            + " " + status.name().toLowerCase(Locale.ROOT)
            + ": " + (childJob.getStepId() != null ? childJob.getStepId() : "unknown");

        telegramBotClient.sendPlain(chatId, message);
    }

    private void notifyWorkflowTerminal(Job workflowJob, JobStatus status) {
        Long chatId = getChatId(workflowJob);
        if (chatId == null) {
            return;
        }

        String icon = status == JobStatus.SUCCESS ? "✅" : "❌";
        String message = icon + " Workflow #" + workflowJob.getId()
            + " — " + status.name().toLowerCase(Locale.ROOT);

        telegramBotClient.sendPlain(chatId, message);
    }

    private static Long getChatId(Job job) {
        if (job.getRequester() == null || job.getRequester().getTelegramChatId() == null) {
            return null;
        }
        return job.getRequester().getTelegramChatId();
    }
}
