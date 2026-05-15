package io.github.stellhub.connectionreuse.client;

/** 单次压测请求任务。 */
@FunctionalInterface
public interface BenchmarkTask {

  /**
   * 执行一次压测请求。
   *
   * @param requestId 请求 ID
   * @throws Exception 请求异常
   */
  void execute(String requestId) throws Exception;
}
