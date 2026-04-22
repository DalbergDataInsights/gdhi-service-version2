package it.gdhi.ai.dto;

import java.time.Instant;
import java.util.Map;

public record BedrockToolResponse<T>(
        String tool,
        String status,
        String message,
        Map<String, Object> filters,
        T data,
        Instant timestamp
) {
    public static <T> BedrockToolResponse<T> ok(String tool, String message, Map<String, Object> filters, T data) {
        return new BedrockToolResponse<>(tool, "ok", message, filters, data, Instant.now());
        // TODO: consider using a builder pattern if the number of parameters grows or if we want to make some of them optional
    }
}
