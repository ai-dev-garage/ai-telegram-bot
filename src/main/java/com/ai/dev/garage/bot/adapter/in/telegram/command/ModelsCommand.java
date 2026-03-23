package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorAgentModelsCliRunner;
import com.ai.dev.garage.bot.application.support.ContentLengthLimits;
import com.ai.dev.garage.bot.config.RunnerProperties;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(19)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelsCommand implements TelegramCommand {

    private static final String CLAUDE_HINT =
        "AGENT_RUNTIME=claude: model listing is not run from this bot. See docs/agent-claude.md for Claude Code model selection.";

    /** Room for truncation ellipsis + hint line under {@link ContentLengthLimits#TELEGRAM_PLAIN_MESSAGE_SAFE}. */
    private static final int TRUNCATION_MESSAGE_SUFFIX_CHARS = 80;

    private final TelegramBotClient telegramBotClient;
    private final RunnerProperties runnerProperties;
    private final ObjectProvider<CursorAgentModelsCliRunner> cursorAgentModelsCliRunner;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(new BotCommandInfo("models", "List Cursor CLI model ids"));
    }

    @Override
    public List<String> helpLines() {
        return List.of("/models — list model ids for app.cursor.telegram-model-aliases");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim().toLowerCase(Locale.ROOT);
        return Objects.equals(t, "/models") || t.startsWith("/models ");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String runtime = runnerProperties.getAgentRuntime();
        if (runtime != null && "claude".equalsIgnoreCase(runtime.trim())) {
            telegramBotClient.sendPlain(ctx.chatId(), CLAUDE_HINT);
            return;
        }
        CursorAgentModelsCliRunner runner = cursorAgentModelsCliRunner.getIfAvailable();
        if (runner == null) {
            telegramBotClient.sendPlain(ctx.chatId(),
                "Models command unavailable (Cursor CLI runner not active).");
            return;
        }
        try {
            String out = runner.runListModels();
            String body = out.isBlank() ? "(empty output)" : out;
            int max = ContentLengthLimits.TELEGRAM_PLAIN_MESSAGE_SAFE;
            if (body.length() > max) {
                body = body.substring(0, max - TRUNCATION_MESSAGE_SUFFIX_CHARS)
                    + "\n\n… (truncated; see docs/agent-cursor.md)";
            }
            telegramBotClient.sendPlain(ctx.chatId(),
                "Cursor CLI models (use exact strings in telegram-model-aliases values):\n\n" + body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted listing Cursor models: {}", e.getMessage());
            telegramBotClient.sendPlain(ctx.chatId(), "Failed to list models: interrupted");
        } catch (Exception e) {
            log.warn("Failed to list Cursor models: {}", e.getMessage());
            telegramBotClient.sendPlain(ctx.chatId(),
                "Failed to list models: " + e.getMessage());
        }
    }
}
