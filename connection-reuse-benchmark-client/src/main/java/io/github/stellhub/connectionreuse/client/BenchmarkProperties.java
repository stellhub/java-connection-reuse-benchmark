package io.github.stellhub.connectionreuse.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 连接复用压测配置。 */
@Data
@ConfigurationProperties(prefix = "benchmark")
public class BenchmarkProperties {

  private int warmupRequests = 100;

  private int requests = 1000;

  private int concurrency = 16;

  private int payloadBytes = 256;

  private long timeoutMillis = 3000;

  private String httpBaseUrl = "http://127.0.0.1:18080";

  private String grpcHost = "127.0.0.1";

  private int grpcPort = 19090;
}
