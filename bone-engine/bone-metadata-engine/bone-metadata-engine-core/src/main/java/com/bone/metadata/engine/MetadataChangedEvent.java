package com.bone.metadata.engine;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import org.springframework.context.ApplicationEvent;

/** 元数据变更事件类 用于在元数据发生变更时发布Spring事件通知 */
public class MetadataChangedEvent extends ApplicationEvent {

  private final EntityMetadata entityMetadata;
  private final MetadataChangeType changeType;
  private final long timestamp;

  /**
   * 构造函数
   *
   * @param source 事件源
   * @param entityMetadata 变更的实体元数据
   * @param changeType 变更类型
   */
  public MetadataChangedEvent(
      Object source, EntityMetadata entityMetadata, MetadataChangeType changeType) {
    super(source);
    this.entityMetadata = entityMetadata;
    this.changeType = changeType;
    this.timestamp = System.currentTimeMillis();
  }

  /** 获取变更的实体元数据 */
  public EntityMetadata getEntityMetadata() {
    return entityMetadata;
  }

  /** 获取变更类型 */
  public MetadataChangeType getChangeType() {
    return changeType;
  }

  /** 获取事件发生时间戳 */
  public long getEventTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return "MetadataChangedEvent{"
        + "entity="
        + (entityMetadata != null ? entityMetadata.getApiName() : "null")
        + ", type="
        + changeType
        + ", timestamp="
        + getTimestamp()
        + '}';
  }
}
