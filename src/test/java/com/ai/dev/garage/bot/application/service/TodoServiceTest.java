package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.port.out.TodoStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoStore todoStore;

    @Mock
    private JobManagement jobManagement;

    @Mock
    private PlanManagement planManagement;

    @InjectMocks
    private TodoService todoService;

    private static Requester webRequester() {
        return Requester.builder()
            .telegramUserId(0L)
            .telegramChatId(0L)
            .telegramUsername("web")
            .build();
    }

    private static Todo openTodo(long id) {
        return Todo.builder()
            .id(id)
            .title("Fix bug")
            .status(TodoStatus.OPEN)
            .source(TodoSource.WEB)
            .requester(webRequester())
            .workspace("/projects/app")
            .build();
    }

    @Test
    void shouldPersistTodoWhenCreated() {
        when(todoStore.save(any(Todo.class))).thenAnswer(inv -> {
            Todo t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        Todo result = todoService.createTodo("Fix bug", "desc", TodoSource.WEB, webRequester(), "/ws");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Fix bug");
        assertThat(result.getDescription()).isEqualTo("desc");
        assertThat(result.getSource()).isEqualTo(TodoSource.WEB);
        assertThat(result.getWorkspace()).isEqualTo("/ws");
        verify(todoStore).save(any(Todo.class));
    }

    @Test
    void shouldDelegateToStoreWhenListTodos() {
        var expected = List.of(openTodo(1));
        when(todoStore.findAll(20, "createdAt", "desc")).thenReturn(expected);

        var result = todoService.listTodos(null, 20, "createdAt", "desc");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldFilterByStatusWhenListTodosWithStatus() {
        var expected = List.of(openTodo(1));
        when(todoStore.findByStatus(TodoStatus.OPEN, 10, "id", "asc")).thenReturn(expected);

        var result = todoService.listTodos(TodoStatus.OPEN, 10, "id", "asc");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldThrowWhenGetTodoNotFound() {
        when(todoStore.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> todoService.getTodo(99))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void shouldUpdateTitleAndDescriptionWhenUpdateTodo() {
        Todo todo = openTodo(5);
        when(todoStore.findById(5L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        Todo result = todoService.updateTodo(5, "New title", "New desc");

        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getDescription()).isEqualTo("New desc");
    }

    @Test
    void shouldSetStatusDoneWhenMarkDone() {
        Todo todo = openTodo(3);
        when(todoStore.findById(3L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        Todo result = todoService.markDone(3);

        assertThat(result.getStatus()).isEqualTo(TodoStatus.DONE);
    }

    @Test
    void shouldSetStatusCancelledWhenCancel() {
        Todo todo = openTodo(4);
        when(todoStore.findById(4L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        Todo result = todoService.cancel(4);

        assertThat(result.getStatus()).isEqualTo(TodoStatus.CANCELLED);
    }

    @Test
    void shouldCreateAgentJobWhenWorkOnWithAgentMode() {
        Todo todo = openTodo(10);
        when(todoStore.findById(10L)).thenReturn(Optional.of(todo));
        var job = Job.builder().id(100L).build();
        when(jobManagement.createJob("agent Fix bug", todo.getRequester(), "/projects/app"))
            .thenReturn(job);
        when(todoStore.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = todoService.workOn(10, "agent", null);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(todo.getLinkedJobId()).isEqualTo(100L);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void shouldCreatePlanJobWhenWorkOnWithPlanMode() {
        Todo todo = openTodo(11);
        when(todoStore.findById(11L)).thenReturn(Optional.of(todo));
        var job = Job.builder().id(200L).build();
        when(planManagement.createPlan("Fix bug", todo.getRequester(), "/projects/app"))
            .thenReturn(job);
        when(todoStore.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = todoService.workOn(11, "plan", null);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(todo.getLinkedJobId()).isEqualTo(200L);
        assertThat(todo.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
    }

    @Test
    void shouldPreferExplicitWorkspaceOverTodoWorkspace() {
        Todo todo = openTodo(12);
        when(todoStore.findById(12L)).thenReturn(Optional.of(todo));
        var job = Job.builder().id(300L).build();
        when(jobManagement.createJob("agent Fix bug", todo.getRequester(), "/override/path"))
            .thenReturn(job);
        when(todoStore.save(any(Todo.class))).thenAnswer(inv -> inv.getArgument(0));

        todoService.workOn(12, "agent", "/override/path");

        verify(jobManagement).createJob("agent Fix bug", todo.getRequester(), "/override/path");
    }

    @Test
    void shouldThrowWhenWorkOnNonOpenTodo() {
        Todo todo = openTodo(13);
        todo.setStatus(TodoStatus.IN_PROGRESS);
        when(todoStore.findById(13L)).thenReturn(Optional.of(todo));

        assertThatThrownBy(() -> todoService.workOn(13, "agent", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not OPEN");
    }
}
