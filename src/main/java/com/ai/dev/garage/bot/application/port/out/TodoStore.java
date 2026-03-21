package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.List;
import java.util.Optional;

public interface TodoStore {

    Todo save(Todo todo);

    Optional<Todo> findById(Long id);

    List<Todo> findAll(int limit, String sortField, String sortDir);

    List<Todo> findByStatus(TodoStatus status, int limit, String sortField, String sortDir);

    Optional<Todo> findByLinkedJobId(Long jobId);
}
