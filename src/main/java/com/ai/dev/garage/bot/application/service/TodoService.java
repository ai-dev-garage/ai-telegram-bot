package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.port.in.TodoManagement;
import com.ai.dev.garage.bot.application.port.out.TodoStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService implements TodoManagement {

    private final TodoStore todoStore;
    private final JobManagement jobManagement;
    private final PlanManagement planManagement;

    @Override
    @Transactional
    public Todo createTodo(String title, String description, TodoSource source,
                           Requester requester, String workspace) {
        Todo todo = Todo.builder()
            .title(title)
            .description(description)
            .source(source)
            .workspace(workspace)
            .requester(Requester.builder()
                .telegramUserId(requester.getTelegramUserId())
                .telegramChatId(requester.getTelegramChatId())
                .telegramUsername(requester.getTelegramUsername())
                .build())
            .build();
        Todo saved = todoStore.save(todo);
        log.info("Todo #{} created from {}: {}", saved.getId(), source, title);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> listTodos(TodoStatus status, int limit, String sortField, String sortDir) {
        if (status != null) {
            return todoStore.findByStatus(status, limit, sortField, sortDir);
        }
        return todoStore.findAll(limit, sortField, sortDir);
    }

    @Override
    @Transactional(readOnly = true)
    public Todo getTodo(long id) {
        return loadTodoOrThrow(id);
    }

    private Todo loadTodoOrThrow(long id) {
        return todoStore.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Todo not found: " + id));
    }

    @Override
    @Transactional
    public Todo updateTodo(long id, String title, String description) {
        Todo todo = loadTodoOrThrow(id);
        if (title != null && !title.isBlank()) {
            todo.setTitle(title);
        }
        if (description != null) {
            todo.setDescription(description);
        }
        return todoStore.save(todo);
    }

    @Override
    @Transactional
    public Todo markDone(long id) {
        Todo todo = loadTodoOrThrow(id);
        todo.setStatus(TodoStatus.DONE);
        log.info("Todo #{} marked done", id);
        return todoStore.save(todo);
    }

    @Override
    @Transactional
    public Todo cancel(long id) {
        Todo todo = loadTodoOrThrow(id);
        todo.setStatus(TodoStatus.CANCELLED);
        log.info("Todo #{} cancelled", id);
        return todoStore.save(todo);
    }

    @Override
    @Transactional
    public Job workOn(long todoId, String mode, String workspace) {
        Todo todo = loadTodoOrThrow(todoId);
        if (todo.getStatus() != TodoStatus.OPEN) {
            throw new IllegalStateException(
                "Todo #" + todoId + " is not OPEN (current: " + todo.getStatus() + ")");
        }

        String effectiveWorkspace = (workspace != null && !workspace.isBlank())
            ? workspace : todo.getWorkspace();
        Requester requester = todo.getRequester();

        Job job;
        if ("plan".equalsIgnoreCase(mode)) {
            job = planManagement.createPlan(todo.getTitle(), requester, effectiveWorkspace);
        } else {
            String intent = "agent " + todo.getTitle();
            job = jobManagement.createJob(intent, requester, effectiveWorkspace);
        }

        todo.setLinkedJobId(job.getId());
        todo.setStatus(TodoStatus.IN_PROGRESS);
        todoStore.save(todo);

        log.info("Todo #{} linked to Job #{} (mode={})", todoId, job.getId(), mode);
        return job;
    }
}
