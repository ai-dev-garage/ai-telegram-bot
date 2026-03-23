package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobLogQueries;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.support.ContentLengthLimits;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

@Component
@Order(40)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogsCommand implements TelegramCommand {

    private static final Pattern WHITESPACE_SPLIT = Pattern.compile("\\s+");

    private static final int DEFAULT_LOG_TAIL_LINES = 100;

    private final JobManagement jobManagement;
    private final JobLogQueries jobLogQueries;
    private final TelegramBotClient telegramBotClient;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("logs", "Job logs"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/logs <id> [tail=N] — tail log lines");
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/logs");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String[] parts = WHITESPACE_SPLIT.split(ctx.text(), -1);
        if (parts.length < 2) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /logs <job_id> [tail=50]");
            return;
        }
        String id = parts[1];
        int tail = DEFAULT_LOG_TAIL_LINES;
        for (String p : parts) {
            if (p.startsWith("tail=")) {
                tail = Integer.parseInt(p.substring("tail=".length()));
            }
        }
        try {
            Long resolvedId = "last".equalsIgnoreCase(id)
                ? jobManagement.getJob("last").getId()
                : Long.valueOf(id);
            String logs = String.join("\n", jobLogQueries.getTail(resolvedId, tail));
            if (logs.length() > ContentLengthLimits.JOB_TEXT_SNIPPET_MAX) {
                logs = logs.substring(logs.length() - ContentLengthLimits.JOB_TEXT_SNIPPET_MAX);
            }
            telegramBotClient.sendPlain(ctx.chatId(), logs.isBlank() ? "No logs." : logs);
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }
}
