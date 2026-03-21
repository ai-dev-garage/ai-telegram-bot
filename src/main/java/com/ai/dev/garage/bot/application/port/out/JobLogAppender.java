package com.ai.dev.garage.bot.application.port.out;

/**
 * Secondary port: append structured log lines for a job (DIP — executors depend on this, not JPA).
 */
@FunctionalInterface
public interface JobLogAppender {

    void append(Long jobId, String line);
}
