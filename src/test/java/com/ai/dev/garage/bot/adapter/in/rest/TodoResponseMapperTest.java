package com.ai.dev.garage.bot.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.dev.garage.bot.adapter.in.rest.dto.TodoResponse;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TodoResponseMapperTest {

    private final TodoResponseMapper mapper = new TodoResponseMapper();

    @Test
    void shouldMapAllFieldsWhenTodoHasAllValues() {
        OffsetDateTime created = OffsetDateTime.parse("2025-01-01T10:00:00Z");
        OffsetDateTime updated = OffsetDateTime.parse("2025-01-01T11:00:00Z");
        Todo todo = Todo.builder()
            .id(42L)
            .title("Fix CI")
            .description("CI is broken on main")
            .status(TodoStatus.IN_PROGRESS)
            .source(TodoSource.TELEGRAM)
            .workspace("/projects/app")
            .linkedJobId(99L)
            .createdAt(created)
            .updatedAt(updated)
            .build();

        TodoResponse response = mapper.toResponse(todo);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getTitle()).isEqualTo("Fix CI");
        assertThat(response.getDescription()).isEqualTo("CI is broken on main");
        assertThat(response.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.getSource()).isEqualTo("TELEGRAM");
        assertThat(response.getWorkspace()).isEqualTo("/projects/app");
        assertThat(response.getLinkedJobId()).isEqualTo(99L);
        assertThat(response.getCreatedAt()).isEqualTo(created.toString());
        assertThat(response.getUpdatedAt()).isEqualTo(updated.toString());
    }

    @Test
    void shouldHandleNullTimestamps() {
        Todo todo = Todo.builder()
            .id(1L)
            .title("Quick note")
            .status(TodoStatus.OPEN)
            .source(TodoSource.WEB)
            .build();

        TodoResponse response = mapper.toResponse(todo);

        assertThat(response.getCreatedAt()).isNull();
        assertThat(response.getUpdatedAt()).isNull();
        assertThat(response.getDescription()).isNull();
        assertThat(response.getWorkspace()).isNull();
        assertThat(response.getLinkedJobId()).isNull();
    }
}
