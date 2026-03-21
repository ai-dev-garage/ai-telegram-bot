package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.TodoStore;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Updates a linked Todo when its associated Job reaches a terminal state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TodoCompletionHook {

    private final TodoStore todoStore;

    public void onJobCompleted(Long jobId) {
        todoStore.findByLinkedJobId(jobId).ifPresent(todo -> {
            todo.setStatus(TodoStatus.DONE);
            todoStore.save(todo);
            log.info("Todo #{} auto-marked DONE (Job #{} succeeded)", todo.getId(), jobId);
        });
    }

    public void onJobFailed(Long jobId) {
        todoStore.findByLinkedJobId(jobId).ifPresent(todo -> {
            todo.setStatus(TodoStatus.OPEN);
            todo.setLinkedJobId(null);
            todoStore.save(todo);
            log.info("Todo #{} reverted to OPEN (Job #{} failed)", todo.getId(), jobId);
        });
    }

    public void onJobCancelled(Long jobId) {
        todoStore.findByLinkedJobId(jobId).ifPresent(todo -> {
            todo.setStatus(TodoStatus.OPEN);
            todo.setLinkedJobId(null);
            todoStore.save(todo);
            log.info("Todo #{} reverted to OPEN (Job #{} cancelled)", todo.getId(), jobId);
        });
    }
}
