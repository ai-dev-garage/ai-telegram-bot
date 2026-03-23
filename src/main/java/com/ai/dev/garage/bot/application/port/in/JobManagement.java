package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;

import java.util.List;

public interface JobManagement {

    /**
     * Creates a job from natural-language intent and requester context.
     *
     * @param preferredShellCwd optional cwd for {@link com.ai.dev.garage.bot.domain.TaskType#SHELL_COMMAND} jobs
     *                          (e.g. from Telegram navigation); must pass allowlist at execution time
     */
    default Job createJob(String intent, Requester requester, String preferredShellCwd) {
        return createJob(intent, requester, preferredShellCwd, null);
    }

    /**
     * Creates a job with an optional Cursor CLI model override for agent tasks.
     *
     * @param cliModelOverride when non-blank, merged into AGENT_TASK payload as {@code cliModel} for Cursor
     */
    Job createJob(String intent, Requester requester, String preferredShellCwd, String cliModelOverride);

    Job getJob(String idOrLast);

    List<Job> listJobs(int limit);

    Job approve(String idOrLast, String approvedBy);

    Job reject(String idOrLast);

    Job cancel(String idOrLast);
}
