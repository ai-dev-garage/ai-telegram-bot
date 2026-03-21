package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.config.RunnerProperties;
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
@Order(18)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AgentCommand implements TelegramCommand {

    private final JobManagement jobManagement;
    private final JobResponseMapper jobResponseMapper;
    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final RunnerProperties runnerProperties;
    private final AllowedPathValidator allowedPathValidator;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("agent", "Run agent task in current directory"));
    }

    @Override
    public List<String> helpLines() {
        return List.of(
            "/agent <prompt> — agent task in current working directory",
            "/agent @folder <prompt> — target a subfolder of cwd");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().toLowerCase();
        return t.equals("/agent") || t.startsWith("/agent ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String after = ctx.text().trim().replaceFirst("(?i)^/agent", "").trim();
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
        String workspace = null;

        String firstToken = after.split("\\s+", 2)[0];
        if (firstToken.startsWith("@") && after.length() > firstToken.length()) {
            String folderName = firstToken.substring(1);
            workspace = resolveAtFolder(ctx, folderName);
            if (workspace == null) {
                return;
            }
            intent = "agent " + after.substring(firstToken.length()).trim();
        } else {
            workspace = requireCwd(ctx);
            if (workspace == null) {
                return;
            }
            intent = "agent " + after;
        }

        JobResponse job = jobResponseMapper.toResponse(jobManagement.createJob(intent, requester, workspace));
        String agentRuntime = normalizeAgentRuntime(runnerProperties.getAgentRuntime());
        StringBuilder msg = new StringBuilder();
        msg.append("Job #").append(job.getJobId()).append(" received. Classifying…");
        if (workspace != null && !workspace.isBlank()) {
            msg.append("\n\nWorkspace: ").append(workspace);
        }
        if ("claude".equalsIgnoreCase(agentRuntime)) {
            msg.append("\n\n→ Open Claude Code and run: Process pending agent task.");
        } else {
            msg.append("\n\n→ Open Cursor and run: Process pending agent task.");
        }
        if ("pending_cursor".equals(job.getStatus())) {
            msg.append("\n\nUse /logs ").append(job.getJobId()).append(" to see agent activity.");
        }
        telegramBotClient.sendPlain(ctx.chatId(), msg.toString());
    }

    private String requireCwd(TelegramCommandContext ctx) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty() || cwd.get().isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "No working folder selected. Use /nav to pick one, or:\n/agent @folder <prompt>");
            return null;
        }
        return cwd.get();
    }

    private String resolveAtFolder(TelegramCommandContext ctx, String folderName) {
        Optional<String> cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        if (cwd.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "No working folder selected. Use /nav first, then /agent @folder <prompt>.");
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
            /agent <prompt> — run in current working directory
            /agent @folder <prompt> — run in a subfolder of current cwd

            Example: /agent brief status of the projects
            Example: /agent @myapp brief status""".trim();
    }

    private static String normalizeAgentRuntime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cursor";
        }
        return raw.trim();
    }
}
