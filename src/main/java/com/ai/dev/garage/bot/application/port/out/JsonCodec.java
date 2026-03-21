package com.ai.dev.garage.bot.application.port.out;

import java.util.Map;

/**
 * Outbound port: JSON encode/decode (technical concern; keeps domain/services free of Jackson types at call sites).
 */
public interface JsonCodec {

    String toJson(Object value);

    Map<String, Object> fromJson(String json);
}
