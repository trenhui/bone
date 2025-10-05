package com.bone.metadata.sdk.domain.model;

import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Id;
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

    public void markAsAllocated() {
        this.status = AllocationColumnStatus.IN_USE;
        this.updatedAt = LocalDateTime.now();
        this.version = this.version + 1;
    }
}