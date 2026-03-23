package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.in.JobViewQueries;
import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobEvent;
import com.ai.dev.garage.bot.domain.JobLog;
import com.ai.dev.garage.bot.domain.JobStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JobViewQueriesJpaAdapter implements JobViewQueries {

    private final JobJpaRepository jobJpaRepository;
    private final JobEventJpaRepository jobEventJpaRepository;
    private final JobLogJpaRepository jobLogJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Job> listJobs(JobStatus status, int limit, String sortField, String sortDir) {
        int n = Math.max(1, limit);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = "createdAt".equalsIgnoreCase(sortField) ? "createdAt" : "id";
        var pageable = PageRequest.of(0, n, direction, property);
        if (status == null) {
            return jobJpaRepository.findAll(pageable).getContent();
        }
        return jobJpaRepository.findByStatus(status, pageable).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Job getJob(long id) {
        return jobJpaRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("job not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobEvent> getEvents(long jobId) {
        return jobEventJpaRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobLog> getLogTail(long jobId, int limit) {
        int n = Math.max(1, limit);
        List<JobLog> rows = jobLogJpaRepository.findByJobIdOrderBySeqDesc(jobId, PageRequest.of(0, n));
        List<JobLog> copy = new ArrayList<>(rows);
        Collections.reverse(copy);
        return copy;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobLog> getLogsAfter(long jobId, int afterSeq) {
        return jobLogJpaRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, afterSeq);
    }
}
