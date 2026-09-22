package com.bone.engine.extension.studio.sync;

import com.bone.engine.extension.api.model.sync.ExtensionRoutingMetadata;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.support.sync.ExtensionCodeResolver;
import com.bone.engine.extension.support.sync.ExtensionMetadataStore;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfig;
import com.bone.engine.extension.support.sync.ExtensionRuntimeConfigParser;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/** 将 Studio 扩展元数据推送到运行时元数据存储（Redis 或内存），由业务进程 {@code MetadataOverlayExtensionRepository} 叠加。 */
public class RuntimeExtensionSyncService {

  private static final Logger log = LoggerFactory.getLogger(RuntimeExtensionSyncService.class);

  private final ExtensionMetadataStore metadataStore;
  private final ExtPointRepository extPointRepository;

  public RuntimeExtensionSyncService(
      ExtensionMetadataStore metadataStore, ExtPointRepository extPointRepository) {
    this.metadataStore = metadataStore;
    this.extPointRepository = extPointRepository;
  }

  public boolean publish(@NonNull Extension extension) {
    ExtPoint extPoint = resolveExtPoint(extension);
    if (extPoint == null || !StringUtils.hasText(extPoint.getInterfaceName())) {
      log.warn("Skip runtime sync: ext point not found for extension id={}", extension.getId());
      return false;
    }
    ExtensionRoutingMetadata metadata = toMetadata(extension, extPoint.getInterfaceName());
    metadataStore.save(metadata);
    metadataStore.publishRefresh(metadata.getExtensionPoint());
    log.info(
        "Published extension metadata: point={}, code={}, enabled={}, traffic={}",
        metadata.getExtensionPoint(),
        metadata.getCode(),
        metadata.isEnabled(),
        metadata.getTraffic());
    return true;
  }

  public void unpublish(@NonNull Extension extension, @Nullable ExtPoint extPoint) {
    ExtPoint point = extPoint != null ? extPoint : resolveExtPoint(extension);
    if (point == null || !StringUtils.hasText(point.getInterfaceName())) {
      return;
    }
    String code = resolveExtensionCode(extension);
    metadataStore.remove(point.getInterfaceName(), code);
    metadataStore.publishRefresh(point.getInterfaceName());
    log.info("Removed extension metadata: point={}, code={}", point.getInterfaceName(), code);
  }

  @NonNull
  public List<String> validateForRuntime(@NonNull Extension extension) {
    List<String> errors = new ArrayList<>();
    if (!StringUtils.hasText(extension.getClassName())) {
      errors.add("className 不能为空");
    }
    ExtPoint extPoint = resolveExtPoint(extension);
    if (extPoint == null || !StringUtils.hasText(extPoint.getInterfaceName())) {
      errors.add("关联扩展点不存在或未配置 interfaceName");
    }
    String code = resolveExtensionCode(extension);
    if (!StringUtils.hasText(code)) {
      errors.add("无法解析扩展 code");
    }
    ExtensionRuntimeConfig runtimeConfig =
        ExtensionRuntimeConfigParser.parse(extension.getConfig());
    if (StringUtils.hasText(runtimeConfig.getCode())
        && !runtimeConfig.getCode().trim().equals(code)) {
      errors.add(
          "config.code 与 name/类名推导的 code 不一致: config="
              + runtimeConfig.getCode().trim()
              + ", resolved="
              + code);
    }
    if (runtimeConfig.getTraffic() != null
        && (runtimeConfig.getTraffic() < 0 || runtimeConfig.getTraffic() > 100)) {
      errors.add("config.traffic 须在 0-100 之间");
    }
    return errors;
  }

  @NonNull
  public static String resolveExtensionCode(@NonNull Extension extension) {
    ExtensionRuntimeConfig runtimeConfig =
        ExtensionRuntimeConfigParser.parse(extension.getConfig());
    if (StringUtils.hasText(runtimeConfig.getCode())) {
      return runtimeConfig.getCode().trim();
    }
    return ExtensionCodeResolver.resolve(
        extension.getName(), extension.getClassName(), extension.getId());
  }

  private ExtPoint resolveExtPoint(Extension extension) {
    if (extension.getExtPointId() == null) {
      return null;
    }
    return extPointRepository.findById(extension.getExtPointId());
  }

  private ExtensionRoutingMetadata toMetadata(Extension extension, String extensionPoint) {
    ExtensionRuntimeConfig runtimeConfig =
        ExtensionRuntimeConfigParser.parse(extension.getConfig());
    int priority = extension.getPriority() != null ? extension.getPriority() : 100;
    int weight = runtimeConfig.getWeight() != null ? runtimeConfig.getWeight() : 100;
    int traffic = runtimeConfig.getTraffic() != null ? runtimeConfig.getTraffic() : 100;
    boolean defaultImpl = Boolean.TRUE.equals(runtimeConfig.getDefaultImpl());

    var builder =
        ExtensionRoutingMetadata.builder()
            .extensionPoint(extensionPoint)
            .code(resolveExtensionCode(extension))
            .tenant(extension.getTenantCode())
            .bizCode(extension.getBizCode())
            .useCase(extension.getUseCase())
            .scenario(extension.getScenario())
            .userGroup(extension.getUserGroup())
            .priority(priority)
            .weight(weight)
            .traffic(Math.min(100, Math.max(0, traffic)))
            .enabled(extension.isEnabled())
            .defaultImpl(defaultImpl)
            .version(System.currentTimeMillis());

    if (StringUtils.hasText(runtimeConfig.getEnv())) {
      builder.env(runtimeConfig.getEnv().trim());
    }
    if (runtimeConfig.getCondition() != null) {
      builder.condition(runtimeConfig.getCondition());
    }
    return builder.build();
  }
}
