package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.TodoStore;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoCompletionHookTest {

    @Mock
    private TodoStore todoStore;

    @InjectMocks
    private TodoCompletionHook todoCompletionHook;

    private static Todo linkedTodo(long todoId, long jobId) {
        return Todo.builder()
            .id(todoId)
            .title("Test todo")
            .status(TodoStatus.IN_PROGRESS)
            .source(TodoSource.WEB)
            .linkedJobId(jobId)
            .build();
    }

    @Test
    void shouldMarkDoneWhenJobCompleted() {
        Todo todo = linkedTodo(1, 100);
        when(todoStore.findByLinkedJobId(100L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        todoCompletionHook.onJobCompleted(100L);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.DONE);
        verify(todoStore).save(todo);
    }

    @Test
    void shouldRevertToOpenWhenJobFailed() {
        Todo todo = linkedTodo(2, 200);
        when(todoStore.findByLinkedJobId(200L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        todoCompletionHook.onJobFailed(200L);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.OPEN);
        assertThat(todo.getLinkedJobId()).isNull();
        verify(todoStore).save(todo);
    }

    @Test
    void shouldRevertToOpenWhenJobCancelled() {
        Todo todo = linkedTodo(3, 300);
        when(todoStore.findByLinkedJobId(300L)).thenReturn(Optional.of(todo));
        when(todoStore.save(todo)).thenReturn(todo);

        todoCompletionHook.onJobCancelled(300L);

        assertThat(todo.getStatus()).isEqualTo(TodoStatus.OPEN);
        assertThat(todo.getLinkedJobId()).isNull();
        verify(todoStore).save(todo);
    }

    @Test
    void shouldDoNothingWhenNoLinkedTodoOnJobCompleted() {
        when(todoStore.findByLinkedJobId(999L)).thenReturn(Optional.empty());

        todoCompletionHook.onJobCompleted(999L);

        verify(todoStore, never()).save(any());
    }

    @Test
    void shouldDoNothingWhenNoLinkedTodoOnJobFailed() {
        when(todoStore.findByLinkedJobId(999L)).thenReturn(Optional.empty());

        todoCompletionHook.onJobFailed(999L);

        verify(todoStore, never()).save(any());
    }
}
