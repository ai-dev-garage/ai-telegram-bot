package com.ai.dev.garage.bot.application.service.support;

import com.ai.dev.garage.bot.application.port.in.support.JobLifecycle;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.RiskLevel;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLifecycleService implements JobLifecycle {

    private final JobStore jobRepository;
    private final AgentTaskRuntime agentTaskRuntime;

    @Override
    @Transactional
    public FinalizeOutcome finalizeNewJob(Job job, ClassificationResult classified) {
        if (classified.taskType() == TaskType.PLAN_TASK) {
            return new FinalizeOutcome(null);
        }
        if (classified.taskType() == TaskType.AGENT_TASK) {
            job.setStatus(job.getStatus());
            job.setApprovalState(ApprovalState.APPROVED);
            jobRepository.save(job);
            boolean started = agentTaskRuntime.startForJob(job);
            return new FinalizeOutcome(started);
        }
        if (classified.riskLevel() == RiskLevel.LOW) {
            job.setApprovalState(ApprovalState.APPROVED);
        } else {
            job.setApprovalState(ApprovalState.PENDING);
        }
        jobRepository.save(job);
        log.debug("Job {} finalized status={} approval={}", job.getId(), job.getStatus(), job.getApprovalState());
        return new FinalizeOutcome(null);
    }
}
