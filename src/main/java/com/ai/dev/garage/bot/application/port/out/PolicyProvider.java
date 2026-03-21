package com.ai.dev.garage.bot.application.port.out;

import java.util.Map;

/**
 * Port for loading approval/classification policy (YAML). Keeps {@link com.ai.dev.garage.bot.application.service.support.ClassificationService}
 * independent of filesystem / classpath details (DIP).
 */
public interface PolicyProvider {

    Map<String, Object> loadPolicy();
}
