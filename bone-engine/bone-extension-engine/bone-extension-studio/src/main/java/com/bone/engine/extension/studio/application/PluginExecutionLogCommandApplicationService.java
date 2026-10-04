package com.bone.engine.extension.studio.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.engine.extension.studio.domain.gateway.TenantProvider;
import com.bone.engine.extension.studio.domain.model.execution.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.PluginExecutionLogRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 插件执行日志写侧。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务写入 PluginExecutionLog 聚合，当前不发布领域事件； 若将来接入事件发布，须改为调用
 * publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
public class PluginExecutionLogCommandApplicationService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PluginExecutionLogCommandApplicationService.class);

  /** 平台租户：进程级上报无用户会话时的默认归属（tenant_id=0，tenant_code='*' 通配）。 */
  private static final long PLATFORM_TENANT_ID = 0L;

  private final PluginExecutionLogRepository logRepository;
  private final ExtensionRepository extensionRepository;
  private final ExtPointRepository extPointRepository;
  private final TenantProvider tenantProvider;

  /**
   * 数据面运行时上报执行日志。
   *
   * <p>数据面（如 blueprint）以本地 {@code @Extension} 定义的扩展并不在 studio 注册表内， 此时按上报的扩展点接口 FQCN 与实现类 FQCN
   * 幂等登记后再落日志，避免可观测数据被丢弃。
   *
   * <p>上报不携带终端用户 JWT：无租户上下文时按平台租户 0 落库， 否则 Metadata SDK 对租户表直接抛 {@code
   * MissingTenantContextException}（fail-closed），整条日志 500 丢弃。
   *
   * <p><b>租户访问经 {@link TenantProvider} 端口</b>（E-2，2026-10-03 修正）：此前本方法直接 {@code
   * TenantContext.setTenantId(0)} + {@code clear()}，违反 {@code tenant_context_via_provider} 规则。现改为
   * {@code tenantProvider.runAs(0, ...)}，把「设租户 → 执行 → 恢复原上下文」的 try/finally 收敛到基础设施层， 避免业务分支提前
   * return / 抛异常时 ThreadLocal 上下文泄漏到同线程后续请求。
   */
  @Transactional
  public Optional<PluginExecutionLog> ingestFromRuntime(
      String className,
      String methodName,
      @Nullable String extPointName,
      String status,
      Long durationMs,
      String errorMessage) {
    if (!StringUtils.hasText(className)) {
      return Optional.empty();
    }
    if (tenantProvider.currentTenantIdOrNull() != null) {
      return doIngest(className, methodName, extPointName, status, durationMs, errorMessage);
    }
    // 无租户上下文：经端口在平台租户作用域内执行，作用域与「恢复原上下文」由适配器 try/finally 闭合（E-2）。
    return tenantProvider.runAs(
        PLATFORM_TENANT_ID,
        () -> doIngest(className, methodName, extPointName, status, durationMs, errorMessage));
  }

  private Optional<PluginExecutionLog> doIngest(
      String className,
      String methodName,
      @Nullable String extPointName,
      String status,
      Long durationMs,
      String errorMessage) {
    String fqcn = className.trim();
    Extension extension = extensionRepository.findByClassName(fqcn);
    if (extension == null) {
      extension = discoverRuntimeExtension(fqcn, extPointName);
    }
    if (extension == null) {
      return Optional.empty();
    }
    String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "SUCCESS";
    String input =
        "{\"source\":\"runtime-sdk\",\"className\":\""
            + fqcn
            + "\",\"method\":\""
            + (methodName != null ? methodName : "")
            + "\",\"extPoint\":\""
            + (extPointName != null ? extPointName : "")
            + "\"}";
    return Optional.of(
        record(
            extension,
            "INVOKE",
            normalizedStatus,
            input,
            null,
            errorMessage,
            durationMs != null ? durationMs : 0L));
  }

  /** 运行时扩展自动登记；返回 null 表示缺少扩展点信息、无法归属。 */
  @Nullable
  private Extension discoverRuntimeExtension(String className, @Nullable String extPointName) {
    if (!StringUtils.hasText(extPointName)) {
      return null;
    }
    String iface = extPointName.trim();
    ExtPoint point = extPointRepository.findByInterfaceName(iface);
    if (point == null) {
      point =
          extPointRepository.save(
              ExtPoint.create(simpleName(iface), iface, "运行时自动登记的扩展点（数据面执行上报发现）"));
    }
    if (point == null || point.getId() == null) {
      return null;
    }
    Extension created =
        Extension.create(
            point.getId(), simpleName(className), "运行时自动登记的扩展实现（数据面执行上报发现）", className);
    try {
      return extensionRepository.save(created);
    } catch (RuntimeException ex) {
      // 并发首次上报可能重复登记，回退按 className 重新查找（仍无则本次 404，下次自然命中）
      LOGGER.warn("运行时扩展登记失败，回退按 className 查找: {}", ex.getMessage());
      return extensionRepository.findByClassName(className);
    }
  }

  private static String simpleName(@Nullable String fqcn) {
    if (fqcn == null) {
      return "";
    }
    if (!StringUtils.hasText(fqcn)) {
      return fqcn;
    }
    int idx = fqcn.lastIndexOf('.');
    return idx >= 0 && idx < fqcn.length() - 1 ? fqcn.substring(idx + 1) : fqcn;
  }

  @Transactional
  public PluginExecutionLog record(
      Extension extension,
      String action,
      String status,
      String input,
      String output,
      String error,
      long durationMs) {
    PluginExecutionLog log = new PluginExecutionLog();
    log.setPluginId(extension.getId());
    log.setExtensionPointId(extension.getExtPointId());
    log.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
    log.setStatus(status);
    log.setInputData(input != null ? input : "{\"action\":\"" + action + "\"}");
    log.setOutputData(output);
    log.setErrorMessage(error);
    log.setDurationMs(durationMs);
    return logRepository.save(log);
  }
}
