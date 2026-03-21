package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanSessionStoreJpaAdapter implements PlanSessionStore {

    private final PlanSessionJpaRepository sessionRepo;
    private final PlanQuestionJpaRepository questionRepo;

    @Override
    public PlanSession save(PlanSession session) {
        return sessionRepo.save(session);
    }

    @Override
    public Optional<PlanSession> findByJobId(long jobId) {
        return sessionRepo.findByJobId(jobId);
    }

    @Override
    public Optional<PlanSession> findById(long id) {
        return sessionRepo.findById(id);
    }

    @Override
    public List<PlanSession> findByStateIn(List<PlanState> states) {
        return sessionRepo.findByStateIn(states);
    }

    @Override
    public PlanQuestion saveQuestion(PlanQuestion question) {
        return questionRepo.save(question);
    }

    @Override
    public List<PlanQuestion> findQuestionsBySessionAndRound(long sessionId, int round) {
        return questionRepo.findByPlanSessionIdAndRoundOrderBySeqAsc(sessionId, round);
    }

    @Override
    public Optional<PlanQuestion> findQuestion(long sessionId, int round, int seq) {
        return questionRepo.findByPlanSessionIdAndRoundAndSeq(sessionId, round, seq);
    }

    @Override
    public Optional<PlanQuestion> findFirstUnansweredQuestion(long sessionId) {
        return questionRepo.findFirstByPlanSessionIdAndAnswerIsNullOrderByRoundAscSeqAsc(sessionId);
    }

    @Override
    public List<PlanQuestion> findAllQuestionsBySession(long sessionId) {
        return questionRepo.findByPlanSessionIdOrderByRoundAscSeqAsc(sessionId);
    }
}
