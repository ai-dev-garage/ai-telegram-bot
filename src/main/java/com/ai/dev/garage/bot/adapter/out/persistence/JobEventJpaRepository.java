package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.JobEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobEventJpaRepository extends JpaRepository<JobEvent, Long> {

    List<JobEvent> findByJobIdOrderByCreatedAtAsc(Long jobId);
}
