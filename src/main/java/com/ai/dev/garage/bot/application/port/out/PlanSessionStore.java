package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import java.util.List;
import java.util.Optional;

public interface PlanSessionStore {

    PlanSession save(PlanSession session);

    Optional<PlanSession> findByJobId(long jobId);

    Optional<PlanSession> findById(long id);

    List<PlanSession> findByStateIn(List<PlanState> states);

    PlanQuestion saveQuestion(PlanQuestion question);

    List<PlanQuestion> findQuestionsBySessionAndRound(long sessionId, int round);

    Optional<PlanQuestion> findQuestion(long sessionId, int round, int seq);

    Optional<PlanQuestion> findFirstUnansweredQuestion(long sessionId);

    List<PlanQuestion> findAllQuestionsBySession(long sessionId);
}
