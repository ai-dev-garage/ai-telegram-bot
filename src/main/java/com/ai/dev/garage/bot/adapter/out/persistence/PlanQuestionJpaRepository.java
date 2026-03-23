package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.domain.PlanQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanQuestionJpaRepository extends JpaRepository<PlanQuestion, Long> {

    List<PlanQuestion> findByPlanSessionIdAndRoundOrderBySeqAsc(long planSessionId, int round);

    Optional<PlanQuestion> findByPlanSessionIdAndRoundAndSeq(long planSessionId, int round, int seq);

    Optional<PlanQuestion> findFirstByPlanSessionIdAndAnswerIsNullOrderByRoundAscSeqAsc(long planSessionId);

    List<PlanQuestion> findByPlanSessionIdOrderByRoundAscSeqAsc(long planSessionId);
}
