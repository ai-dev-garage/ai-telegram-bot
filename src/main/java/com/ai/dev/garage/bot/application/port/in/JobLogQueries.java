package com.ai.dev.garage.bot.application.port.in;

import java.util.List;

/**
 * Inbound port: read job log lines (REST / Telegram adapters depend on this, not on persistence).
 */
public interface JobLogQueries {

    List<String> getTail(Long jobId, int tail);
}
