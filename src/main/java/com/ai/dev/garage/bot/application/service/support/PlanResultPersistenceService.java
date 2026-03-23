package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedMessage;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult.ParsedQuestion;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.PlanSessionService.PlanCompletionListener;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists CLI plan round outcomes in a proper Spring transaction (invoked from async executor via
 * {@link com.ai.dev.garage.bot.application.service.PlanSessionService}, not via {@code this}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanResultPersistenceService {

    private final PlanSessionStore planSessionStore;
    private final JobStore jobStore;
    private final JsonCodec jsonCodec;

    @Transactional
    public void persistCliResult(long jobId, PlanSessionResult result, PlanCompletionListener listener) {
        PlanSession session = planSessionStore.findByJobId(jobId)
            .orElseThrow(() -> new EntityNotFoundException("No plan session for job " + jobId));

        if (result.sessionId() != null) {
            session.setCliSessionId(result.sessionId());
        }

        List<ParsedQuestion> allQuestions = collectQuestions(result);

        if (!allQuestions.isEmpty()) {
            persistQuestionsRound(session, allQuestions);
            updateJobAwaitingInput(jobId);
            notifyQuestionsReady(listener, jobId, session.getId());
        } else {
            finishPlanReady(session, result, jobId);
            notifyPlanReady(listener, jobId);
        }
    }

    private static List<ParsedQuestion> collectQuestions(PlanSessionResult result) {
        List<ParsedQuestion> allQuestions = new ArrayList<>();
        for (ParsedMessage msg : result.messages()) {
            allQuestions.addAll(msg.questions());
        }
        return allQuestions;
    }

    private void persistQuestionsRound(PlanSession session, List<ParsedQuestion> allQuestions) {
        int seq = 1;
        for (ParsedQuestion pq : allQuestions) {
            var question = PlanQuestion.builder()
                .planSession(session)
                .round(session.getRound())
                .seq(seq++)
                .questionText(pq.questionText())
                .options(pq.options().isEmpty() ? null : jsonCodec.toJson(pq.options()))
                .build();
            planSessionStore.saveQuestion(question);
        }
        session.setState(PlanState.AWAITING_INPUT);
        planSessionStore.save(session);
    }

    private void updateJobAwaitingInput(long jobId) {
        Job job = jobStore.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.AWAITING_INPUT);
            jobStore.save(job);
        }
    }

    private void finishPlanReady(PlanSession session, PlanSessionResult result, long jobId) {
        session.setPlanText(result.fullText());
        session.setState(PlanState.PLAN_READY);
        planSessionStore.save(session);

        Job job = jobStore.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.AWAITING_INPUT);
            jobStore.save(job);
        }
    }

    private static void notifyQuestionsReady(PlanCompletionListener listener, long jobId, long sessionId) {
        if (listener == null) {
            return;
        }
        try {
            listener.onQuestionsReady(jobId, sessionId);
        } catch (Exception e) {
            log.warn("Plan completion listener error (questions) for job {}: {}", jobId, e.getMessage());
        }
    }

    private static void notifyPlanReady(PlanCompletionListener listener, long jobId) {
        if (listener == null) {
            return;
        }
        try {
            listener.onPlanReady(jobId);
        } catch (Exception e) {
            log.warn("Plan completion listener error (plan) for job {}: {}", jobId, e.getMessage());
        }
    }
}
