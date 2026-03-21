package com.ai.dev.garage.bot.application.port.out;

import java.util.List;

public interface JobLogStore {

    void appendLine(Long jobId, String line);

    List<String> findLinesTail(Long jobId, int tail);
}
