package io.github.stellhub.connectionreuse.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** 连接复用压测客户端启动类。 */
@SpringBootApplication
@EnableConfigurationProperties(BenchmarkProperties.class)
public class ConnectionReuseBenchmarkClientApplication {

  /**
   * 启动压测客户端。
   *
   * @param args 启动参数
   */
  public static void main(String[] args) {
    // only for local
    System.setProperty("log.stdout", "true");
    SpringApplication.run(ConnectionReuseBenchmarkClientApplication.class, args);
  }
}
