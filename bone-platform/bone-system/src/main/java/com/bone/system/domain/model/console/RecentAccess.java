package com.bone.system.domain.model.console;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 最近访问记录（控制台访问流水，详设 §7 预留 {@code cnsl_recent_access} 表）。
 *
 * <p>由 {@code RecentAccessRecorder} 拦截 {@code /api/v1/console/**} 成功访问后追加写入，供控制台
 * 「最近访问」展示。属只追加流水、不承接业务命令，故无软删 / 乐观锁 / 领域事件（不发 DomainEvent）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("cnsl_recent_access")
public class RecentAccess extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long userId;
  private String resourceType;
  private String resourceId;
  private String resourceName;
  private String accessUrl;
  private LocalDateTime accessedAt;

  public static RecentAccess of(
      Long tenantId,
      Long userId,
      String resourceType,
      String resourceId,
      String resourceName,
      String accessUrl) {
    RecentAccess access = new RecentAccess();
    access.setTenantId(tenantId == null ? 0L : tenantId);
    access.userId = userId;
    access.resourceType = resourceType;
    access.resourceId = resourceId;
    access.resourceName = resourceName;
    access.accessUrl = accessUrl;
    access.accessedAt = LocalDateTime.now();
    return access;
  }
}
