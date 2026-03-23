package com.ai.dev.garage.bot.adapter.out.persistence;

import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.JobLogStore;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Outbound adapter: implements {@link JobLogAppender} by delegating to {@link JobLogStore} (write path).
 */
@Component
@RequiredArgsConstructor
public class JobLogAppenderAdapter implements JobLogAppender {

    private final JobLogStore jobLogStore;

    @Override
    @Transactional
    public void append(Long jobId, String line) {
        jobLogStore.appendLine(jobId, line);
    }
}
