package com.bone.engine.extension.studio.infrastructure.persistence.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.Entity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/** Metadata SDK：Studio 审计日志（append-only）。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("exts_audit_log")
public class ExtStudioAuditLog extends Entity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId = 0L;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "action")
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "result")
    private String result;

    @Column(name = "detail")
    private String detail;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at")
    private Date createdAt;
}
