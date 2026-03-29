package com.ai.dev.garage.bot.application.port.out;

import com.ai.dev.garage.bot.domain.Job;

import java.util.List;
import java.util.Optional;

public interface JobStore {

    Job save(Job job);

    Optional<Job> findById(Long id);

    /**
     * Most recent jobs first, at most {@code limit} rows.
     */
    List<Job> findRecentOrderedByIdDesc(int limit);

    /**
     * Most recent top-level jobs (no parent) first, at most {@code limit} rows.
     * Excludes child jobs created by workflow orchestration.
     */
    List<Job> findRecentTopLevelOrderedByIdDesc(int limit);

    /**
     * All child jobs for a given parent workflow job, ordered by step index.
     */
    List<Job> findChildrenByParentId(Long parentJobId);

    /**
     * Oldest QUEUED job that is APPROVED and not an AGENT_TASK (agent tasks use the file-based flow).
     */
    Optional<Job> findNextRunnableJob();
}
