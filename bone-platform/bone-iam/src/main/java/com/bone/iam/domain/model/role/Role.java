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

  /**
   * 更新角色描述（保留旧签名：只改描述的场景）。
   *
   * @deprecated 用 {@link #update(String, String)} —— 只改描述的单参重载会让调用方「忘了还能改名字」， 实测缺陷：{@code
   *     RoleApplicationService#update} 长期只调这个重载，导致 UI 上改角色名返回 200 但 名字纹丝不动（命令里 name 已带过来却没人应用）。
   */
  @Deprecated
  public void update(String description) {
    update(null, description);
  }

  /**
   * 更新角色名称与描述。
   *
   * <p>{@code null} 表示「本次不修改该字段」（PATCH 语义），与 {@code UpdateRoleReq} 允许部分字段缺省一致； 只有显式传入非空值才覆盖。
   */
  public void update(String name, String description) {
    if (name != null) {
      this.name = name;
    }
    if (description != null) {
      this.description = description;
    }
    this.updatedAt = LocalDateTime.now();
  }
}
