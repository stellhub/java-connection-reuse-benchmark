package io.github.stellhub.connectionreuse.client;

import io.github.stellflux.grpc.client.StellfluxGrpcChannelFactory;
import io.github.stellflux.grpc.client.StellfluxGrpcClientOptions;
import io.github.stellflux.http.client.StellfluxHttpClient;
import io.github.stellflux.http.client.StellfluxHttpClientFactory;
import io.github.stellflux.http.client.StellfluxHttpClientOptions;
import io.grpc.ManagedChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** stellflux HTTP 与 gRPC 客户端创建器。 */
@Component
@RequiredArgsConstructor
public class BenchmarkClientFactory {

  private final StellfluxHttpClientFactory httpClientFactory;
  private final StellfluxGrpcChannelFactory grpcChannelFactory;
  private final BenchmarkProperties properties;

  /**
   * 创建 HTTP 客户端。
   *
   * @return HTTP 客户端
   */
  public StellfluxHttpClient createHttpClient() {
    StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
    options.setBaseUrl(properties.getHttpBaseUrl());
    options.setConnectTimeoutMillis(properties.getTimeoutMillis());
    options.setReadTimeoutMillis(properties.getTimeoutMillis());
    options.setWriteTimeoutMillis(properties.getTimeoutMillis());
    options.setCallTimeoutMillis(properties.getTimeoutMillis());
    options.setRetryOnConnectionFailure(false);
    return httpClientFactory.create(options);
  }

  /**
   * 创建 gRPC 连接。
   *
   * @return gRPC channel
   */
  public ManagedChannel createGrpcChannel() {
    StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
    options.setHost(properties.getGrpcHost());
    options.setPort(properties.getGrpcPort());
    options.setPlaintext(true);
    return grpcChannelFactory.create(options);
  }
}
