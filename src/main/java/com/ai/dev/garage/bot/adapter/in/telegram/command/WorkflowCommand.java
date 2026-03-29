package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.port.in.WorkflowOrchestration;
import com.ai.dev.garage.bot.application.service.JobService;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.WorkflowGraph;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Telegram command for workflow operations: plan, run, status, resume.
 * Formatting logic delegated to {@link WorkflowStatusFormatter}.
 * Callback handling delegated to {@link WorkflowCallbackHandler}.
 */
@Slf4j
@Component
@Order(35)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WorkflowCommand implements TelegramCommand {

    public static final String CALLBACK_PREFIX = "wf:";

    private static final Pattern WHITESPACE_SPLIT = Pattern.compile("\\s+");
    private static final int CMD_SPLIT_LIMIT = 3;

    private final JobManagement jobManagement;
    private final JobService jobService;
    private final WorkflowOrchestration orchestrationService;
    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("workflow", "Multi-step workflow orchestration"));
    }

    @Override
    public List<String> helpLines() {
        return List.of(
            "/workflow plan <intent> — plan a multi-step workflow",
            "/workflow status <id> — show workflow step progress",
            "/workflow resume <id> — resume approved workflow"
        );
    }

    @Override
    public boolean canHandle(String text) {
        return text.startsWith("/workflow");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String[] parts = WHITESPACE_SPLIT.split(ctx.text(), CMD_SPLIT_LIMIT);
        if (parts.length < 2) {
            telegramBotClient.sendPlain(ctx.chatId(), usage());
            return;
        }
        String subcommand = parts[1].toLowerCase(Locale.ROOT);
        String body = parts.length > 2 ? parts[2].trim() : "";

        switch (subcommand) {
            case "plan" -> handlePlan(ctx, body);
            case "status" -> handleStatus(ctx, body);
            case "resume", "run" -> handleResume(ctx, body);
            default -> telegramBotClient.sendPlain(ctx.chatId(), usage());
        }
    }

    private void handlePlan(TelegramCommandContext ctx, String intent) {
        if (intent.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /workflow plan <what you want to do>");
            return;
        }
        String fullIntent = "workflow plan " + intent;
        String cwd = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId()).orElse(null);
        try {
            Job job = jobManagement.createJob(fullIntent,
                Requester.builder()
                    .telegramUserId(ctx.userId())
                    .telegramChatId(ctx.chatId())
                    .telegramUsername(ctx.username())
                    .build(),
                cwd, null);
            telegramBotClient.sendPlain(ctx.chatId(),
                "Workflow #" + job.getId() + " created — planning in progress.\n"
                    + "You'll be notified when the plan is ready for review.");
        } catch (Exception e) {
            log.error("Failed to create workflow job", e);
            telegramBotClient.sendPlain(ctx.chatId(), "Error creating workflow: " + e.getMessage());
        }
    }

    private void handleStatus(TelegramCommandContext ctx, String idArg) {
        if (idArg.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /workflow status <job_id>");
            return;
        }
        try {
            Job job = jobService.resolveJob(idArg);
            WorkflowGraph graph = orchestrationService.parseGraph(job);
            List<Job> children = jobService.findChildrenByParentId(job.getId());
            String text = WorkflowStatusFormatter.format(job, graph, children);
            sendStatusResponse(ctx.chatId(), job, graph, text);
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }

    private void sendStatusResponse(Long chatId, Job job, WorkflowGraph graph, String text) {
        if (job.getStatus() == JobStatus.AWAITING_INPUT && graph != null) {
            var keyboard = InlineKeyboardBuilder.create()
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Approve",
                        CALLBACK_PREFIX + "approve:" + job.getId()),
                    new InlineKeyboardBuilder.Button("Reject",
                        CALLBACK_PREFIX + "reject:" + job.getId())
                ))
                .build();
            telegramBotClient.sendWithInlineKeyboard(chatId, text, keyboard);
        } else {
            telegramBotClient.sendPlain(chatId, text);
        }
    }

    private void handleResume(TelegramCommandContext ctx, String idArg) {
        if (idArg.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), "Usage: /workflow resume <job_id>");
            return;
        }
        try {
            Job job = jobService.resolveJob(idArg);
            if (job.getStatus() != JobStatus.AWAITING_INPUT) {
                telegramBotClient.sendPlain(ctx.chatId(),
                    "Workflow #" + job.getId() + " is " + job.getStatus() + " — cannot resume.");
                return;
            }
            WorkflowGraph graph = orchestrationService.parseGraph(job);
            if (graph == null) {
                telegramBotClient.sendPlain(ctx.chatId(),
                    "Workflow #" + job.getId() + " has no execution plan yet.");
                return;
            }
            jobService.requeue(job);
            telegramBotClient.sendPlain(ctx.chatId(),
                "Workflow #" + job.getId() + " approved and re-queued for execution.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }

    private static String usage() {
        return """
            Usage:
            /workflow plan <intent> — plan a multi-step workflow
            /workflow status <id> — show workflow step progress
            /workflow resume <id> — resume approved workflow""".stripIndent();
    }
}
