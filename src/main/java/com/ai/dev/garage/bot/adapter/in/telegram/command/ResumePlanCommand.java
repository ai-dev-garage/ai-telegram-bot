package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

import lombok.RequiredArgsConstructor;

@Component
@Order(32)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ResumePlanCommand implements TelegramCommand {

    private final PlanManagement planManagement;
    private final TelegramBotClient telegramBotClient;

    // Hidden command — not registered in the Telegram menu or help text.
    // Users interact via inline "Continue Plan" buttons instead.

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("/resume-plan") || t.startsWith("/resumeplan");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String arg = ctx.text().trim()
            .replaceFirst("(?i)^/resume-?plan", "").trim();
        if (arg.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /resume-plan <job_id>");
            return;
        }
        try {
            long jobId = Long.parseLong(arg);
            telegramBotClient.sendPlain(ctx.chatId(), "Resuming plan #" + jobId + "...");
            planManagement.resumePlan(jobId);
        } catch (NumberFormatException e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Invalid job ID: " + arg);
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }
}
