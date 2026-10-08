package com.bone.iam.domain.model.tenant;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_tenant")
public class Tenant extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  // 逻辑删除标记：聚合根基类（TenantAggregateRoot / AggregateRoot）不自带 deleted 字段；
  // 不声明则 Repository#deleteById 发出 DELETE FROM，行永久消失且不可审计、不可恢复。
  // 补 @Deleted 后 SDK 走 UPDATE deleted=1 软删；配套 DDL（0020_soft_delete_unique_index.sql）
  // 已把本表唯一索引纳入 deleted 列，避免「软删后同值无法重建」。
  @Deleted private Boolean deleted = false;

  /**
   * 子类重新声明 id 字段，须显式 override setId 到子类字段，避免字段遮蔽 （父类 setId 只设置 Entity.id，导致 create() 后 getId() 返回
   * null）。
   */
  @Override
  public void setId(Long id) {
    this.id = id;
  }

  private String name;
  private String code;
  private int level;
  private int status;
  private String adminEmail;

  /** 账号配额，{@code null} 表示不限制（社区版 As-Is 列）。 */
  private Integer maxAccounts;

  /** 角色配额，{@code null} 表示不限制。 */
  private Integer maxRoles;

  /**
   * 已分配账号数。与 {@link #maxAccounts} 配合做配额核算：count 后写的序列在跨实例部署下会超卖， 故改为在租户聚合上维护占用计数，写操作由
   * {@code @Version} 乐观锁保证跨实例串行。
   */
  private Integer allocatedAccounts;

  /** 已分配角色数，语义同 {@link #allocatedAccounts}。 */
  private Integer allocatedRoles;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /**
   * SDK 原生 {@code @Version} 乐观锁（ADR-0031 D2）：写路径由 {@code bone-metadata-sdk} 统一维护—— 更新时 {@code SET
   * version = version + 1}、{@code WHERE version = :old}，并发写 0 行由 SDK 抛 {@code
   * OptimisticLockingFailureException}。{@code iam_tenant.version} 列已存在（DEFAULT 0），无需 DDL。 与 {@link
   * TenantQuotaEnforcer} 的 JVM 分段锁互补：单实例走锁快路径，跨实例靠本乐观锁兜底。
   */
  @Version private Long version;

  public static Tenant create(Long id, String name, String code, int level, String adminEmail) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.name = name;
    tenant.code = code;
    tenant.level = level;
    tenant.status = 1;
    tenant.adminEmail = adminEmail;
    tenant.createdAt = LocalDateTime.now();
    tenant.updatedAt = LocalDateTime.now();
    tenant.version = 0L;
    tenant.allocatedAccounts = 0;
    tenant.allocatedRoles = 0;
    return tenant;
  }

  public void update(String name, String adminEmail, int level) {
    if (name != null) {
      this.name = name;
    }
    if (adminEmail != null) {
      this.adminEmail = adminEmail;
    }
    this.level = level;
    this.updatedAt = LocalDateTime.now();
  }

  public void enable() {
    this.status = 1;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    this.status = 0;
    this.updatedAt = LocalDateTime.now();
  }

  public void updateQuota(Integer maxAccounts, Integer maxRoles) {
    this.maxAccounts = maxAccounts;
    this.maxRoles = maxRoles;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 尝试占用一个账号配额。
   *
   * @return {@code true} 占用成功；{@code false} 表示已达上限，由应用层翻译成 {@code TENANT_QUOTA_EXCEEDED}
   */
  public boolean tryAllocateAccount() {
    if (maxAccounts == null) {
      return true; // 不限制
    }
    int used = allocatedAccounts == null ? 0 : allocatedAccounts;
    if (used >= maxAccounts) {
      return false;
    }
    this.allocatedAccounts = used + 1;
    this.updatedAt = LocalDateTime.now();
    return true;
  }

  /** 释放一个账号配额（账号删除时调用）；下限 clamp 到 0，避免历史脏数据导致负值。 */
  public void releaseAccount() {
    int used = allocatedAccounts == null ? 0 : allocatedAccounts;
    this.allocatedAccounts = Math.max(0, used - 1);
    this.updatedAt = LocalDateTime.now();
  }

  /** 尝试占用一个角色配额，语义同 {@link #tryAllocateAccount()}。 */
  public boolean tryAllocateRole() {
    if (maxRoles == null) {
      return true;
    }
    int used = allocatedRoles == null ? 0 : allocatedRoles;
    if (used >= maxRoles) {
      return false;
    }
    this.allocatedRoles = used + 1;
    this.updatedAt = LocalDateTime.now();
    return true;
  }

  /** 释放一个角色配额，语义同 {@link #releaseAccount()}。 */
  public void releaseRole() {
    int used = allocatedRoles == null ? 0 : allocatedRoles;
    this.allocatedRoles = Math.max(0, used - 1);
    this.updatedAt = LocalDateTime.now();
  }
}
