package io.github.stellhub.connectionreuse.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.stellflux.http.client.StellfluxHttpClient;
import io.github.stellhub.connectionreuse.proto.BenchmarkRequest;
import io.github.stellhub.connectionreuse.proto.BenchmarkResponse;
import io.github.stellhub.connectionreuse.proto.BenchmarkServiceGrpc;
import io.grpc.ManagedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 连接复用压测执行器。 */
@Component
@RequiredArgsConstructor
public class ConnectionReuseBenchmarkRunner implements ApplicationRunner {

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final BenchmarkProperties properties;
  private final BenchmarkClientFactory clientFactory;
  private final ObjectMapper objectMapper;

  /**
   * 启动后执行 HTTP/gRPC 长短连接压测。
   *
   * @param args 启动参数
   * @throws Exception 压测异常
   */
  @Override
  public void run(ApplicationArguments args) throws Exception {
    String payload = createPayload(properties.getPayloadBytes());
    System.out.println("准备执行连接复用压测：" + summarizeConfig(payload));

    StellfluxHttpClient longHttpClient = clientFactory.createHttpClient();
    ManagedChannel longGrpcChannel = clientFactory.createGrpcChannel();
    try {
      List<BenchmarkScenarioResult> results = new ArrayList<>();
      results.add(
          runScenario("HTTP", "长连接", id -> executeHttp(longHttpClient, id, payload, false)));
      results.add(runScenario("HTTP", "短连接", id -> executeShortHttp(id, payload)));
      results.add(runScenario("gRPC", "长连接", id -> executeGrpc(longGrpcChannel, id, payload)));
      results.add(runScenario("gRPC", "短连接", id -> executeShortGrpc(id, payload)));
      printReport(results);
    } finally {
      closeHttpClient(longHttpClient);
      shutdownChannel(longGrpcChannel);
    }
  }

  /**
   * 执行一个压测场景。
   *
   * @param protocol 协议
   * @param connectionMode 连接模式
   * @param task 压测任务
   * @return 压测结果
   * @throws Exception 压测异常
   */
  private BenchmarkScenarioResult runScenario(
      String protocol, String connectionMode, BenchmarkTask task) throws Exception {
    for (int i = 0; i < properties.getWarmupRequests(); i++) {
      task.execute(protocol + "-" + connectionMode + "-warmup-" + i);
    }

    ExecutorService executor = Executors.newFixedThreadPool(properties.getConcurrency());
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger sequence = new AtomicInteger();
    long[] latencies = new long[properties.getRequests()];
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < properties.getConcurrency(); i++) {
      futures.add(
          executor.submit(
              () -> {
                start.await();
                int index;
                while ((index = sequence.getAndIncrement()) < properties.getRequests()) {
                  long begin = System.nanoTime();
                  task.execute(protocol + "-" + connectionMode + "-" + index);
                  latencies[index] = System.nanoTime() - begin;
                }
                return null;
              }));
    }

