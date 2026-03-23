package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TodoJpaRepository extends JpaRepository<Todo, Long> {

    Page<Todo> findByStatus(TodoStatus status, Pageable pageable);

    Optional<Todo> findByLinkedJobId(Long linkedJobId);
}
