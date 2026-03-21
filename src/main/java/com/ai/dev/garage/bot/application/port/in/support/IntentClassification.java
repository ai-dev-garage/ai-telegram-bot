package com.ai.dev.garage.bot.application.port.in.support;

import com.ai.dev.garage.bot.domain.ClassificationResult;

/**
 * Internal inbound port: classify raw intent into task type, payload, risk, approval (supporting {@code JobService}).
 */
public interface IntentClassification {

    ClassificationResult classify(String intent);
}
