package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoSource;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.List;

public interface TodoManagement {

    Todo createTodo(String title, String description, TodoSource source, Requester requester, String workspace);

    List<Todo> listTodos(TodoStatus status, int limit, String sortField, String sortDir);

    default List<Todo> listTodos(TodoStatus status, int limit) {
        return listTodos(status, limit, "createdAt", "desc");
    }

    Todo getTodo(long id);

    Todo updateTodo(long id, String title, String description);

    Todo markDone(long id);

    Todo cancel(long id);

    /**
     * Execute a todo by creating a Job (AGENT_TASK or PLAN_TASK).
     *
     * @param mode "agent" or "plan"
     * @return the created Job
     */
    Job workOn(long todoId, String mode, String workspace);
}
