package com.bone.metadata.engine.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.engine.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 平台桥接的真实实现（runtime 层）。
 *
 * <p>实现 {@link MetadataPlatformBridge}，将引擎与宿主平台解耦的端口接真：
 *
 * <ul>
 *   <li>{@link #currentTenantId()} —— 读取 {@link TenantContext} 当前租户 ID。
 *   <li>{@link #publishEvent(String)} —— 经 Spring {@link ApplicationEventPublisher} 发布元数据变更事件。
 *   <li>{@link #loadPublishedEntityJson(Long, String)} —— 经 {@code bone-metadata-sdk} 的 {@link
 *       Repository} 读取已发布（status=1）的 {@code meta_entity}，序列化为 JSON 快照。
 * </ul>
 *
 * <p>默认引擎库无真实数据源/事件总线时，宿主可注入 {@code NoopMetadataPlatformBridge} 回退。
 */
@Component
public class IamMetadataBridge implements MetadataPlatformBridge {

  private final Repository<MetaEntityPo, Long> entityRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public IamMetadataBridge(
      Repository<MetaEntityPo, Long> entityRepository,
      ApplicationEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.entityRepository = entityRepository;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<String> currentTenantId() {
    return Optional.ofNullable(TenantContext.getTenantId());
  }

  @Override
  public void publishEvent(String eventJson) {
    // 将变更事件 JSON 快照作为 Spring 事件发布，监听方按需订阅。
    eventPublisher.publishEvent(eventJson);
  }

  @Override
  public Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return Optional.empty();
    }
    Criteria<MetaEntityPo> criteria =
        Criteria.<MetaEntityPo>create().eq("code", entityCode).eq("status", 1);
    if (tenantId != null) {
      criteria.eq("tenantId", tenantId);
    }
    MetaEntityPo po = entityRepository.findOneByCriteria(criteria);
    if (po == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(objectMapper.writeValueAsString(po));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }
}
