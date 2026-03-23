package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.in.telegram.command.support.WorkspaceResolutionService;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.config.RunnerProperties;
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
@Order(18)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AgentCommand implements TelegramCommand {

    private final JobManagement jobManagement;
    private final JobResponseMapper jobResponseMapper;
    private final TelegramBotClient telegramBotClient;
    private final WorkspaceResolutionService workspaceResolutionService;
    private final RunnerProperties runnerProperties;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("agent", "Run agent task in current directory"));
    }

    @Override
    public List<String> helpLines() {
        return List.of(
            "/agent <prompt> — agent task in current working directory",
            "/agent @folder <prompt> — target a subfolder of cwd",
            "/agent @alias <prompt> — model alias (telegram-model-aliases), if first @token matches");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return Objects.equals(t, "/agent") || t.startsWith("/agent ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String after = ctx.text().trim().replaceFirst("(?i)^/agent", "").trim();
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
            workspaceResolutionService.resolveWorkspaceAndPrompt(ctx, after, "agent");
        if (resolution.isEmpty()) {
            return;
        }
        String workspace = resolution.get().workspace();
        String intent = "agent " + resolution.get().prompt();

        String cliModel = resolution.get().cliModel();
        JobResponse job = jobResponseMapper.toResponse(
            jobManagement.createJob(intent, requester, workspace, cliModel));
        String agentRuntime = normalizeAgentRuntime(runnerProperties.getAgentRuntime());
        var msg = new StringBuilder();
        msg.append("Job #").append(job.getJobId()).append(" received. Classifying…");
        if (!workspace.isBlank()) {
            msg.append("\n\nWorkspace: ").append(workspace);
        }
        if (cliModel != null && !cliModel.isBlank()) {
            msg.append("\nModel: ").append(cliModel);
        }
        if ("claude".equalsIgnoreCase(agentRuntime)) {
            msg.append("\n\n→ Open Claude Code and run: Process pending agent task.");
        } else {
            msg.append("\n\n→ Open Cursor and run: Process pending agent task.");
        }
        if (Objects.equals(job.getStatus(), "pending_cursor")) {
            msg.append("\n\nUse /logs ").append(job.getJobId()).append(" to see agent activity.");
        }
        telegramBotClient.sendPlain(ctx.chatId(), msg.toString());
    }

    private static String usage() {
        return """
            Usage
            /agent <prompt> — run in current working directory
            /agent @folder <prompt> — run in a subfolder of current cwd
            /agent @alias <prompt> — model from app.cursor.telegram-model-aliases

            Example: /agent brief status of the projects
            Example: /agent @myapp brief status
            /models — list Cursor CLI model ids""".trim();
    }

    private static String normalizeAgentRuntime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cursor";
        }
        return raw.trim();
    }
}
