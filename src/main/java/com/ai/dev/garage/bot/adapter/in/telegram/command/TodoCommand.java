package com.ai.dev.garage.bot.adapter.in.telegram.command;

import com.ai.dev.garage.bot.adapter.in.telegram.InlineKeyboardBuilder;
import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ai.dev.garage.bot.adapter.in.telegram.command.TelegramCommand.BotCommandInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(15)
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TodoCommand implements TelegramCommand {

    public static final String CALLBACK_PREFIX = "todo:";
    private static final Pattern ID_PATTERN = Pattern.compile("^#?(\\d+)$");
    private static final Pattern DONE_PATTERN = Pattern.compile("(?i)^done\\s+#?(\\d+)$");
    private static final Pattern CANCEL_PATTERN = Pattern.compile("(?i)^cancel\\s+#?(\\d+)$");
    private static final Pattern WORK_PATTERN = Pattern.compile("(?i)^work\\s+#?(\\d+)$");

    private final TodoManagement todoManagement;
    private final TelegramBotClient telegramBotClient;
    private final NavigationStateStore navigationStateStore;

    @Override
    public List<BotCommandInfo> botCommands() {
        return List.of(
            new BotCommandInfo("todo", "Create a todo or manage by ID"),
            new BotCommandInfo("todos", "List all todos"));
    }

    @Override
    public List<String> helpLines() {
        return List.of(
            "/todo <text> — create a new todo",
            "/todos — list all todos",
            "/todo <id> — view todo details",
            "/todo done <id> — mark todo as done",
            "/todo cancel <id> — cancel todo",
            "/todo work <id> — execute todo (agent or plan)");
    }

    @Override
    public boolean canHandle(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("/todo") || t.startsWith("/todo ")
            || t.equals("/todos");
    }

    @Override
    public void handle(TelegramCommandContext ctx) {
        String trimmed = ctx.text().trim();

        if (trimmed.equalsIgnoreCase("/todos")) {
            handleList(ctx);
            return;
        }

        String after = trimmed.replaceFirst("(?i)^/todo", "").trim();
        if (after.isBlank()) {
            telegramBotClient.sendPlain(ctx.chatId(), usage());
            return;
        }

        Matcher doneMatcher = DONE_PATTERN.matcher(after);
        if (doneMatcher.matches()) {
            handleDone(ctx, Long.parseLong(doneMatcher.group(1)));
            return;
        }

        Matcher cancelMatcher = CANCEL_PATTERN.matcher(after);
        if (cancelMatcher.matches()) {
            handleCancel(ctx, Long.parseLong(cancelMatcher.group(1)));
            return;
        }

        Matcher workMatcher = WORK_PATTERN.matcher(after);
        if (workMatcher.matches()) {
            handleWorkPrompt(ctx, Long.parseLong(workMatcher.group(1)));
            return;
        }

        Matcher idMatcher = ID_PATTERN.matcher(after);
        if (idMatcher.matches()) {
            handleDetail(ctx, Long.parseLong(idMatcher.group(1)));
            return;
        }

        handleCreate(ctx, after);
    }

    private void handleList(TelegramCommandContext ctx) {
        List<Todo> todos = todoManagement.listTodos(null, 20);
        if (todos.isEmpty()) {
            telegramBotClient.sendPlain(ctx.chatId(), "No todos found. Create one with /todo <text>");
            return;
        }
        StringBuilder sb = new StringBuilder("Your todos:\n\n");
        for (Todo t : todos) {
            sb.append(statusIcon(t.getStatus()))
                .append(" #").append(t.getId())
                .append(" ").append(t.getTitle());
            if (t.getLinkedJobId() != null) {
                sb.append(" (Job #").append(t.getLinkedJobId()).append(")");
            }
            sb.append("\n");
        }
        sb.append("\nUse /todo <id> for details, /todo work <id> to execute");
        telegramBotClient.sendPlain(ctx.chatId(), sb.toString());
    }

    private void handleCreate(TelegramCommandContext ctx, String title) {
        Requester requester = Requester.builder()
            .telegramUserId(ctx.userId())
            .telegramUsername(ctx.username())
            .telegramChatId(ctx.chatId())
            .build();
        Optional<String> workspace = navigationStateStore.getSelectedPath(ctx.chatId(), ctx.userId());
        Todo todo = todoManagement.createTodo(
            title, null, TodoSource.TELEGRAM, requester,
            workspace.orElse(null));
        String msg = "Todo #" + todo.getId() + " created: " + todo.getTitle();
        if (workspace.isPresent()) {
            msg += "\nWorkspace: " + workspace.get();
        }
        telegramBotClient.sendPlain(ctx.chatId(), msg);
    }

    private void handleDetail(TelegramCommandContext ctx, long id) {
        try {
            Todo todo = todoManagement.getTodo(id);
            StringBuilder sb = new StringBuilder();
            sb.append(statusIcon(todo.getStatus()))
                .append(" Todo #").append(todo.getId())
                .append("\n\nTitle: ").append(todo.getTitle());
            if (todo.getDescription() != null && !todo.getDescription().isBlank()) {
                sb.append("\nDescription: ").append(todo.getDescription());
            }
            sb.append("\nStatus: ").append(todo.getStatus());
            sb.append("\nSource: ").append(todo.getSource());
            if (todo.getWorkspace() != null) {
                sb.append("\nWorkspace: ").append(todo.getWorkspace());
            }
            if (todo.getLinkedJobId() != null) {
                sb.append("\nLinked Job: #").append(todo.getLinkedJobId());
            }
            sb.append("\nCreated: ").append(todo.getCreatedAt());

            if (todo.getStatus() == TodoStatus.OPEN) {
                var keyboard = InlineKeyboardBuilder.create()
                    .row(List.of(
                        new InlineKeyboardBuilder.Button("Done", CALLBACK_PREFIX + "done:" + id),
                        new InlineKeyboardBuilder.Button("Cancel", CALLBACK_PREFIX + "cancel:" + id)))
                    .row(List.of(
                        new InlineKeyboardBuilder.Button("Work (Agent)", CALLBACK_PREFIX + "work:agent:" + id),
                        new InlineKeyboardBuilder.Button("Work (Plan)", CALLBACK_PREFIX + "work:plan:" + id)))
                    .build();
                telegramBotClient.sendWithInlineKeyboard(ctx.chatId(), sb.toString(), keyboard);
            } else {
                telegramBotClient.sendPlain(ctx.chatId(), sb.toString());
            }
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Todo not found: #" + id);
        }
    }

    private void handleDone(TelegramCommandContext ctx, long id) {
        try {
            todoManagement.markDone(id);
            telegramBotClient.sendPlain(ctx.chatId(), "Todo #" + id + " marked done.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }

    private void handleCancel(TelegramCommandContext ctx, long id) {
        try {
            todoManagement.cancel(id);
            telegramBotClient.sendPlain(ctx.chatId(), "Todo #" + id + " cancelled.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }

    private void handleWorkPrompt(TelegramCommandContext ctx, long id) {
        try {
            Todo todo = todoManagement.getTodo(id);
            if (todo.getStatus() != TodoStatus.OPEN) {
                telegramBotClient.sendPlain(ctx.chatId(),
                    "Todo #" + id + " is " + todo.getStatus() + ", cannot work on it.");
                return;
            }
            var keyboard = InlineKeyboardBuilder.create()
                .row(List.of(
                    new InlineKeyboardBuilder.Button("Agent", CALLBACK_PREFIX + "work:agent:" + id),
                    new InlineKeyboardBuilder.Button("Plan", CALLBACK_PREFIX + "work:plan:" + id)))
                .build();
            telegramBotClient.sendWithInlineKeyboard(ctx.chatId(),
                "How should I work on Todo #" + id + " (" + todo.getTitle() + ")?",
                keyboard);
        } catch (Exception e) {
            telegramBotClient.sendPlain(ctx.chatId(), "Error: " + e.getMessage());
        }
    }

    public void handleWorkCallback(Long chatId, Long userId, long todoId, String mode) {
        try {
            Optional<String> workspace = navigationStateStore.getSelectedPath(chatId, userId);
            Job job = todoManagement.workOn(todoId, mode, workspace.orElse(null));
            StringBuilder msg = new StringBuilder();
            msg.append("Todo #").append(todoId).append(" -> Job #").append(job.getId());
            msg.append(" (").append(mode).append(" mode)");
            if ("plan".equalsIgnoreCase(mode)) {
                msg.append("\nYou'll be notified when the plan agent responds.");
            } else {
                msg.append("\nAgent task created. Open Cursor to process it.");
            }
            telegramBotClient.sendPlain(chatId, msg.toString());
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    public void handleDoneCallback(Long chatId, long todoId) {
        try {
            todoManagement.markDone(todoId);
            telegramBotClient.sendPlain(chatId, "Todo #" + todoId + " marked done.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    public void handleCancelCallback(Long chatId, long todoId) {
        try {
            todoManagement.cancel(todoId);
            telegramBotClient.sendPlain(chatId, "Todo #" + todoId + " cancelled.");
        } catch (Exception e) {
            telegramBotClient.sendPlain(chatId, "Error: " + e.getMessage());
        }
    }

    private static String statusIcon(TodoStatus status) {
        return switch (status) {
            case OPEN -> "\u2B1C";
            case IN_PROGRESS -> "\u23F3";
            case DONE -> "\u2705";
            case CANCELLED -> "\u274C";
        };
    }

    private static String usage() {
        return """
            Usage
            /todo <text> \u2014 create a new todo
            /todos \u2014 list all todos
            /todo <id> \u2014 view todo details
            /todo done <id> \u2014 mark as done
            /todo cancel <id> \u2014 cancel todo
            /todo work <id> \u2014 execute todo (agent or plan)""".trim();
    }
}
