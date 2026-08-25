package com.bone.metadata.engine.spi;

import java.util.Optional;

/**
 * 平台桥接端口（SPI）。
 *
 * <p>将 {@code bone-metadata-engine} 与宿主平台（IAM / 多租户 / 事件总线）解耦。引擎领域层仅依赖本端口， 不感知具体平台实现。
 *
 * <p>方法语义：
 *
 * <ul>
 *   <li>{@link #loadPublishedEntityJson(Long, String)} —— 按租户 + 实体编码加载<b>已发布</b>实体定义 （status=1）的
 *       JSON 快照；未接入平台时返回空。
 *   <li>{@link #currentTenantId()} —— 当前租户上下文（接 {@code TenantContext}）。
 *   <li>{@link #publishEvent(String)} —— 发布元数据变更事件（接 Spring {@code ApplicationEventPublisher}）。
 * </ul>
 *
 * <p>默认实现 {@link NoopMetadataPlatformBridge} 在宿主未注入真实桥接时使用，行为与既有 {@code
 * platform.NoopMetadataPlatformBridge} 保持一致（META-ENG-01）。
 */
public interface MetadataPlatformBridge {

  Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode);

  /**
   * @return 当前租户 ID；未接入时返回空
   */
  Optional<String> currentTenantId();

  /** 发布元数据变更事件。 */
  void publishEvent(String eventJson);
}
