package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.rest.JobResponseMapper;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import static com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommand.BotCommandInfo;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StatusCommand implements TelegramCommand {

    private final JobManagement jobManagement;
    private final JobResponseMapper jobResponseMapper;
    private final TelegramBotClient telegramBotClient;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("status", "Job status"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/status [id|last] — job list or one job");
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/status");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String arg = ctx.text().replaceFirst("^/status", "").trim();
        if (arg.isBlank()) {
            List<JobResponse> jobs = jobManagement.listJobs(5).stream().map(jobResponseMapper::toResponse).toList();
            StringBuilder sb = new StringBuilder();
            for (JobResponse j : jobs) {
                sb.append("• ").append(j.getJobId()).append(" — ").append(j.getStatus()).append(" (")
                    .append(j.getTaskType()).append(")\n");
            }
            telegramBotClient.sendPlain(ctx.chatId(), sb.length() == 0 ? "No jobs." : sb.toString());
            return;
        }
        JobResponse j = jobResponseMapper.toResponse(jobManagement.getJob(arg));
        String body = "Job #" + j.getJobId() + " · " + j.getStatus() + " · " + j.getTaskType()
            + "\nRisk: " + j.getRiskLevel()
            + "\nIntent: " + j.getIntent()
            + formatAgentQueuedHint(j)
            + formatResult(j);
        telegramBotClient.sendPlain(ctx.chatId(), body);
    }

    private static String formatAgentQueuedHint(JobResponse j) {
        if (!"queued".equalsIgnoreCase(String.valueOf(j.getStatus()))) {
            return "";
        }
        if (!"agent_task".equalsIgnoreCase(String.valueOf(j.getTaskType()))) {
            return "";
        }
        return "\n\nAgent handoff: this job is not started by the poll worker. "
            + "Run \"Process pending agent task\" in Cursor/Claude (or drop a result file). "
            + "/approve is not used for agent tasks — they are auto-approved.";
    }

    private String formatResult(JobResponse response) {
        Map<String, Object> result = response.getResult();
        if (result == null || result.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (result.get("summary") != null) {
            builder.append("\n\nResult: ").append(result.get("summary"));
        }
        if (result.get("error") != null) {
            builder.append("\nError: ").append(result.get("error"));
        }
        return builder.toString();
    }
}
