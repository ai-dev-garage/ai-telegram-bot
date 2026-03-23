package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.Requester;

import java.util.List;
import java.util.Optional;

public interface PlanManagement {

    /**
     * Creates a PLAN_TASK job and starts the first CLI round asynchronously.
     * Returns the job immediately; Telegram notifications arrive when the CLI finishes.
     */
    default Job createPlan(String intent, Requester requester, String workspace) {
        return createPlan(intent, requester, workspace, null);
    }

    /**
     * Creates a plan job with an optional Cursor CLI model override.
     *
     * @param cliModelOverride when non-blank, stored as payload {@code cliModel} for Cursor {@code --model}
     */
    Job createPlan(String intent, Requester requester, String workspace, String cliModelOverride);

    PlanSession getSession(long jobId);

    Optional<PlanQuestion> nextUnansweredQuestion(long sessionId);

    void recordAnswer(long sessionId, int round, int seq, String answer);

    boolean allQuestionsAnswered(long sessionId);

    /**
     * Compile answers and resume the CLI session. Runs asynchronously;
     * Telegram notifications arrive when the CLI finishes.
     */
    void resumePlan(long jobId);

    Job approvePlan(long jobId);

    Job pausePlan(long jobId);

    Job rejectPlan(long jobId);

    List<PlanSession> listActivePlans();
}
