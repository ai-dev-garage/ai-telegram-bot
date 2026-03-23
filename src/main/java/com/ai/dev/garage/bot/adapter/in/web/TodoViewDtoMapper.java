package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.adapter.in.web.dto.TodoSummaryView;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TodoViewDtoMapper {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private static final Map<TodoStatus, String> STATUS_BADGE = Map.of(
        TodoStatus.OPEN, "bg-blue-500 text-white",
        TodoStatus.IN_PROGRESS, "bg-yellow-500 text-white",
        TodoStatus.DONE, "bg-green-500 text-white",
        TodoStatus.CANCELLED, "bg-gray-600 text-white"
    );

    private static final Set<TodoStatus> ACTIONABLE = Set.of(TodoStatus.OPEN, TodoStatus.IN_PROGRESS);

    public List<TodoSummaryView> toSummaryList(List<Todo> todos) {
        return todos.stream().map(this::toSummary).toList();
    }

    public TodoSummaryView toSummary(Todo todo) {
        return new TodoSummaryView(
            todo.getId(),
            todo.getTitle(),
            todo.getDescription() != null ? todo.getDescription() : "",
            todo.getStatus().name(),
            STATUS_BADGE.getOrDefault(todo.getStatus(), "bg-gray-400 text-white"),
            todo.getSource().name(),
            todo.getWorkspace(),
            todo.getLinkedJobId(),
            formatDateTime(todo.getCreatedAt()),
            formatDateTime(todo.getUpdatedAt()),
            todo.getStatus() == TodoStatus.OPEN,
            ACTIONABLE.contains(todo.getStatus()));
    }

    private static String formatDateTime(OffsetDateTime dt) {
        return dt != null ? dt.format(DATE_TIME) : "—";
    }
}
