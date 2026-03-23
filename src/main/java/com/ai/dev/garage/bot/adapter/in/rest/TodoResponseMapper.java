package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.adapter.in.rest.dto.TodoResponse;
import com.ai.dev.garage.bot.domain.Todo;

import org.springframework.stereotype.Component;

@Component
public class TodoResponseMapper {

    public TodoResponse toResponse(Todo todo) {
        return TodoResponse.builder()
            .id(todo.getId())
            .title(todo.getTitle())
            .description(todo.getDescription())
            .status(todo.getStatus().name())
            .source(todo.getSource().name())
            .workspace(todo.getWorkspace())
            .linkedJobId(todo.getLinkedJobId())
            .createdAt(todo.getCreatedAt() != null ? todo.getCreatedAt().toString() : null)
            .updatedAt(todo.getUpdatedAt() != null ? todo.getUpdatedAt().toString() : null)
            .build();
    }
}
