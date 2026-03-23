package com.ai.dev.garage.bot.adapter.in.telegram.command.support;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.PlanCallbackHandler;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.PlanSessionService;
import com.ai.dev.garage.bot.application.support.ContentLengthLimits;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.PlanSession;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Sends Telegram messages when plan jobs complete or need user input (async from executor).
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlanCompletionTelegramNotifier implements PlanSessionService.PlanCompletionListener {

    private final JobStore jobStore;
    private final PlanSessionStore planSessionStore;
    private final PlanSessionService planSessionService;
    private final TelegramBotClient telegramBotClient;

    @PostConstruct
    void registerListener() {
        planSessionService.setCompletionListener(this);
    }

    @Override
    public void onQuestionsReady(long jobId, long sessionId) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) {
            return;
        }
        Long chatId = job.getRequester().getTelegramChatId();

        PlanSession session = planSessionStore.findById(sessionId).orElse(null);
        int total = 0;
        if (session != null) {
            total = planSessionStore.findQuestionsBySessionAndRound(sessionId, session.getRound()).size();
        }

        String text = "Plan #" + jobId + " — agent has " + total + " question" + (total != 1 ? "s" : "") + ":";
        String prefix = PlanCallbackHandler.CALLBACK_PREFIX;
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row("Answer Questions", prefix + "questions:" + sessionId)
            .row("Cancel Plan", prefix + "reject:" + jobId);
        telegramBotClient.sendWithInlineKeyboard(chatId, text, kb.build());
    }

    @Override
    public void onPlanReady(long jobId) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) {
            return;
        }
        Long chatId = job.getRequester().getTelegramChatId();

        PlanSession session = planSessionStore.findByJobId(jobId).orElse(null);
        if (session == null) {
            return;
        }

        String planText = session.getPlanText();
        String display = PlanCallbackKeyboards.truncateForTelegram(planText, ContentLengthLimits.TELEGRAM_PLAIN_MESSAGE_SAFE);
        String prefix = PlanCallbackHandler.CALLBACK_PREFIX;

        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create()
            .row(List.of(
                new InlineKeyboardBuilder.Button("Build", prefix + "approve:" + jobId),
                new InlineKeyboardBuilder.Button("Adjust", prefix + "adjust:" + jobId)
            ))
            .row(List.of(
                new InlineKeyboardBuilder.Button("Pause", prefix + "pause:" + jobId),
                new InlineKeyboardBuilder.Button("Cancel", prefix + "reject:" + jobId)
            ))
            .row("Q&A History", prefix + "qa:" + session.getId());

        telegramBotClient.sendWithInlineKeyboard(chatId,
            "Plan #" + jobId + " — plan ready:\n\n" + display,
            kb.build());
    }

    @Override
    public void onPlanError(long jobId, String error) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job == null || job.getRequester() == null) {
            return;
        }
        Long chatId = job.getRequester().getTelegramChatId();
        telegramBotClient.sendPlain(chatId,
            "Plan #" + jobId + " failed: " + error);
    }
}
