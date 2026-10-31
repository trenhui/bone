package com.bone.iam.domain.model.account;

import com.bone.core.annotation.Deleted;
import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.exception.DomainException;
import com.bone.iam.domain.model.account.valueobject.AccountStatus;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_account")
/** 多租户账户聚合根样板：{@link TenantAggregateRoot} + IAM 域内 {@link LocalDateTime} 审计字段。 */
public class Account extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  // 逻辑删除标记：聚合根基类（TenantAggregateRoot / AggregateRoot）不自带 deleted 字段；
  // 不声明则 Repository#deleteById 发出 DELETE FROM，行永久消失且不可审计、不可恢复。
  // 补 @Deleted 后 SDK 走 UPDATE deleted=1 软删；配套 DDL（0020_soft_delete_unique_index.sql）
  // 已把本表唯一索引纳入 deleted 列，避免「软删后同值无法重建」。
  @Deleted private Boolean deleted = false;

  /**
   * 子类重新声明了 id 字段（@Getter 不会生成 setter），若缺省则 setId() 继承自父类仅设置 {@code Entity.id}，导致 create() 后
   * getId() 返回 null（字段遮蔽）。此处显式 override 到子类字段。
   */
  @Override
  public void setId(Long id) {
    this.id = id;
  }

  private Username username;
  private String passwordHash;
  private Email email;
  private String phone;
  private String realName;
  private String avatarUrl;

  /**
   * 归属部门（主部门）。跨聚合只存 ID，不做对象引用与级联——部门停用/删除不摆布账号聚合。
   *
   * <p>为何放账号上而不是反向由部门持有成员列表：部门树的读写频率与账号差一个量级，且账号还有「批量导入/跨部门筛选」等独立入口， 反向持有会让每次成员查询都必须先加载部门树（ADR-0030
   * 读侧边界）。「一人多部门」见 [Vision]，届时可保留本列作主部门。
   */
  private Long deptId;

  /**
   * 直属主管（审批流 {@code LINE_MANAGER} 指派策略的数据源）。跨聚合只存 ID，与 {@link #deptId} 同构。
   *
   * <p><b>为什么必须单独存而不是从部门树推</b>：部门树只表达「谁和谁同部门」，推不出「谁向谁汇报」——同一部门内
   * 平级同事互相不审批，而跨部门的虚线汇报（如项目组）也真实存在。审批流语义要的是汇报线，故独立成列。
   *
   * <p><b>与 {@code dept_id} 的关键差异：本字段真可空</b>（创始人/CEO 无上级）。因此 {@code changeManager(null)}
   * 是合法的「清空」，不像 {@link #changeDept} 那样拒绝 null——照抄部门的三分支语义会得到 「主管一旦设置就永远清不掉」。
   */
  private Long managerId;

  private AccountStatus status;
  private boolean isAdmin;
  private LocalDateTime lastLoginAt;
  private String lastLoginIp;
  private int loginFailCount;
  private LocalDateTime lockedAt;
  private LocalDateTime passwordUpdatedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Account create(
      Long id,
      Username username,
      String passwordHash,
      Email email,
      String phone,
      String realName,
      Long tenantId) {
    Account account = new Account();
    account.setId(id);
    account.setTenantId(tenantId);
    account.username = username;
    account.passwordHash = passwordHash;
    account.email = email;
    account.phone = phone;
    account.realName = realName;
    account.status = AccountStatus.ENABLED;
    account.isAdmin = false;
    account.loginFailCount = 0;
    account.createdAt = LocalDateTime.now();
    account.updatedAt = LocalDateTime.now();
    account.passwordUpdatedAt = LocalDateTime.now();
    return account;
  }

  public void enable() {
    if (this.status == AccountStatus.ENABLED) {
      throw new IllegalStateException("账户已处于启用状态");
    }
    this.status = AccountStatus.ENABLED;
    this.loginFailCount = 0;
    this.lockedAt = null;
    this.updatedAt = LocalDateTime.now();
  }

  public void disable() {
    if (this.status == AccountStatus.DISABLED) {
      throw new IllegalStateException("账户已处于禁用状态");
    }
    this.status = AccountStatus.DISABLED;
    this.updatedAt = LocalDateTime.now();
  }

  public void recordLoginSuccess(String ip) {
    this.lastLoginAt = LocalDateTime.now();
    this.lastLoginIp = ip;
    this.loginFailCount = 0;
    this.updatedAt = LocalDateTime.now();
  }

  /** 默认阈值：5 次失败 → 锁定 30 分钟（沿用历史调用方）。 */
  public void recordLoginFailure() {
    recordLoginFailure(5, 30);
  }

  /**
   * 失败计数 +1；累计达到 {@code threshold} 时进入 LOCKED 状态，{@code lockedAt} 设为当前时间 + {@code lockMinutes}。
   *
   * @param threshold 锁定阈值（&lt;= 0 表示不锁定，仅计数）
   * @param lockMinutes 锁定持续分钟数
   */
  public void recordLoginFailure(int threshold, int lockMinutes) {
    this.loginFailCount++;
    if (threshold > 0 && this.loginFailCount >= threshold) {
      this.status = AccountStatus.LOCKED;
      this.lockedAt = LocalDateTime.now().plusMinutes(Math.max(1, lockMinutes));
    }
    this.updatedAt = LocalDateTime.now();
  }

  public void updatePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
    this.passwordUpdatedAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
  }

  /** 调整归属部门。归属部门为必填项（所有账号必须归属一个主部门），故不接受 null； 幂等：值未变则不改更新时间，避免无意义的乐观锁版本号推进。 */
  public void changeDept(Long newDeptId) {
    if (newDeptId == null) {
      throw new DomainException("归属部门为必填项，不能清空");
    }
    if (java.util.Objects.equals(this.deptId, newDeptId)) {
      return;
    }
    this.deptId = newDeptId;
    this.updatedAt = LocalDateTime.now();
  }

  /**
   * 调整直属主管。
   *
   * <p>与 {@link #changeDept} 的两点差异，都是刻意的：
   *
   * <ul>
   *   <li><b>允许 null</b>——无上级是合法状态（创始人/CEO），不能像部门那样「拒绝清空」。
   *   <li><b>拒绝自环</b>——「自己是自己的主管」会让 {@code LINE_MANAGER} 解析出申请人本人，进而被引擎的
   *       「不能审批自己」规则拒绝（单据直接挂死），所以在聚合内先挡掉。跨租户/账号存在性校验依赖仓储，放应用服务。
   * </ul>
   *
   * <p>幂等：值未变则不动 {@code updatedAt}，避免全列 UPDATE 场景下乐观锁版本号无意义推进。
   */
  public void changeManager(Long newManagerId) {
    if (newManagerId != null && newManagerId.equals(this.getId())) {
      throw new DomainException("直属主管不能是本人");
    }
    if (java.util.Objects.equals(this.managerId, newManagerId)) {
      return;
    }
    this.managerId = newManagerId;
    this.updatedAt = LocalDateTime.now();
  }

  public void updateProfile(String realName, String phone, String avatarUrl) {
    this.realName = realName;
    this.phone = phone;
    if (avatarUrl != null) {
      this.avatarUrl = avatarUrl;
    }
    this.updatedAt = LocalDateTime.now();
  }

  public boolean isLocked() {
    if (this.status != AccountStatus.LOCKED) {
      return false;
    }
    if (this.lockedAt != null && this.lockedAt.isBefore(LocalDateTime.now())) {
      this.status = AccountStatus.ENABLED;
      this.loginFailCount = 0;
      this.lockedAt = null;
      return false;
    }
    return true;
  }
}
