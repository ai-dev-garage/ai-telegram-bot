package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.adapter.out.filesystem.PlanFileExporter;
import com.ai.dev.garage.bot.application.port.in.PlanManagement;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanSessionResult;
import com.ai.dev.garage.bot.application.port.out.PlanSessionStore;
import com.ai.dev.garage.bot.application.service.support.PlanResultPersistenceService;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.application.support.ContentLengthLimits;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.PlanQuestion;
import com.ai.dev.garage.bot.domain.PlanSession;
import com.ai.dev.garage.bot.domain.PlanState;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.persistence.EntityNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSessionService implements PlanManagement {

    private static final String TRUNCATE_ELLIPSIS = "...";

    private final JobStore jobStore;
    private final PlanSessionStore planSessionStore;
    private final PlanCliRuntime planCliRuntime;
    private final JsonCodec jsonCodec;
    private final AllowedPathValidator allowedPathValidator;
    private final TodoCompletionHook todoCompletionHook;
    private final PlanFileExporter planFileExporter;
    private final PlanResultPersistenceService planResultPersistenceService;
    private final ExecutorService planExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final AtomicReference<PlanCompletionListener> completionListener = new AtomicReference<>();

    /**
     * Register a listener for async plan completion notifications (set by Telegram layer).
     */
    public void setCompletionListener(PlanCompletionListener listener) {
        completionListener.set(listener);
    }

    @Override
    @Transactional
    public Job createPlan(String intent, Requester requester, String workspace) {
        if (workspace != null && !workspace.isBlank()) {
            String reason = allowedPathValidator.validationFailureReason(workspace.trim());
            if (reason != null) {
                throw new IllegalArgumentException(reason);
            }
        }

        Map<String, Object> payload = new HashMap<>();
        if (workspace != null && !workspace.isBlank()) {
            payload.put("workspace", workspace.trim());
        }
        payload.put("input", intent);

        Job job = Job.builder()
            .intent(intent)
            .requester(Requester.builder()
                .telegramUserId(requester.getTelegramUserId())
                .telegramChatId(requester.getTelegramChatId())
                .telegramUsername(requester.getTelegramUsername())
                .build())
            .taskType(TaskType.PLAN_TASK)
            .riskLevel(RiskLevel.LOW)
            .approvalState(ApprovalState.APPROVED)
            .status(JobStatus.RUNNING)
            .taskPayloadJson(jsonCodec.toJson(payload))
            .startedAt(OffsetDateTime.now(ZoneId.systemDefault()))
            .build();
        job = jobStore.save(job);

        var session = PlanSession.builder()
            .jobId(job.getId())
            .state(PlanState.PLANNING)
            .build();
        planSessionStore.save(session);

        long jobId = job.getId();
        String prompt = intent;
        submitAfterCommit(() -> executePlanRound(jobId, prompt, null));

        log.info("Plan job {} created, CLI starting async", jobId);
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public PlanSession getSession(long jobId) {
        return loadPlanSessionForJob(jobId);
    }

    private PlanSession loadPlanSessionForJob(long jobId) {
        return planSessionStore.findByJobId(jobId)
            .orElseThrow(() -> new EntityNotFoundException("No plan session for job " + jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanQuestion> nextUnansweredQuestion(long sessionId) {
        return planSessionStore.findFirstUnansweredQuestion(sessionId);
    }

    @Override
    @Transactional
    public void recordAnswer(long sessionId, int round, int seq, String answer) {
        PlanQuestion question = planSessionStore.findQuestion(sessionId, round, seq)
            .orElseThrow(() -> new EntityNotFoundException(
                "Question not found: session=" + sessionId + " round=" + round + " seq=" + seq));
        question.setAnswer(answer);
        question.setAnsweredAt(OffsetDateTime.now(ZoneId.systemDefault()));
        planSessionStore.saveQuestion(question);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allQuestionsAnswered(long sessionId) {
        return planSessionStore.findFirstUnansweredQuestion(sessionId).isEmpty();
    }

    @Override
    @Transactional
    public void resumePlan(long jobId) {
        PlanSession session = loadPlanSessionForJob(jobId);
        if (session.getState() != PlanState.AWAITING_INPUT) {
            throw new IllegalStateException("Plan session not awaiting input (current: " + session.getState() + ")");
        }

        Job job = jobStore.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        job.setStatus(JobStatus.RUNNING);
        jobStore.save(job);

        session.setState(PlanState.PLANNING);
        session.setRound(session.getRound() + 1);
        planSessionStore.save(session);

        String compiledAnswers = compileAnswers(session.getId(), session.getRound() - 1);
        String cliSessionId = session.getCliSessionId();

        submitAfterCommit(() -> executePlanRound(jobId, compiledAnswers, cliSessionId));
    }

    @Override
    @Transactional
    public Job approvePlan(long jobId) {
        PlanSession session = loadPlanSessionForJob(jobId);
        if (session.getState() != PlanState.PLAN_READY && session.getState() != PlanState.PAUSED) {
            throw new IllegalStateException("Plan not ready for approval (current: " + session.getState() + ")");
        }
        session.setState(PlanState.APPROVED);
        planSessionStore.save(session);

        Job job = jobStore.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

        List<PlanQuestion> allQuestions = planSessionStore.findAllQuestionsBySession(session.getId());
        String exportedPath = planFileExporter.exportPlan(job, session, allQuestions);

        job.setStatus(JobStatus.SUCCESS);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "Plan approved");
        result.put("plan", truncate(session.getPlanText(), ContentLengthLimits.JOB_TEXT_SNIPPET_MAX));
        if (exportedPath != null) {
            result.put("exportedTo", exportedPath);
        }
        job.setResultJson(jsonCodec.toJson(result));
        Job saved = jobStore.save(job);
        todoCompletionHook.onJobCompleted(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Job pausePlan(long jobId) {
        PlanSession session = loadPlanSessionForJob(jobId);
        if (session.getState() != PlanState.PLAN_READY) {
            throw new IllegalStateException("Plan not ready to pause (current: " + session.getState() + ")");
        }
        session.setState(PlanState.PAUSED);
        planSessionStore.save(session);

        Job job = jobStore.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

        List<PlanQuestion> allQuestions = planSessionStore.findAllQuestionsBySession(session.getId());
        String exportedPath = planFileExporter.exportPlan(job, session, allQuestions);

        job.setStatus(JobStatus.PAUSED);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "Plan paused");
        if (exportedPath != null) {
            result.put("exportedTo", exportedPath);
        }
        job.setResultJson(jsonCodec.toJson(result));
        return jobStore.save(job);
    }

    @Override
    @Transactional
    public Job rejectPlan(long jobId) {
        PlanSession session = loadPlanSessionForJob(jobId);
        session.setState(PlanState.REJECTED);
        planSessionStore.save(session);

        Job job = jobStore.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        Job saved = jobStore.save(job);
        todoCompletionHook.onJobCancelled(saved.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanSession> listActivePlans() {
        return planSessionStore.findByStateIn(
            List.of(PlanState.PLANNING, PlanState.AWAITING_INPUT, PlanState.PLAN_READY, PlanState.PAUSED));
    }

    private void executePlanRound(long jobId, String prompt, String cliSessionId) {
        try {
            Job job = jobStore.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));

            PlanSessionResult result;
            if (cliSessionId == null) {
                result = planCliRuntime.startPlan(job, prompt);
            } else {
                result = planCliRuntime.resumePlan(job, cliSessionId, prompt);
            }

            planResultPersistenceService.persistCliResult(jobId, result, completionListener.get());
        } catch (Exception e) {
            log.error("Plan CLI execution failed for job {}: {}", jobId, e.getMessage(), e);
            try {
                Job job = jobStore.findById(jobId).orElse(null);
                if (job != null) {
                    job.setStatus(JobStatus.FAILED);
                    job.setLastError(e.getMessage());
                    job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
                    jobStore.save(job);
                }
            } catch (Exception inner) {
                log.error("Failed to mark job {} as failed: {}", jobId, inner.getMessage());
            }
            notifyError(jobId, e.getMessage());
        }
    }

    private void notifyError(long jobId, String error) {
        PlanCompletionListener listener = completionListener.get();
        if (listener != null) {
            try {
                listener.onPlanError(jobId, error);
            } catch (Exception e) {
                log.warn("Plan completion listener error (error) for job {}: {}", jobId, e.getMessage());
            }
        }
    }

    private String compileAnswers(long sessionId, int round) {
        List<PlanQuestion> questions = planSessionStore.findQuestionsBySessionAndRound(sessionId, round);
        var sb = new StringBuilder();
        for (PlanQuestion q : questions) {
            sb.append("Q").append(q.getSeq()).append(": ").append(q.getQuestionText()).append("\n");
            sb.append("A: ").append(q.getAnswer() != null ? q.getAnswer() : "(no answer)").append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Schedules a task to run on {@link #planExecutor} after the current
     * transaction commits. Prevents the async thread from querying rows
     * that haven't been committed yet.
     */
    private void submitAfterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                planExecutor.execute(task);
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - TRUNCATE_ELLIPSIS.length()) + TRUNCATE_ELLIPSIS;
    }

    /**
     * Callback interface for async plan completion notifications to the Telegram layer.
     */
    public interface PlanCompletionListener {
        void onQuestionsReady(long jobId, long sessionId);

        void onPlanReady(long jobId);

        void onPlanError(long jobId, String error);
    }
}
