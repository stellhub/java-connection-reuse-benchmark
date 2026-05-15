package io.github.stellhub.connectionreuse.client;

/** HTTP 压测请求。 */
public record BenchmarkHttpRequest(String requestId, String payload, int payloadBytes) {}
