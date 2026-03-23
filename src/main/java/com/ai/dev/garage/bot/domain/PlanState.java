package com.ai.dev.garage.bot.domain;

public enum PlanState {
    PLANNING,
    AWAITING_INPUT,
    PLAN_READY,
    PAUSED,
    APPROVED,
    REJECTED,
    CANCELLED,
    /** Plan CLI exited with an error or produced no usable output. */
    FAILED
}
