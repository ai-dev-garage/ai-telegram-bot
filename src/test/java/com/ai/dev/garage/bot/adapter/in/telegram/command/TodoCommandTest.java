package com.ai.dev.garage.bot.adapter.in.telegram.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ai.dev.garage.bot.adapter.in.telegram.NavigationStateStore;
import com.ai.dev.garage.bot.adapter.in.telegram.TelegramBotClient;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodoCommandTest {

    @Mock
    private TodoManagement todoManagement;

    @Mock
    private TelegramBotClient telegramBotClient;

    private NavigationStateStore navigationStateStore;
    private TodoCommand todoCommand;

    @BeforeEach
    void setUp() {
        navigationStateStore = new NavigationStateStore();
        todoCommand = new TodoCommand(todoManagement, telegramBotClient, navigationStateStore);
    }

    @Test
    void shouldCreateTodoWhenTextProvided() {
        Todo created = Todo.builder().id(1L).title("Buy milk").source(TodoSource.TELEGRAM).build();
        when(todoManagement.createTodo(eq("Buy milk"), eq(null), eq(TodoSource.TELEGRAM),
            any(Requester.class), eq(null)))
            .thenReturn(created);

        todoCommand.handle(ctx("/todo Buy milk"));

        verify(todoManagement).createTodo(eq("Buy milk"), eq(null), eq(TodoSource.TELEGRAM),
            any(Requester.class), eq(null));
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("#1")));
    }

    @Test
    void shouldListTodosOnSlashTodos() {
        Todo todo = Todo.builder().id(5L).title("Task").status(TodoStatus.OPEN)
            .source(TodoSource.WEB).build();
        when(todoManagement.listTodos(null, 20)).thenReturn(List.of(todo));

        todoCommand.handle(ctx("/todos"));

        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg ->
            msg.contains("#5") && msg.contains("Task")));
    }

    @Test
    void shouldShowUsageWhenTodoWithNoArgs() {
        todoCommand.handle(ctx("/todo"));

        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("Usage")));
    }

    @Test
    void shouldMarkDoneWhenDoneCommand() {
        todoCommand.handle(ctx("/todo done 3"));

        verify(todoManagement).markDone(3);
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("done")));
    }

    @Test
    void shouldCancelWhenCancelCommand() {
        todoCommand.handle(ctx("/todo cancel 4"));

        verify(todoManagement).cancel(4);
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("cancelled")));
    }

    @Test
    void shouldShowDetailWhenIdProvided() {
        Todo todo = Todo.builder().id(7L).title("Detail task").status(TodoStatus.OPEN)
            .source(TodoSource.TELEGRAM).build();
        when(todoManagement.getTodo(7)).thenReturn(todo);

        todoCommand.handle(ctx("/todo 7"));

        verify(telegramBotClient).sendWithInlineKeyboard(eq(10L),
            argThat(msg -> msg.contains("Detail task")), any());
    }

    @Test
    void shouldHandleWorkCallback() {
        Job job = Job.builder().id(50L).build();
        when(todoManagement.workOn(1, "agent", null)).thenReturn(job);

        todoCommand.handleWorkCallback(10L, 20L, 1, "agent");

        verify(todoManagement).workOn(1, "agent", null);
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("Job #50")));
    }

    @Test
    void shouldHandleDoneCallback() {
        todoCommand.handleDoneCallback(10L, 5);

        verify(todoManagement).markDone(5);
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("done")));
    }

    @Test
    void shouldHandleCancelCallback() {
        todoCommand.handleCancelCallback(10L, 6);

        verify(todoManagement).cancel(6);
        verify(telegramBotClient).sendPlain(eq(10L), argThat(msg -> msg.contains("cancelled")));
    }

    @Test
    void shouldIncludeWorkspaceWhenNavSelected() {
        navigationStateStore.setSelectedPath(10L, 20L, "/my/workspace");
        Todo created = Todo.builder().id(2L).title("Work").source(TodoSource.TELEGRAM).build();
        when(todoManagement.createTodo(eq("Work"), eq(null), eq(TodoSource.TELEGRAM),
            any(Requester.class), eq("/my/workspace")))
            .thenReturn(created);

        todoCommand.handle(ctx("/todo Work"));

        verify(todoManagement).createTodo(eq("Work"), eq(null), eq(TodoSource.TELEGRAM),
            any(Requester.class), eq("/my/workspace"));
    }

    private static TelegramCommandContext ctx(String text) {
        return new TelegramCommandContext(10L, 20L, "testuser", text);
    }
}
