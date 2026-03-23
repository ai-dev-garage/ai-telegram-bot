package com.ai.dev.garage.bot.adapter.in.telegram.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobTelegramMessagesTest {

    @Test
    void shouldExpandMessageWhenJobNotAwaitingApproval() {
        String out = JobTelegramMessages.formatApproveRejectError(new IllegalStateException("job not awaiting approval"));
        assertThat(out)
            .contains("job not awaiting approval")
            .contains("Agent tasks are auto-approved");
    }

    @Test
    void shouldReturnMessageWhenOtherException() {
        assertThat(JobTelegramMessages.formatApproveRejectError(new IllegalArgumentException("bad")))
            .isEqualTo("bad");
    }
}
