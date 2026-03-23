package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.JobActions;
import com.ai.dev.garage.bot.application.port.out.JobStore;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobActionsService implements JobActions {

    private final JobStore jobStore;

    @Override
    @Transactional
    public Job cancel(long id) {
        Job job = jobStore.findById(id).orElseThrow(() -> new EntityNotFoundException("job not found"));
        if (List.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELLED).contains(job.getStatus())) {
            throw new IllegalStateException("job already terminal");
        }
        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        return jobStore.save(job);
    }

    @Override
    @Transactional
    public Job retry(long id) {
        Job job = jobStore.findById(id).orElseThrow(() -> new EntityNotFoundException("job not found"));
        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException("can only retry failed jobs");
        }
        job.setStatus(JobStatus.QUEUED);
        job.setFinishedAt(null);
        job.setLastError(null);
        job.setResultJson(null);
        job.setAttempt(job.getAttempt() + 1);
        return jobStore.save(job);
    }
}
