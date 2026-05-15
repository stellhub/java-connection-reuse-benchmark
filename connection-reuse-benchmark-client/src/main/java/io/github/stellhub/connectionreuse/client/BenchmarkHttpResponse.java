package io.github.stellhub.connectionreuse.client;

/** HTTP 压测响应。 */
public record BenchmarkHttpResponse(
    String requestId, String payload, int payloadBytes, long serverTimeNanos) {}
