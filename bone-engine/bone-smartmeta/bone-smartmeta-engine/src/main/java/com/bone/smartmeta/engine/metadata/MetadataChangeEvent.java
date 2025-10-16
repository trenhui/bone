package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import java.time.LocalDateTime;

/**
 * 元数据变更事件类
 * 用于通知元数据变更，支持热加载和动态更新
 */
@Getter
public class MetadataChangeEvent extends org.springframework.context.ApplicationEvent {
    
    // 实体API名称
    private final String entityApiName;
    
    // 变更前的元数据
    private final EntityMetadata oldMetadata;
    
    // 变更后的元数据
    private final EntityMetadata newMetadata;
    
    // 变更时间
    private final LocalDateTime changeTime;
    
    // 变更类型
    private final ChangeType changeType;
    
    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        // 新增
        CREATED,
        // 更新
        UPDATED,
        // 删除
        DELETED
    }
    
    /**
     * 构造函数
     */
    public MetadataChangeEvent(String entityApiName, EntityMetadata oldMetadata, EntityMetadata newMetadata) {
        super(entityApiName);
        this.entityApiName = entityApiName;
        this.oldMetadata = oldMetadata;
        this.newMetadata = newMetadata;
        this.changeTime = LocalDateTime.now();
        
        // 确定变更类型
        if (oldMetadata == null) {
            this.changeType = ChangeType.CREATED;
        } else if (newMetadata == null) {
            this.changeType = ChangeType.DELETED;
        } else {
            this.changeType = ChangeType.UPDATED;
        }
    }
    
    /**
     * 判断是否为首次创建
     */
    public boolean isCreated() {
        return changeType == ChangeType.CREATED;
    }
    
    /**
     * 判断是否为更新
     */
    public boolean isUpdated() {
        return changeType == ChangeType.UPDATED;
    }
    
    /**
     * 判断是否为删除
     */
    public boolean isDeleted() {
        return changeType == ChangeType.DELETED;
    }
    
    @Override
    public String toString() {
        return "MetadataChangeEvent{" +
                "entityApiName='" + entityApiName + '\'' +
                ", changeType=" + changeType +
                ", changeTime=" + changeTime +
                ", oldMetadataPresent=" + (oldMetadata != null) +
                ", newMetadataPresent=" + (newMetadata != null) +
                '}';
    }
}