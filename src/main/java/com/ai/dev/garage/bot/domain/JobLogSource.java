package com.ai.dev.garage.bot.domain;

/**
 * Distinguishes between log lines emitted by the backend application
 * and those produced by the external AI agent (Cursor, Claude, etc.).
 */
public enum JobLogSource {
    BACKEND,
    AGENT
}
