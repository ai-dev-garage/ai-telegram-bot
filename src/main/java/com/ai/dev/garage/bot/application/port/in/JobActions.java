package com.ai.dev.garage.bot.application.port.in;

import com.ai.dev.garage.bot.domain.Job;

public interface JobActions {

    Job cancel(long id);

    Job retry(long id);
}
