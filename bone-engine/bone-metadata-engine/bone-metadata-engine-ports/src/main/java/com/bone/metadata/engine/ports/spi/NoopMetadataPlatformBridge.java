package com.bone.metadata.engine.ports.spi;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认空实现：宿主未注入真实平台桥接时使用（META-ENG-01）。
 *
 * <p>行为与原 {@code platform.NoopMetadataPlatformBridge} 一致：所有读返回空、事件静默丢弃。后续 T4 由 {@code
 * IamMetadataBridge} 提供接真实现（接 {@code TenantContext} / Spring event / IAM client）。
 */
public class NoopMetadataPlatformBridge implements MetadataPlatformBridge {

  private static final Logger log = LoggerFactory.getLogger(NoopMetadataPlatformBridge.class);

  @Override
  public Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode) {
    log.debug("MetadataPlatformBridge noop: tenantId={}, entityCode={}", tenantId, entityCode);
    return Optional.empty();
  }

  @Override
  public Optional<String> currentTenantId() {
    return Optional.empty();
  }

  @Override
  public void publishEvent(String eventJson) {
    log.debug("MetadataPlatformBridge noop publishEvent: {}", eventJson);
  }
}
