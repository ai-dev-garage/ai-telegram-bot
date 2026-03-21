package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.ApprovalState;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;
import com.ai.dev.garage.bot.domain.TaskType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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
    public Optional<Job> findNextRunnableJob() {
        return jobJpaRepository.findNextRunnable(
            JobStatus.QUEUED, ApprovalState.APPROVED,
            List.of(TaskType.AGENT_TASK, TaskType.PLAN_TASK));
    }
}
