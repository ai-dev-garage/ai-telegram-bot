package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.service.JobService;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles inline keyboard callbacks for workflow approve/reject/approve-step actions.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WorkflowCallbackHandler {

    private final TelegramBotClient telegramBotClient;
    private final JobManagement jobManagement;
    private final JobService jobService;

    public void handle(String callbackQueryId, long chatId, long userId, String data) {
        String action = data.substring(WorkflowCommand.CALLBACK_PREFIX.length());
        telegramBotClient.answerCallbackQuery(callbackQueryId, null);

        int sep = action.indexOf(':');
        if (sep < 0) {
            log.warn("Unrecognized workflow callback action: {}", action);
            return;
        }

        String verb = action.substring(0, sep);
        String jobId = action.substring(sep + 1);

        try {
            Job job = jobService.resolveJob(jobId);
            if (!isAuthorized(job, userId)) {
                telegramBotClient.sendPlain(chatId, "You are not authorized to act on job #" + jobId + ".");
                return;
            }

            switch (verb) {
                case "approve" -> handleApprove(chatId, job);
                case "approve-step" -> handleApproveStep(chatId, userId, job);
                case "reject" -> handleReject(chatId, job);
                default -> log.warn("Unrecognized workflow callback verb: {}", verb);
            }
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private void handleApprove(long chatId, Job job) {
        if (job.getStatus() != JobStatus.AWAITING_INPUT) {
            telegramBotClient.sendPlain(chatId, "Workflow #" + job.getId() + " is " + job.getStatus() + " — cannot approve.");
            return;
        }
        jobService.requeue(job);
        telegramBotClient.sendPlain(chatId, "Workflow #" + job.getId() + " approved — execution starting.");
    }

    private void handleApproveStep(long chatId, long userId, Job childJob) {
        jobManagement.approve(String.valueOf(childJob.getId()), String.valueOf(userId));
        jobService.requeue(childJob);
        telegramBotClient.sendPlain(chatId, "Step approved — job #" + childJob.getId() + " queued for execution.");
    }

    private void handleReject(long chatId, Job job) {
        jobManagement.cancel(String.valueOf(job.getId()));
        telegramBotClient.sendPlain(chatId, "Workflow #" + job.getId() + " cancelled.");
    }

    /**
     * Returns {@code true} if the user is allowed to act on the job.
     * Allows action when no requester is set (e.g., API-created jobs) or when the userId matches.
     */
    private static boolean isAuthorized(Job job, long userId) {
        if (job.getRequester() == null || job.getRequester().getTelegramUserId() == null) {
            return true;
        }
        return job.getRequester().getTelegramUserId() == userId;
    }
}