    long begin = System.nanoTime();
    start.countDown();
    for (Future<?> future : futures) {
      future.get();
    }
    long totalNanos = System.nanoTime() - begin;
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    return toResult(protocol, connectionMode, latencies, totalNanos);
  }

  /**
   * 执行一次 HTTP 请求。
   *
   * @param httpClient HTTP 客户端
   * @param requestId 请求 ID
   * @param payload 请求内容
   * @param closeConnection 是否显式关闭连接
   * @throws IOException 请求异常
   */
  private void executeHttp(
      StellfluxHttpClient httpClient, String requestId, String payload, boolean closeConnection)
      throws IOException {
    BenchmarkHttpRequest benchmarkRequest =
        new BenchmarkHttpRequest(
            requestId, payload, payload.getBytes(StandardCharsets.UTF_8).length);
    RequestBody body = RequestBody.create(objectMapper.writeValueAsBytes(benchmarkRequest), JSON);
    Request.Builder builder =
        new Request.Builder().url(httpClient.buildUrl("/api/benchmark/echo")).post(body);
    if (closeConnection) {
      builder.header("Connection", "close");
    }

    try (Response response = httpClient.newCall(builder.build()).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new IOException("HTTP request failed, status=" + response.code());
      }
      BenchmarkHttpResponse benchmarkResponse =
          objectMapper.readValue(response.body().bytes(), BenchmarkHttpResponse.class);
      validateResponse(
          requestId, payload, benchmarkResponse.requestId(), benchmarkResponse.payload());
    }
  }

  /**
   * 执行一次 HTTP 短连接请求。
   *
   * @param requestId 请求 ID
   * @param payload 请求内容
   * @throws IOException 请求异常
   */
  private void executeShortHttp(String requestId, String payload) throws IOException {
    StellfluxHttpClient httpClient = clientFactory.createHttpClient();
    try {
      executeHttp(httpClient, requestId, payload, true);
    } finally {
      closeHttpClient(httpClient);
    }
  }

  /**
   * 执行一次 gRPC 请求。
   *
   * @param channel gRPC channel
   * @param requestId 请求 ID
   * @param payload 请求内容
   */
  private void executeGrpc(ManagedChannel channel, String requestId, String payload) {
    BenchmarkRequest request =
        BenchmarkRequest.newBuilder()
            .setRequestId(requestId)
            .setPayload(payload)
            .setPayloadSizeBytes(payload.getBytes(StandardCharsets.UTF_8).length)
            .build();
    BenchmarkResponse response =
        BenchmarkServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(properties.getTimeoutMillis(), TimeUnit.MILLISECONDS)
            .echo(request);
    validateResponse(requestId, payload, response.getRequestId(), response.getPayload());
  }

  /**
   * 执行一次 gRPC 短连接请求。
   *
   * @param requestId 请求 ID
   * @param payload 请求内容
   */
  private void executeShortGrpc(String requestId, String payload) {
    ManagedChannel channel = clientFactory.createGrpcChannel();
    try {
      executeGrpc(channel, requestId, payload);
    } finally {
      shutdownChannel(channel);
    }
  }

  /**
   * 校验服务端回显结果。
   *
   * @param expectedRequestId 期望请求 ID
   * @param expectedPayload 期望请求内容
   * @param actualRequestId 实际请求 ID
   * @param actualPayload 实际请求内容
   */
  private void validateResponse(
      String expectedRequestId,
      String expectedPayload,
      String actualRequestId,
      String actualPayload) {
    if (!expectedRequestId.equals(actualRequestId) || !expectedPayload.equals(actualPayload)) {
      throw new IllegalStateException("Benchmark response does not match request");
    }
  }

  /**
   * 转换压测结果。
   *
   * @param protocol 协议
   * @param connectionMode 连接模式
   * @param latencies 延迟数组
   * @param totalNanos 总耗时
   * @return 压测结果
   */
  private BenchmarkScenarioResult toResult(
      String protocol, String connectionMode, long[] latencies, long totalNanos) {
    List<Long> sorted = new ArrayList<>(latencies.length);
    long sum = 0;
    for (long latency : latencies) {
      sorted.add(latency);
      sum += latency;
    }
    sorted.sort(Comparator.naturalOrder());

    double totalMillis = nanosToMillis(totalNanos);
    double seconds = Math.max(totalNanos / 1_000_000_000.0, 0.000_001);
    return new BenchmarkScenarioResult(
        protocol,
        connectionMode,
        latencies.length,
        properties.getConcurrency(),
        totalMillis,
        latencies.length / seconds,
        nanosToMillis(sum / latencies.length),
        nanosToMillis(percentile(sorted, 0.50)),
        nanosToMillis(percentile(sorted, 0.95)),
        nanosToMillis(percentile(sorted, 0.99)));
  }

  /**
   * 打印压测报告。
   *
   * @param results 压测结果
   */
  private void printReport(List<BenchmarkScenarioResult> results) {
    System.out.println();
    System.out.println("协议   连接模式 请求数 并发 总耗时(ms) 吞吐量(req/s) 平均延迟(ms) P50(ms) P95(ms) P99(ms)");
    for (BenchmarkScenarioResult result : results) {
      System.out.println(formatResult(result));
    }

    printImprovement("HTTP", results);
    printImprovement("gRPC", results);
  }

  /**
   * 打印长连接相对短连接的提升。
   *
   * @param protocol 协议
   * @param results 压测结果
   */
  private void printImprovement(String protocol, List<BenchmarkScenarioResult> results) {
    BenchmarkScenarioResult longResult = findResult(protocol, "长连接", results);
    BenchmarkScenarioResult shortResult = findResult(protocol, "短连接", results);
    double latencyDrop =
        (shortResult.averageLatencyMillis() - longResult.averageLatencyMillis())
            / shortResult.averageLatencyMillis()
            * 100.0;
    double throughputIncrease =
        (longResult.throughputPerSecond() / shortResult.throughputPerSecond() - 1.0) * 100.0;
    System.out.println(
        String.format(
            Locale.ROOT,
            "%s 长连接相对短连接：平均延迟降低 %.2f%%，吞吐量提升 %.2f%%",
            protocol,
            latencyDrop,
            throughputIncrease));
  }

  /**
   * 查找指定场景结果。
   *
   * @param protocol 协议
   * @param connectionMode 连接模式
   * @param results 压测结果
   * @return 场景结果
   */
  private BenchmarkScenarioResult findResult(
      String protocol, String connectionMode, List<BenchmarkScenarioResult> results) {
    return results.stream()
        .filter(
            result ->
                protocol.equals(result.protocol())
                    && connectionMode.equals(result.connectionMode()))
        .findFirst()
        .orElseThrow();
  }

  /**
   * 格式化单行结果。
   *
   * @param result 压测结果
   * @return 单行文本
   */
  private String formatResult(BenchmarkScenarioResult result) {
    return String.format(
        Locale.ROOT,
        "%-6s %-6s %5d %4d %10.2f %14.2f %12.3f %7.3f %7.3f %7.3f",
        result.protocol(),
        result.connectionMode(),
        result.requests(),
        result.concurrency(),
        result.totalMillis(),
        result.throughputPerSecond(),
        result.averageLatencyMillis(),
        result.p50LatencyMillis(),
        result.p95LatencyMillis(),
        result.p99LatencyMillis());
  }

  /**
   * 创建固定大小请求负载。
   *
   * @param payloadBytes 负载字节数
   * @return 请求负载
   */
  private String createPayload(int payloadBytes) {
    StringBuilder builder = new StringBuilder(payloadBytes);
    while (builder.toString().getBytes(StandardCharsets.UTF_8).length < payloadBytes) {
      builder.append("x");
    }
    return builder.toString();
  }

  /**
   * 汇总压测配置。
   *
   * @param payload 请求负载
   * @return 配置摘要
   */
  private String summarizeConfig(String payload) {
    return "requests="
        + properties.getRequests()
        + ", warmupRequests="
        + properties.getWarmupRequests()
        + ", concurrency="
        + properties.getConcurrency()
        + ", payloadBytes="
        + payload.getBytes(StandardCharsets.UTF_8).length
        + ", httpBaseUrl="
        + properties.getHttpBaseUrl()
        + ", grpcTarget="
        + properties.getGrpcHost()
        + ":"
        + properties.getGrpcPort();
  }

  /**
   * 关闭 HTTP 客户端资源。
   *
   * @param httpClient HTTP 客户端
   */
  private void closeHttpClient(StellfluxHttpClient httpClient) {
    httpClient.getDelegate().connectionPool().evictAll();
    httpClient.getDelegate().dispatcher().executorService().shutdown();
  }

  /**
   * 关闭 gRPC channel。
   *
   * @param channel gRPC channel
   */
  private void shutdownChannel(ManagedChannel channel) {
    try {
      channel
          .shutdownNow()
          .awaitTermination(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * 计算百分位延迟。
   *
   * @param sortedLatencies 已排序延迟
   * @param percentile 百分位
   * @return 延迟纳秒
   */
  private long percentile(List<Long> sortedLatencies, double percentile) {
    int index = (int) Math.ceil(percentile * sortedLatencies.size()) - 1;
    return sortedLatencies.get(Math.min(Math.max(index, 0), sortedLatencies.size() - 1));
  }

  /**
   * 纳秒转换为毫秒。
   *
   * @param nanos 纳秒
   * @return 毫秒
   */
  private double nanosToMillis(long nanos) {
    return nanos / 1_000_000.0;
  }
}
