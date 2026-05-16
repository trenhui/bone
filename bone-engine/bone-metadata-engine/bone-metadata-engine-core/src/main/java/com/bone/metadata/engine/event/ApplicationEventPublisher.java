package com.bone.metadata.engine.event;

/** 应用事件发布器接口 用于发布应用事件 */
public interface ApplicationEventPublisher {

  /**
   * 发布事件
   *
   * @param event 事件对象
   */
  void publishEvent(Object event);

  /**
   * 发布元数据变更事件
   *
   * @param event 元数据变更事件
   */
  void publishEvent(MetadataChangedEvent event);
}
