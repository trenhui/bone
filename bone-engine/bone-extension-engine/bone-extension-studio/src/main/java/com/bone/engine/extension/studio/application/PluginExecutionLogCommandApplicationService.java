package com.bone.engine.extension.studio.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.engine.extension.studio.domain.model.execution.PluginExecutionLog;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import com.bone.engine.extension.studio.domain.repository.PluginExecutionLogRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

  private final PluginExecutionLogRepository logRepository;
  private final ExtensionRepository extensionRepository;

  @Transactional
  public Optional<PluginExecutionLog> ingestFromRuntime(
      String className, String methodName, String status, Long durationMs, String errorMessage) {
    if (!StringUtils.hasText(className)) {
      return Optional.empty();
    }
    Extension extension = extensionRepository.findByClassName(className.trim());
    if (extension == null) {
      return Optional.empty();
    }
    String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : "SUCCESS";
    String input =
        "{\"source\":\"runtime-sdk\",\"className\":\""
            + className
            + "\",\"method\":\""
            + (methodName != null ? methodName : "")
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
