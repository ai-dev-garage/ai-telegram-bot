package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(17)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlanCommand implements TelegramCommand {

    private final PlanManagement planManagement;
    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final AllowedPathValidator allowedPathValidator;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("plan", "Start a plan session"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/plan <prompt> — start a plan session");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("/plan") || t.startsWith("/plan ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String after = ctx.text().trim().replaceFirst("(?i)^/plan", "").trim();
        if (after.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), usage());
            return;
        }

        Requester requester = Requester.builder()
            .telegramUserId(ctx.userId())
            .telegramUsername(ctx.username())
            .telegramChatId(ctx.chatId())
            .build();

        String intent;
        String workspace;

        String firstToken = after.split("\\s+", 2)[0];
        if (firstToken.startsWith("@") && after.length() > firstToken.length()) {
            String folderName = firstToken.substring(1);
            workspace = resolveAtFolder(ctx, folderName);
            if (workspace == null) return;
            intent = after.substring(firstToken.length()).trim();
        } else {
            workspace = requireCwd(ctx);
            if (workspace == null) return;
            intent = after;
        }

        try {
            Job job = planManagement.createPlan(intent, requester, workspace);
            StringBuilder msg = new StringBuilder();
            msg.append("Plan Job #").append(job.getId()).append(" started. Analyzing...");
            if (workspace != null && !workspace.isBlank()) {
                msg.append("\n\nWorkspace: ").append(workspace);
            }
            msg.append("\n\nYou'll be notified when the agent responds.");
            telegramBotClient.sendPlain(ctx.chatId(), msg.toString());
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error creating plan: " + e.getMessage());
        }
    }

    private String requireCwd(TelegramCommandContext ctx) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty() || cwd.get().isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "No working folder selected. Use /nav to pick one, or:\n/plan @folder <prompt>");
            return null;
        }
        return cwd.get();
    }

    private String resolveAtFolder(TelegramCommandContext ctx, String folderName) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "No working folder selected. Use /nav first, then /plan @folder <prompt>.");
            return null;
        }
        try {
            Path target = Path.of(cwd.get(), folderName).toRealPath();
            if (!Files.isDirectory(target)) {
                telegramBotClient.sendPlain(ctx.chatId(), "Not a directory: " + folderName);
                return null;
            }
            if (!allowedPathValidator.isAllowedCwd(target.toString())) {
                telegramBotClient.sendPlain(ctx.chatId(), "Folder not under allowed path: " + folderName);
                return null;
            }
            return target.toString();
        } catch (IOException e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Folder not found: " + folderName);
            return null;
        }
    }

    private static String usage() {
        return """
            Usage
            /plan <prompt> \u2014 start a plan in current working directory
            /plan @folder <prompt> \u2014 plan in a subfolder of current cwd

            Example: /plan refactor the auth module
            Example: /plan @myapp propose a caching strategy""".trim();
    }
}
