package com.bone.iam.domain.tenant;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
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

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

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
}
