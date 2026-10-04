package com.bone.engine.extension.support.studio;

import com.bone.core.tenant.context.TenantContext;
import com.bone.engine.extension.support.config.ExtensionProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/** 将运行时扩展调用结果异步上报至 bone-extension-studio。 */
@Component
@ConditionalOnProperty(
    prefix = "bone.extension.studio.report",
    name = "enabled",
    havingValue = "true")
public class StudioExecutionLogReporter {

  private static final Logger log = LoggerFactory.getLogger(StudioExecutionLogReporter.class);

  /** 与 studio 侧 ReporterTokenFilter 对应的请求头名（数据面机器身份，非用户 JWT）。 */
  private static final String REPORTER_TOKEN_HEADER = "X-Reporter-Token";

  private final RestTemplate restTemplate;
  private final String ingestUrl;
  private final String reporterToken;

  /**
   * 上报通道是否可用（令牌已配置）。
   *
   * <p>studio 侧对上报端点默认<b>失败关闭</b>：未配置 {@code reporter-token} 时一律拒绝（503）。此时若仍照常发请求， 只会每次都白跑一次网络往返并刷
   * WARN 噪声， 反而把真正的根因（"没配 token"）淹没在重复日志里。 故此处短路。
   */
  private final boolean channelUsable;

  public StudioExecutionLogReporter(
      ExtensionProperties properties, RestTemplateBuilder restTemplateBuilder) {
    ExtensionProperties.StudioReportConfig report = properties.getStudio().getReport();
    String base =
        StringUtils.hasText(report.getBaseUrl())
            ? report.getBaseUrl().trim()
            : "http://localhost:8088";
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.ingestUrl = base + "/api/v1/extension/execution-logs:ingest";
    this.reporterToken = StringUtils.hasText(report.getToken()) ? report.getToken().trim() : null;
    this.channelUsable = reporterToken != null;
    this.restTemplate = restTemplateBuilder.build();
    if (!channelUsable) {
      log.warn(
          "bone.extension.studio.report.token 未配置 —— 执行日志上报通道按失败前置处理，"
              + "本次及后续上报均不会发出（studio 侧默认失败关闭，会拒绝无令牌上报）。"
              + "请注入与 studio 侧 reporter-token 一致的密钥");
    }
    log.info(
        "Studio execution log reporter enabled, ingest={}, tokenConfigured={}",
        ingestUrl,
        channelUsable);
  }

  public void reportSuccess(
      String className, String methodName, @Nullable String extPointName, long durationMs) {
    report(className, methodName, extPointName, "SUCCESS", durationMs, null);
  }

  public void reportFailure(
      String className,
      String methodName,
      @Nullable String extPointName,
      long durationMs,
      String errorMessage) {
    report(className, methodName, extPointName, "FAILED", durationMs, errorMessage);
  }

  private void report(
      String className,
      String methodName,
      @Nullable String extPointName,
      String status,
      long durationMs,
      String errorMessage) {
    if (!StringUtils.hasText(className)) {
      return;
    }
    // 失败前置：令牌未配置 ⇒ 通道不可用（根因已在构造时告警一次），此处不再重复打日志，
    // 避免每次调用都刷一行把真正原因淹没。
    if (!channelUsable) {
      return;
    }
    // 执行日志按租户内聚存储，上报必须携带当前请求租户（ingest 端点据此落库）；
    // 无租户上下文（如调度线程）时 studio 侧拒绝，避免日志误归租户。
    String tenantId = TenantContext.getTenantId();
    CompletableFuture.runAsync(
        () -> {
          try {
            Map<String, Object> body = new HashMap<>();
            body.put("className", className);
            body.put("methodName", methodName);
            if (StringUtils.hasText(extPointName)) {
              body.put("extPointName", extPointName);
            }
            body.put("status", status);
            body.put("durationMs", durationMs);
            if (StringUtils.hasText(errorMessage)) {
              body.put("errorMessage", errorMessage);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (reporterToken != null) {
              headers.set(REPORTER_TOKEN_HEADER, reporterToken);
            }
            if (StringUtils.hasText(tenantId)) {
              headers.set("X-Tenant-Id", tenantId);
            }
            restTemplate.postForEntity(ingestUrl, new HttpEntity<>(body, headers), String.class);
          } catch (Exception ex) {
            log.debug("Failed to report execution log to studio: {}", ex.getMessage());
          }
        });
  }
}
