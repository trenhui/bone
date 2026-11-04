package com.bone.claim.e2e;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 仅当显式开启本地联调（{@code -De2e.local=true} 或环境变量 {@code E2E_LOCAL=on/1}）且本地 MySQL（{@code
 * 127.0.0.1:3306}）可达时，才执行 {@link ClaimLifecycleE2ETest}。
 *
 * <p>作为 JUnit {@link ExecutionCondition} 在容器级（Spring 上下文加载之前）判定，因此 CI 或缺库环境会被干净地 跳过，而不会触发重量级 Spring
 * 上下文装载与连接失败。
 */
class LocalE2EEnvironment implements ExecutionCondition {

  private static final String MYSQL_HOST = "127.0.0.1";
  private static final int MYSQL_PORT = 3306;
  private static final int CONNECT_TIMEOUT_MS = 2000;

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (!localE2EEnabled()) {
      return ConditionEvaluationResult.disabled(
          "本地联调 E2E 未启用（运行测试时加 -De2e.local=true 或环境变量 E2E_LOCAL=on）");
    }
    if (!mysqlReachable()) {
      return ConditionEvaluationResult.disabled(
          "本地 MySQL(" + MYSQL_HOST + ":" + MYSQL_PORT + ") 不可达，跳过 E2E 联调");
    }
    return ConditionEvaluationResult.enabled("本地联调环境就绪（本地 MySQL 可达）");
  }

  private static boolean localE2EEnabled() {
    String flag = System.getProperty("e2e.local", System.getenv("E2E_LOCAL"));
    return flag != null
        && ("true".equalsIgnoreCase(flag) || "on".equalsIgnoreCase(flag) || "1".equals(flag));
  }

  private static boolean mysqlReachable() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(MYSQL_HOST, MYSQL_PORT), CONNECT_TIMEOUT_MS);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
