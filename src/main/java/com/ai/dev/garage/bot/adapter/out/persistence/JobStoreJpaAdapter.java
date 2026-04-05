package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.TaskType;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobStoreJpaAdapter implements JobStore {

    private final JobJpaRepository jobJpaRepository;

    @Override
    public Job save(Job job) {
        return jobJpaRepository.save(job);
    }

    @Override
    public Optional<Job> findById(Long id) {
        return jobJpaRepository.findById(id);
    }

    @Override
    public List<Job> findRecentOrderedByIdDesc(int limit) {
        return jobJpaRepository.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<Job> findRecentTopLevelOrderedByIdDesc(int limit) {
        return jobJpaRepository.findAllByParentJobIdIsNullOrderByIdDesc(PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<Job> findChildrenByParentId(Long parentJobId) {
        return jobJpaRepository.findAllByParentJobIdOrderByStepIndexAsc(parentJobId);
    }

    @Override
    public Optional<Job> findNextRunnableJob() {
        return jobJpaRepository.findNextRunnable(
            JobStatus.QUEUED, ApprovalState.APPROVED,
            List.of(TaskType.AGENT_TASK, TaskType.PLAN_TASK));
    }
}
