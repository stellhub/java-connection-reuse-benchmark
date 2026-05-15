# java-connection-reuse-benchmark

Java benchmark suite for comparing persistent connection reuse with per-request connection creation across HTTP and gRPC workloads.

## 模块

- `connection-reuse-benchmark-server`：同时提供 HTTP Server 与 gRPC Server。
- `connection-reuse-benchmark-client`：同时提供 HTTP Client 与 gRPC Client，并执行四组压测：
  - HTTP 长连接：复用同一个 `StellfluxHttpClient`
  - HTTP 短连接：每次请求创建并关闭一个 `StellfluxHttpClient`
  - gRPC 长连接：复用同一个 `ManagedChannel`
  - gRPC 短连接：每次请求创建并关闭一个 `ManagedChannel`

## 依赖

两个模块都接入 stellflux HTTP / gRPC starter：

```xml
<dependency>
    <groupId>io.github.stellhub</groupId>
    <artifactId>stellflux-spring-boot-starter-http</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.github.stellhub</groupId>
    <artifactId>stellflux-spring-boot-starter-grpc</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 运行

先构建：

```bash
mvn clean package
```

启动服务端：

```bash
mvn -pl connection-reuse-benchmark-server org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认端口：

- HTTP: `http://127.0.0.1:18080/api/benchmark/echo`
- gRPC: `127.0.0.1:19090`

另开终端启动客户端：

```bash
mvn -pl connection-reuse-benchmark-client org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

可以通过启动参数调整压测规模：

```bash
mvn -pl connection-reuse-benchmark-client org.springframework.boot:spring-boot-maven-plugin:3.5.14:run \
  -Dspring-boot.run.arguments="--benchmark.requests=5000 --benchmark.warmup-requests=500 --benchmark.concurrency=32 --benchmark.payload-bytes=512"
```

客户端会输出每组的总耗时、吞吐量、平均延迟、P50/P95/P99，并计算：

- HTTP 长连接相对 HTTP 短连接的平均延迟降低比例和吞吐量提升比例
- gRPC 长连接相对 gRPC 短连接的平均延迟降低比例和吞吐量提升比例

## 示例结果

| 协议 | 连接模式 | 请求数 | 并发 | 总耗时(ms) | 吞吐量(req/s) | 平均延迟(ms) | P50(ms) | P95(ms) | P99(ms) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| HTTP | 长连接 | 1000 | 16 | 73.41 | 13621.58 | 1.167 | 0.533 | 3.149 | 13.587 |
| HTTP | 短连接 | 1000 | 16 | 92.22 | 10843.61 | 1.462 | 1.164 | 3.304 | 6.597 |
| gRPC | 长连接 | 1000 | 16 | 82.90 | 12063.24 | 1.322 | 0.459 | 4.694 | 22.245 |
| gRPC | 短连接 | 1000 | 16 | 164.42 | 6081.81 | 2.620 | 1.693 | 6.112 | 23.142 |

- HTTP 长连接相对短连接：平均延迟降低 `20.20%`，吞吐量提升 `25.62%`。
- gRPC 长连接相对短连接：平均延迟降低 `49.52%`，吞吐量提升 `98.35%`。

## 短连接风险

OkHttp 和 gRPC Java 默认都倾向于连接复用：复用同一个 `OkHttpClient` / `StellfluxHttpClient`，或者复用同一个 `ManagedChannel`，就会复用内部连接池、HTTP/2 连接、线程和握手结果。真正危险的通常不是框架默认行为，而是业务代码在请求路径里反复 `new` 中间件客户端。

这类问题在中间件降级逻辑里很常见：

- 本地配置读不到时，临时 `new` 一个配置中心客户端去远程拉配置。
- 注册中心实例列表没有及时更新时，临时 `new` 一个注册中心客户端去刷新实例。
- 动态请求、高频请求或兜底逻辑里，临时 `new` 任意中间件客户端，例如配置中心、注册中心、RPC、消息队列、缓存、对象存储、搜索服务客户端。

这些中间件客户端内部往往会封装 `OkHttpClient`、`ManagedChannel`、连接池、线程池或后台刷新任务。平时低频触发时问题不明显；一旦故障或降级条件被大量请求同时命中，业务侧就会从“复用少量稳定连接”退化成“每个请求都创建客户端、建连、握手、认证、初始化线程或后台任务”。结果通常是：

- 客户端 CPU、线程、文件描述符和端口被快速消耗。
- 中间件服务端收到连接风暴，建连、TLS 握手、HTTP/2 preface、认证和初始化成本被放大。
- 原本用于保护业务的降级逻辑，反而把配置中心、注册中心或其他中间件服务端打崩。
- 中间件被打崩后，更多请求进入降级路径，形成放大循环。

业务开发里应默认把中间件客户端作为长生命周期对象管理，例如 Spring Bean、单例组件或受控连接池。降级路径中可以触发一次性刷新、读缓存、使用快照、异步恢复或限流重试，但不应该在高频请求路径中反复创建新的中间件客户端。

如果确实需要临时客户端，也应加上严格边界：低频触发、并发保护、超时、熔断、限流、生命周期关闭和指标告警。否则所谓“兜底”很容易变成新的故障源。

## 说明

四组压测使用同一份请求语义：`requestId + payload + payloadBytes`。HTTP 通过 JSON POST 回显，gRPC 通过同名 protobuf message 回显。

一般来说，长连接会减少 TCP 建连、TLS/HTTP2/gRPC channel 初始化、连接池 warmup 等成本；短连接每个请求都要承担这些成本。实际提升幅度与机器、并发、payload 大小、网络和 JVM 状态有关，所以请以本仓库在目标环境跑出的报告为准。
