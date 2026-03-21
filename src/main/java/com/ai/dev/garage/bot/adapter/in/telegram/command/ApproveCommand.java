package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ApproveCommand implements TelegramCommand {

    private final JobManagement jobManagement;
    private final TelegramBotClient telegramBotClient;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("approve", "Approve gated job"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/approve <id> — approve gated job");
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/approve");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String[] parts = ctx.text().split("\\s+");
        if (parts.length < 2) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /approve <job_id>");
            return;
        }
        try {
            jobManagement.approve(parts[1], String.valueOf(ctx.userId()));
            telegramBotClient.sendPlain(ctx.chatId(), "Job #" + parts[1] + " approved.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + JobTelegramMessages.formatApproveRejectError(e));
        }
    }
}
