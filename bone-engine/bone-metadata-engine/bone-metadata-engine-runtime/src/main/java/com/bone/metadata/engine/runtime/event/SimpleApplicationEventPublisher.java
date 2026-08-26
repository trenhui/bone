package com.bone.metadata.engine.runtime.event;

import lombok.extern.slf4j.Slf4j;

/** 简单应用事件发布器实现 提供基本的事件发布功能 */
@Slf4j
public class SimpleApplicationEventPublisher implements ApplicationEventPublisher {

  @Override
  public void publishEvent(Object event) {
    // 简单实现：记录事件发布
    log.debug("Publishing event: {}", event.getClass().getSimpleName());

    // 如果是MetadataChangedEvent类型，调用特定方法
    if (event instanceof MetadataChangedEvent) {
      publishEvent((MetadataChangedEvent) event);
    }
  }

  @Override
  public void publishEvent(MetadataChangedEvent event) {
    // 简单实现：记录元数据变更事件
    log.debug(
        "Publishing metadata changed event: {} for entity {} in tenant {}",
        event.getAction(),
        event.getMetadata() != null ? event.getMetadata().getApiName() : "null",
        event.getTenantId());
  }
}
