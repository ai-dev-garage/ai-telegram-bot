package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.in.JobLogQueries;
import com.ai.dev.garage.bot.application.port.out.JobLogStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogQueryService implements JobLogQueries {

    private final JobLogStore jobLogStore;

    @Override
    @Transactional(readOnly = true)
    public List<String> getTail(Long jobId, int tail) {
        List<String> lines = jobLogStore.findLinesTail(jobId, tail);
        log.debug("Log tail jobId={} lines={}", jobId, lines.size());
        return lines;
    }
}
