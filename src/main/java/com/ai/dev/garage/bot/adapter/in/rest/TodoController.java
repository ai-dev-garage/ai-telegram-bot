package com.ai.dev.garage.bot.adapter.in.rest;

import com.ai.dev.garage.bot.adapter.in.rest.dto.CreateTodoRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.JobResponse;
import com.ai.dev.garage.bot.adapter.in.rest.dto.TodoResponse;
import com.ai.dev.garage.bot.adapter.in.rest.dto.UpdateTodoRequest;
import com.ai.dev.garage.bot.adapter.in.rest.dto.WorkOnTodoRequest;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class TodoController {

    private final TodoManagement todoManagement;
    private final TodoResponseMapper todoResponseMapper;
    private final JobResponseMapper jobResponseMapper;

    @GetMapping("/todos")
    public Map<String, Object> listTodos(
            @RequestParam(required = false) TodoStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        List<TodoResponse> todos = todoManagement.listTodos(status, limit, sort, dir)
            .stream().map(todoResponseMapper::toResponse).toList();
        return Map.of("todos", todos);
    }

    @GetMapping("/todos/{id}")
    public TodoResponse getTodo(@PathVariable long id) {
        return todoResponseMapper.toResponse(todoManagement.getTodo(id));
    }

    @PostMapping("/todos")
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse createTodo(@Valid @RequestBody CreateTodoRequest request) {
        Requester webRequester = Requester.builder()
            .telegramUserId(0L)
            .telegramChatId(0L)
            .telegramUsername("web")
            .build();
        return todoResponseMapper.toResponse(todoManagement.createTodo(
            request.getTitle(), request.getDescription(),
            TodoSource.WEB, webRequester, request.getWorkspace()));
    }

    @PutMapping("/todos/{id}")
    public TodoResponse updateTodo(@PathVariable long id, @RequestBody UpdateTodoRequest request) {
        return todoResponseMapper.toResponse(todoManagement.updateTodo(id, request.getTitle(), request.getDescription()));
    }

    @PostMapping("/todos/{id}/done")
    public TodoResponse markDone(@PathVariable long id) {
        return todoResponseMapper.toResponse(todoManagement.markDone(id));
    }

    @PostMapping("/todos/{id}/cancel")
    public TodoResponse cancelTodo(@PathVariable long id) {
        return todoResponseMapper.toResponse(todoManagement.cancel(id));
    }

    @PostMapping("/todos/{id}/work")
    public JobResponse workOnTodo(@PathVariable long id, @Valid @RequestBody WorkOnTodoRequest request) {
        return jobResponseMapper.toResponse(
            todoManagement.workOn(id, request.getMode(), request.getWorkspace()));
    }
}
