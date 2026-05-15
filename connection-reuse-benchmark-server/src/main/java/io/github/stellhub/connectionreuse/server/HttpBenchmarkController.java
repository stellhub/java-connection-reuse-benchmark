package io.github.stellhub.connectionreuse.server;

import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 连接复用压测接口。 */
@RestController
@RequestMapping("/api/benchmark")
public class HttpBenchmarkController {

  /**
   * 返回服务端健康状态。
   *
   * @return 健康状态
   */
  @GetMapping("/health")
  public String health() {
    return "ok";
  }

  /**
   * 回显同一份压测请求。
   *
   * @param request 压测请求
   * @return 压测响应
   */
  @PostMapping("/echo")
  public BenchmarkResponse echo(@RequestBody BenchmarkRequest request) {
    int payloadBytes = request.payload().getBytes(StandardCharsets.UTF_8).length;
    return new BenchmarkResponse(
        request.requestId(), request.payload(), payloadBytes, System.nanoTime());
  }
}
