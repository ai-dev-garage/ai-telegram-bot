package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanSessionJpaRepository extends JpaRepository<PlanSession, Long> {

    Optional<PlanSession> findByJobId(long jobId);

    List<PlanSession> findByStateIn(List<PlanState> states);
}
