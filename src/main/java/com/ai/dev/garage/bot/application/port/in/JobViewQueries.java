package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;
import com.ai.dev.garage.bot.domain.JobEvent;
import com.ai.dev.garage.bot.domain.JobLog;
import com.ai.dev.garage.bot.domain.JobStatus;

import java.util.List;

public interface JobViewQueries {

    List<Job> listJobs(JobStatus status, int limit, String sortField, String sortDir);

    Job getJob(long id);

    List<JobEvent> getEvents(long jobId);

    List<JobLog> getLogTail(long jobId, int limit);

    List<JobLog> getLogsAfter(long jobId, int afterSeq);
}
