package com.bone.metadata.engine.runtime.event;

import com.bone.metadata.engine.domain.model.EntityMetadata;

/** 元数据变更事件 当元数据发生变更时触发 */
public class MetadataChangedEvent {

  public enum Action {
    CREATED,
    UPDATED,
    DELETED
  }

  private EntityMetadata metadata;
  private Action action;
  private String tenantId;
  private long timestamp;

  // 构造方法
  public MetadataChangedEvent(EntityMetadata metadata, Action action, String tenantId) {
    this.metadata = metadata;
    this.action = action;
    this.tenantId = tenantId;
    this.timestamp = System.currentTimeMillis();
  }

  // getter方法
  public EntityMetadata getMetadata() {
    return metadata;
  }

  public Action getAction() {
    return action;
  }

  public String getTenantId() {
    return tenantId;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
