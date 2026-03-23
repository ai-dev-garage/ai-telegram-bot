package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

@Component
@Order(70)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CancelCommand implements TelegramCommand {

    private static final Pattern WHITESPACE_SPLIT = Pattern.compile("\\s+");

    private final JobManagement jobManagement;
    private final TelegramBotClient telegramBotClient;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("cancel", "Cancel job"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/cancel <id> — cancel job");
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/cancel");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String[] parts = WHITESPACE_SPLIT.split(ctx.text(), -1);
        if (parts.length < 2) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /cancel <job_id>");
            return;
        }
        try {
            jobManagement.cancel(parts[1]);
            telegramBotClient.sendPlain(ctx.chatId(), "Job #" + parts[1] + " cancelled.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }
}
