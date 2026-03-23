package com.ai.dev.garage.bot.application.port.in.support;

import com.ai.dev.garage.bot.domain.ClassificationResult;
import com.ai.dev.garage.bot.domain.Job;

/**
 * Internal inbound port: post-create transitions (agent path vs queue vs awaiting approval).
 */
@FunctionalInterface
public interface JobLifecycle {

    /**
     * Outcome of finalizing a newly persisted job (e.g. whether Cursor CLI was started).
     */
    record FinalizeOutcome(Boolean agentCliInvoked) {
    }

    FinalizeOutcome finalizeNewJob(Job job, ClassificationResult classified);
}
