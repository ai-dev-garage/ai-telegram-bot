package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.config.RunnerProperties;
import com.ai.dev.garage.bot.domain.Requester;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

@Component
@Order(20)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RunCommand implements TelegramCommand {

    private final JobManagement jobManagement;
    private final JobResponseMapper jobResponseMapper;
    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;
    private final RunnerProperties runnerProperties;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("run", "Submit shell or agent task"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/run <task> — submit intent (shell, agent, or repo)");
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/run");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String intent = ctx.text().replaceFirst("^/run", "").trim();
        if (intent.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "Usage: /run <shell or task> — or /agent <prompt> / /agent <project> <prompt> for Cursor/Claude handoff.");
            return;
        }
        var requester = Requester.builder()
            .telegramUserId(ctx.userId())
            .telegramUsername(ctx.username())
            .telegramChatId(ctx.chatId())
            .build();
        String cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId()).orElse(null);
        JobResponse job = jobResponseMapper.toResponse(jobManagement.createJob(intent, requester, cwd));
        String agentRuntime = normalizeAgentRuntime(runnerProperties.getAgentRuntime());
        var msg = new StringBuilder();
        msg.append("Job #").append(job.getJobId()).append(" received. Classifying…");
        if (cwd != null && !cwd.isBlank()) {
            msg.append("\n\nWorkspace: ").append(cwd);
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

    private static String normalizeAgentRuntime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cursor";
        }
        return raw.trim();
    }
}
