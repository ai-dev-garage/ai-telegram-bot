package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.Requester;
import java.util.List;

public interface JobManagement {

    /**
     * @param preferredShellCwd optional cwd for {@link com.ai.dev.garage.bot.domain.TaskType#SHELL_COMMAND} jobs (e.g. from Telegram navigation); must pass allowlist at execution time
     */
    Job createJob(String intent, Requester requester, String preferredShellCwd);

    Job getJob(String idOrLast);

    List<Job> listJobs(int limit);

    Job approve(String idOrLast, String approvedBy);

    Job reject(String idOrLast);

    Job cancel(String idOrLast);
}
