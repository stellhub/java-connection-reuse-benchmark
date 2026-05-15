package io.github.stellhub.connectionreuse.client;

/** 单个压测场景结果。 */
public record BenchmarkScenarioResult(
    String protocol,
    String connectionMode,
    int requests,
    int concurrency,
    double totalMillis,
    double throughputPerSecond,
    double averageLatencyMillis,
    double p50LatencyMillis,
    double p95LatencyMillis,
    double p99LatencyMillis) {}
