package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.adapter.out.cursor.CursorCliModelResolver;
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
import com.ai.dev.garage.bot.domain.JobTerminalEvent;
import com.ai.dev.garage.bot.domain.Requester;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;
import com.ai.dev.garage.bot.domain.WorkflowStep;

import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Job createJob(String intent, Requester requester, String preferredShellCwd, String cliModelOverride) {
        ClassificationResult classified = intentClassification.classify(intent);
        Map<String, Object> payload = new HashMap<>(classified.taskPayload());
        mergeShellCwdIntoPayload(classified.taskType(), preferredShellCwd, payload);
        mergeAgentWorkspaceIntoPayload(classified.taskType(), preferredShellCwd, payload);
        mergeCliModelIntoPayload(classified.taskType(), cliModelOverride, payload);
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

    private static void mergeShellCwdIntoPayload(TaskType taskType, String preferredShellCwd, Map<String, Object> payload) {
        if (taskType == TaskType.SHELL_COMMAND
            && preferredShellCwd != null
            && !preferredShellCwd.isBlank()) {
            payload.put("cwd", preferredShellCwd.trim());
        }
    }

    private void mergeAgentWorkspaceIntoPayload(TaskType taskType, String preferredShellCwd, Map<String, Object> payload) {
        if (taskType != TaskType.AGENT_TASK
            || preferredShellCwd == null
            || preferredShellCwd.isBlank()) {
            return;
        }
        String ws = preferredShellCwd.trim();
        String reason = allowedPathValidator.validationFailureReason(ws);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        payload.put("workspace", ws);
    }

    private static void mergeCliModelIntoPayload(TaskType taskType, String cliModelOverride, Map<String, Object> payload) {
        if (taskType == TaskType.AGENT_TASK
            && cliModelOverride != null
            && !cliModelOverride.isBlank()) {
            payload.put(CursorCliModelResolver.CLI_MODEL_PAYLOAD_KEY, cliModelOverride.trim());
        }
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
        eventPublisher.publishEvent(new JobTerminalEvent(saved.getId(), JobStatus.CANCELLED));
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
        eventPublisher.publishEvent(new JobTerminalEvent(job.getId(), JobStatus.SUCCESS));
    }

    @Transactional
    public void markFailed(Job job, String summary, int exitCode, String error) {
        job.setStatus(JobStatus.FAILED);
        job.setResultJson(jsonCodec.toJson(JobResultPayload.result(summary, exitCode, error)));
        job.setLastError(error);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        jobRepository.save(job);
        todoCompletionHook.onJobFailed(job.getId());
        eventPublisher.publishEvent(new JobTerminalEvent(job.getId(), JobStatus.FAILED));
    }

    /**
     * Create a child job for a workflow step. The child inherits the parent's requester
     * and workspace context.
     */
    @Transactional
    public Job createChildJob(Job parent, WorkflowStep step, int stepIndex) {
        Map<String, Object> parentPayload = jsonCodec.fromJson(parent.getTaskPayloadJson());
        Map<String, Object> childPayload = new HashMap<>();
        if (step.taskType() == TaskType.SHELL_COMMAND) {
            childPayload.put("command", step.intent());
            Object cwd = parentPayload.get("workspace");
            if (cwd != null) {
                childPayload.put("cwd", cwd);
            }
        } else if (step.taskType() == TaskType.AGENT_TASK) {
            childPayload.put("agent_or_command", "agent");
            childPayload.put("input", step.intent());
            childPayload.put("context", Map.of());
            Object workspace = parentPayload.get("workspace");
            if (workspace != null) {
                childPayload.put("workspace", workspace);
            }
        }

        ApprovalState approval = step.critical() ? ApprovalState.PENDING : ApprovalState.APPROVED;
        var child = Job.builder()
            .intent(step.intent())
            .requester(copyRequester(parent.getRequester()))
            .taskType(step.taskType())
            .riskLevel(step.critical() ? RiskLevel.HIGH : RiskLevel.LOW)
            .approvalState(approval)
            .status(JobStatus.QUEUED)
            .taskPayloadJson(jsonCodec.toJson(childPayload))
            .parentJobId(parent.getId())
            .stepIndex(stepIndex)
            .stepId(step.id())
            .build();

        Job saved = jobRepository.save(child);
        log.debug("Child job created id={} parentId={} step={} taskType={} critical={}",
            saved.getId(), parent.getId(), step.id(), step.taskType(), step.critical());

        // For non-critical AGENT_TASK children, trigger the CLI handoff immediately
        if (step.taskType() == TaskType.AGENT_TASK && !step.critical()) {
            jobLifecycle.finalizeNewJob(saved,
                new ClassificationResult(step.taskType(),
                    childPayload,
                    saved.getRiskLevel(),
                    saved.getApprovalState()));
        }

        return saved;
    }

    @Transactional
    public void markAwaitingInput(Job job) {
        job.setStatus(JobStatus.AWAITING_INPUT);
        jobRepository.save(job);
    }

    @Transactional
    public void requeue(Job job) {
        job.setStatus(JobStatus.QUEUED);
        job.setApprovalState(ApprovalState.APPROVED);
        jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<Job> findChildrenByParentId(Long parentJobId) {
        return jobRepository.findChildrenByParentId(parentJobId);
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
