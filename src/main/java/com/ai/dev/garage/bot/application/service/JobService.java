package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.JobManagement;
import com.ai.dev.garage.bot.application.port.in.support.IntentClassification;
import com.ai.dev.garage.bot.application.port.in.support.JobLifecycle;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.service.model.JobResultPayload;
import com.ai.dev.garage.bot.application.support.AllowedPathValidator;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService implements JobManagement {
    private final JobStore jobRepository;
    private final IntentClassification intentClassification;
    private final JsonCodec jsonCodec;
    private final JobLifecycle jobLifecycle;
    private final AllowedPathValidator allowedPathValidator;
    private final TodoCompletionHook todoCompletionHook;

    @Override
    @Transactional
    public Job createJob(String intent, Requester requester, String preferredShellCwd) {
        ClassificationResult classified = intentClassification.classify(intent);
        Map<String, Object> payload = new HashMap<>(classified.taskPayload());
        if (classified.taskType() == TaskType.SHELL_COMMAND
            && preferredShellCwd != null
            && !preferredShellCwd.isBlank()) {
            payload.put("cwd", preferredShellCwd.trim());
        }
        if (classified.taskType() == TaskType.AGENT_TASK
            && preferredShellCwd != null
            && !preferredShellCwd.isBlank()) {
            String ws = preferredShellCwd.trim();
            String reason = allowedPathValidator.validationFailureReason(ws);
            if (reason != null) {
                throw new IllegalArgumentException(reason);
            }
            payload.put("workspace", ws);
        }
        String storedIntent = storedIntent(intent, classified);
        var entity = Job.builder()
            .intent(storedIntent)
            .requester(copyRequester(requester))
            .taskType(classified.taskType())
            .riskLevel(classified.riskLevel())
            .approvalState(classified.approvalState())
            .status(JobStatus.QUEUED)
            .taskPayloadJson(jsonCodec.toJson(payload))
            .build();
        Job saved = jobRepository.save(entity);
        JobLifecycle.FinalizeOutcome outcome = jobLifecycle.finalizeNewJob(saved, classified);
        if (outcome.agentCliInvoked() != null) {
            saved.setAgentCliInvoked(outcome.agentCliInvoked());
        }
        log.debug("Job created id={} taskType={} status={}", saved.getId(), saved.getTaskType(), saved.getStatus());
        return saved;
    }

    private static String storedIntent(String rawIntent, ClassificationResult classified) {
        String trimmed = rawIntent == null ? "" : rawIntent.trim();
        if (classified.taskType() != TaskType.AGENT_TASK) {
            return trimmed;
        }
        Object input = classified.taskPayload().get("input");
        if (input instanceof String s && !s.isBlank()) {
            return s;
        }
        return trimmed;
    }

    private static Requester copyRequester(Requester source) {
        return Requester.builder()
            .telegramUserId(source.getTelegramUserId())
            .telegramChatId(source.getTelegramChatId())
            .telegramUsername(source.getTelegramUsername())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Job getJob(String idOrLast) {
        return resolveJobEntity(idOrLast);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Job> listJobs(int limit) {
        return jobRepository.findRecentOrderedByIdDesc(Math.max(1, limit));
    }

    @Override
    @Transactional
    public Job approve(String idOrLast, String approvedBy) {
        Job job = resolveJobEntity(idOrLast);
        if (job.getApprovalState() != ApprovalState.PENDING) {
            throw new IllegalStateException("job not awaiting approval");
        }
        job.setApprovalState(ApprovalState.APPROVED);
        job.setApprovedBy(approvedBy);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job reject(String idOrLast) {
        Job job = resolveJobEntity(idOrLast);
        if (job.getApprovalState() != ApprovalState.PENDING) {
            throw new IllegalStateException("job not awaiting approval");
        }
        job.setApprovalState(ApprovalState.REJECTED);
        job.setStatus(JobStatus.CANCELLED);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job cancel(String idOrLast) {
        Job job = resolveJobEntity(idOrLast);
        if (List.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELLED).contains(job.getStatus())) {
            throw new IllegalStateException("job already terminal");
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        Job saved = jobRepository.save(job);
        todoCompletionHook.onJobCancelled(saved.getId());
        return saved;
    }

    @Transactional
    public Optional<Job> pollQueuedJob() {
        return jobRepository.findNextRunnableJob().map(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(OffsetDateTime.now(ZoneId.systemDefault()));
            return jobRepository.save(job);
        });
    }

    @Transactional
    public void markCompleted(Job job, String summary, int exitCode) {
        job.setStatus(JobStatus.SUCCESS);
        job.setResultJson(jsonCodec.toJson(JobResultPayload.result(summary, exitCode, null)));
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        jobRepository.save(job);
        todoCompletionHook.onJobCompleted(job.getId());
    }

    @Transactional
    public void markFailed(Job job, String summary, int exitCode, String error) {
        job.setStatus(JobStatus.FAILED);
        job.setResultJson(jsonCodec.toJson(JobResultPayload.result(summary, exitCode, error)));
        job.setLastError(error);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        jobRepository.save(job);
        todoCompletionHook.onJobFailed(job.getId());
    }

    @Transactional(readOnly = true)
    public Job resolveJob(String idOrLast) {
        return resolveJobEntity(idOrLast);
    }

    private Job resolveJobEntity(String idOrLast) {
        if ("last".equalsIgnoreCase(idOrLast)) {
            return jobRepository.findRecentOrderedByIdDesc(1).stream().findFirst()
                .orElseThrow(() -> new EntityNotFoundException("job not found"));
        }
        Long id = Long.valueOf(idOrLast);
        return jobRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("job not found"));
    }
}
