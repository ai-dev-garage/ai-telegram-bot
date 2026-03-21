package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CancelCommand implements TelegramCommand {

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
        String[] parts = ctx.text().split("\\s+");
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
