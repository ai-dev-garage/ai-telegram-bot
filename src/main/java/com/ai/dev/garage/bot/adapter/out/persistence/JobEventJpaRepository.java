package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.JobEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEventJpaRepository extends JpaRepository<JobEvent, Long> {

    List<JobEvent> findByJobIdOrderByCreatedAtAsc(Long jobId);
}
