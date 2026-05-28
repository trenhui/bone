package com.bone.iam.domain.audit;

import lombok.Builder;
import lombok.Value;

/** 租户审计设置值对象（对齐 {@code iam_audit_settings}）。 */
@Value
@Builder
public class AuditSettings {

    int retentionDays;
    boolean autoArchiveEnabled;
    int archiveAfterDays;
    String storageType;
    boolean wormEnabled;
}
