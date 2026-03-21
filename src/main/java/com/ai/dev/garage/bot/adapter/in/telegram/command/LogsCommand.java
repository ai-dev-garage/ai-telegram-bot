package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobLogQueries;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogsCommand implements TelegramCommand {

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
        String[] parts = ctx.text().split("\\s+");
        if (parts.length < 2) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /logs <job_id> [tail=50]");
            return;
        }
        String id = parts[1];
        int tail = 100;
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
            if (logs.length() > 4000) {
                logs = logs.substring(logs.length() - 4000);
            }
            telegramBotClient.sendPlain(ctx.chatId(), logs.isBlank() ? "No logs." : logs);
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }
}
