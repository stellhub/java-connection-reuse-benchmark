package io.github.stellhub.connectionreuse.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 连接复用压测服务端启动类。 */
@SpringBootApplication
public class ConnectionReuseBenchmarkServerApplication {

  /**
   * 启动 HTTP 与 gRPC 服务端。
   *
   * @param args 启动参数
   */
  public static void main(String[] args) {
    // only for local
    System.setProperty("log.stdout", "true");
    SpringApplication.run(ConnectionReuseBenchmarkServerApplication.class, args);
  }
}
