package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.WorkspaceResolutionService;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.domain.Requester;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@Order(17)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlanCommand implements TelegramCommand {

    private final PlanManagement planManagement;
    private final TelegramBotClient telegramBotClient;
    private final WorkspaceResolutionService workspaceResolutionService;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("plan", "Start a plan session"));
    }

    @Override
    public List<String> helpLines() {
        return List.of(
            "/plan <prompt> — start a plan session",
            "/plan @alias … — model alias (telegram-model-aliases); /models lists ids");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return Objects.equals(t, "/plan") || t.startsWith("/plan ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String after = ctx.text().trim().replaceFirst("(?i)^/plan", "").trim();
        if (after.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), usage());
            return;
        }

        var requester = Requester.builder()
            .telegramUserId(ctx.userId())
            .telegramUsername(ctx.username())
            .telegramChatId(ctx.chatId())
            .build();

        Optional<WorkspaceResolutionService.Resolution> resolution =
            workspaceResolutionService.resolveWorkspaceAndPrompt(ctx, after, "plan");
        if (resolution.isEmpty()) {
            return;
        }
        String workspace = resolution.get().workspace();
        String intent = resolution.get().prompt();

        try {
            String cliModel = resolution.get().cliModel();
            var job = planManagement.createPlan(intent, requester, workspace, cliModel);
            var msg = new StringBuilder();
            msg.append("Plan Job #").append(job.getId()).append(" started. Analyzing...");
            msg.append("\n\nWorkspace: ").append(workspace);
            if (cliModel != null && !cliModel.isBlank()) {
                msg.append("\nModel: ").append(cliModel);
            }
            msg.append("\n\nYou'll be notified when the agent responds.");
            telegramBotClient.sendPlain(ctx.chatId(), msg.toString());
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error creating plan: " + e.getMessage());
        }
    }

    private static String usage() {
        return """
            Usage
            /plan <prompt> — start a plan in current working directory
            /plan @folder <prompt> — plan in a subfolder of current cwd
            /plan @alias <prompt> — use model alias (app.cursor.telegram-model-aliases), if first @token matches
            /plan @alias @folder <prompt> — model then folder

            Example: /plan refactor the auth module
            Example: /plan @myapp propose a caching strategy
            /models — list Cursor CLI model ids for aliases""".trim();
    }
}
