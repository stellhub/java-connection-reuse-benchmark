package io.github.stellhub.connectionreuse.server;

/** HTTP 压测请求。 */
public record BenchmarkRequest(String requestId, String payload, int payloadBytes) {}
