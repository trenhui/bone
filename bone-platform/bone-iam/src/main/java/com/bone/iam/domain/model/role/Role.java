package com.bone.iam.domain.model.role;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_role")
public class Role extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  // 逻辑删除标记：聚合根基类（TenantAggregateRoot / AggregateRoot）不自带 deleted 字段；
  // 不声明则 Repository#deleteById 发出 DELETE FROM，行永久消失且不可审计、不可恢复。
  // 补 @Deleted 后 SDK 走 UPDATE deleted=1 软删；配套 DDL（0020_soft_delete_unique_index.sql）
  // 已把本表唯一索引纳入 deleted 列，避免「软删后同值无法重建」。
  @Deleted private Boolean deleted = false;

  private String name;
  private String code;
  private int type;
  private String description;
  private Long parentRoleId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Role create(
      String name, String code, String description, int type, Long tenantId, Long parentRoleId) {
    Role role = new Role();
    role.name = name;
    role.code = code;
    role.description = description;
    role.type = type;
    role.setTenantId(tenantId);
    role.parentRoleId = parentRoleId;
    role.createdAt = LocalDateTime.now();
    role.updatedAt = LocalDateTime.now();
    return role;
  }

  public void update(String description) {
    this.description = description;
    this.updatedAt = LocalDateTime.now();
  }
}
