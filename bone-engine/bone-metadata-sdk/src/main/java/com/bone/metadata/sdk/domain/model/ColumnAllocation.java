package com.bone.metadata.sdk.domain.model;

import com.bone.core.annotation.Id;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.enums.AllocationColumnStatus;
import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.enums.EnumType;
import com.bone.metadata.sdk.domain.annotation.Enumerated;
import lombok.*;
import java.time.LocalDateTime;

@Table("column_allocation")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnAllocation extends Entity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationStrategy.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "app_code", nullable = false)
    private String appCode;

    @Column(name = "biz_identity_code", nullable = false)
    private String bizIdentityCode;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private DataType dataType;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "column_index", nullable = false)
    private Integer columnIndex;

    @Column(name = "status", nullable = false)
    private AllocationColumnStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    public Long getTenantId() { return tenantId; }
    public String getAppCode() { return appCode; }
    public String getBizIdentityCode() { return bizIdentityCode; }
    public String getEntityType() { return entityType; }
    public DataType getDataType() { return dataType; }
    public String getColumnName() { return columnName; }
    public Integer getColumnIndex() { return columnIndex; }
    public AllocationColumnStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public Long getCreatedBy() { return createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ColumnAllocation allocation = new ColumnAllocation();
        
        public Builder tenantId(Long tenantId) {
            allocation.tenantId = tenantId;
            return this;
        }
        
        public Builder appCode(String appCode) {
            allocation.appCode = appCode;
            return this;
        }
        
        public Builder bizIdentityCode(String bizIdentityCode) {
            allocation.bizIdentityCode = bizIdentityCode;
            return this;
        }
        
        public Builder entityType(String entityType) {
            allocation.entityType = entityType;
            return this;
        }
        
        public Builder dataType(DataType dataType) {
            allocation.dataType = dataType;
            return this;
        }
        
        public Builder columnName(String columnName) {
            allocation.columnName = columnName;
            return this;
        }
        
        public Builder columnIndex(Integer columnIndex) {
            allocation.columnIndex = columnIndex;
            return this;
        }
        
        public Builder status(AllocationColumnStatus status) {
            allocation.status = status;
            return this;
        }
        
        public Builder version(Integer version) {
            allocation.version = version;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            allocation.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(LocalDateTime updatedAt) {
            allocation.updatedAt = updatedAt;
            return this;
        }
        
        public Builder createdBy(Long createdBy) {
            allocation.createdBy = createdBy;
            return this;
        }
        
        public Builder updatedBy(Long updatedBy) {
            allocation.updatedBy = updatedBy;
            return this;
        }
        
        public ColumnAllocation build() {
            return allocation;
        }
    }

    public void markAsAllocated() {
        this.status = AllocationColumnStatus.IN_USE;
        this.updatedAt = LocalDateTime.now();
        this.version = this.version + 1;
    }
}