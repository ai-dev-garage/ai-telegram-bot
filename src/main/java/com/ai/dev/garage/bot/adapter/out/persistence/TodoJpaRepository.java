package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.Todo;
import com.ai.dev.garage.bot.domain.TodoStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoJpaRepository extends JpaRepository<Todo, Long> {

    Page<Todo> findByStatus(TodoStatus status, Pageable pageable);

    Optional<Todo> findByLinkedJobId(Long linkedJobId);
}
