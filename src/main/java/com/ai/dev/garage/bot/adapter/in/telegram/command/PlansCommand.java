package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(31)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlansCommand implements TelegramCommand {

    private final PlanManagement planManagement;
    private final TelegramBotClient telegramBotClient;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("plans", "List active plan sessions"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/plans — list active plan sessions");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("/plans");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        List<PlanSession> active = planManagement.listActivePlans();
        if (active.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), "No active plans.");
            return;
        }

        List<String> infoLines = new ArrayList<>();
        InlineKeyboardBuilder kb = InlineKeyboardBuilder.create();
        boolean hasButtons = false;

        for (PlanSession s : active) {
            String label = "#" + s.getJobId() + " — " + formatState(s) + " (round " + s.getRound() + ")";
            if (s.getState() == PlanState.PLANNING) {
                infoLines.add(label);
            } else {
                kb.row(label, PlanCallbackHandler.CALLBACK_PREFIX + "view:" + s.getJobId());
                hasButtons = true;
            }
        }

        StringBuilder msg = new StringBuilder("Active plans:");
        if (!infoLines.isEmpty()) {
            msg.append("\n\n");
            for (String line : infoLines) {
                msg.append(line).append("\n");
            }
        }

        if (hasButtons) {
            telegramBotClient.sendWithInlineKeyboard(ctx.chatId(), msg.toString().trim(), kb.build());
        } else {
            telegramBotClient.sendPlain(ctx.chatId(), msg.toString().trim());
        }
    }

    private static String formatState(PlanSession s) {
        return switch (s.getState()) {
            case PLANNING -> "analyzing...";
            case AWAITING_INPUT -> "awaiting your input";
            case PLAN_READY -> "plan ready";
            default -> s.getState().name().toLowerCase();
        };
    }
}
