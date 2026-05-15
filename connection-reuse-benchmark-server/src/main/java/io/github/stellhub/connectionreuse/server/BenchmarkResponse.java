package io.github.stellhub.connectionreuse.server;

/** HTTP 压测响应。 */
public record BenchmarkResponse(
    String requestId, String payload, int payloadBytes, long serverTimeNanos) {}
