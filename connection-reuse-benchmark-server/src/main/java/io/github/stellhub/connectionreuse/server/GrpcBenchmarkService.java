package io.github.stellhub.connectionreuse.server;

import io.github.stellflux.grpc.server.annotation.RpcService;
import io.github.stellhub.connectionreuse.proto.BenchmarkRequest;
import io.github.stellhub.connectionreuse.proto.BenchmarkResponse;
import io.github.stellhub.connectionreuse.proto.BenchmarkServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;

/** gRPC 连接复用压测接口。 */
@RpcService(serviceId = "connection-reuse-benchmark.grpc")
public class GrpcBenchmarkService extends BenchmarkServiceGrpc.BenchmarkServiceImplBase {

  /**
   * 回显同一份压测请求。
   *
   * @param request 压测请求
   * @param responseObserver 响应观察者
   */
  @Override
  public void echo(BenchmarkRequest request, StreamObserver<BenchmarkResponse> responseObserver) {
    int payloadBytes = request.getPayload().getBytes(StandardCharsets.UTF_8).length;
    BenchmarkResponse response =
        BenchmarkResponse.newBuilder()
            .setRequestId(request.getRequestId())
            .setPayload(request.getPayload())
            .setPayloadSizeBytes(payloadBytes)
            .setServerTimeNanos(System.nanoTime())
            .build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }
}
