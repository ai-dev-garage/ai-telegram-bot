package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.JobLogStore;
import com.ai.dev.garage.bot.domain.JobLog;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobLogStoreJpaAdapter implements JobLogStore {

    private final JobLogJpaRepository jobLogRepository;

    @Override
    public void appendLine(Long jobId, String line) {
        JobLog log = JobLog.builder()
            .jobId(jobId)
            .seq(jobLogRepository.maxSeq(jobId) + 1)
            .line(line)
            .build();
        jobLogRepository.save(log);
    }

    @Override
    public List<String> findLinesTail(Long jobId, int tail) {
        List<JobLog> rows = jobLogRepository.findByJobIdOrderBySeqDesc(jobId, PageRequest.of(0, tail));
        Collections.reverse(rows);
        return rows.stream().map(JobLog::getLine).toList();
    }
}
