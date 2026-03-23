package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.TodoStore;
import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TodoStoreJpaAdapter implements TodoStore {

    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "status", "title");

    private final TodoJpaRepository todoJpaRepository;

    @Override
    public Todo save(Todo todo) {
        return todoJpaRepository.save(todo);
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return todoJpaRepository.findById(id);
    }

    @Override
    public List<Todo> findAll(int limit, String sortField, String sortDir) {
        return todoJpaRepository.findAll(pageable(limit, sortField, sortDir)).getContent();
    }

    @Override
    public List<Todo> findByStatus(TodoStatus status, int limit, String sortField, String sortDir) {
        return todoJpaRepository.findByStatus(status, pageable(limit, sortField, sortDir)).getContent();
    }

    @Override
    public Optional<Todo> findByLinkedJobId(Long jobId) {
        return todoJpaRepository.findByLinkedJobId(jobId);
    }

    private static Pageable pageable(int limit, String sortField, String sortDir) {
        int n = Math.max(1, limit);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = SORTABLE.contains(sortField) ? sortField : "createdAt";
        return PageRequest.of(0, n, direction, property);
    }
}
