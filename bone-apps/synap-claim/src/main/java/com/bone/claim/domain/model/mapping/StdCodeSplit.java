package com.bone.claim.domain.model.mapping;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 标准码拆分（{@code t_claim_std_code_split}）：一个码拆成多个码（医院分院独立、药品按剂型拆分）。
 *
 * <p><b>为什么不复用别名表</b>：别名是 1:1，可以自动重定向；拆分是 1:N，没有单一目标， 自动挑一个就是把"猜"写进金额链路。故拆到独立表：解析时命中父码即 fail-closed
 * 转人工。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_std_code_split")
public class StdCodeSplit extends TenantAggregateRoot<Long> {

  @Column(name = "domain")
  private String domain;

  @Column(name = "parent_code")
  private String parentCode;

  @Column(name = "child_code")
  private String childCode;

  @Column(name = "split_rule_json")
  private String splitRuleJson;

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "status")
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static StdCodeSplit of(
      Long id,
      Long tenantId,
      MasterDataDomain domain,
      String parentCode,
      String childCode,
      String splitRuleJson,
      Instant now) {
    if (domain == null) {
      throw new DomainException("主数据域不能为空");
    }
    if (parentCode == null || parentCode.isBlank()) {
      throw new DomainException("被拆分码不能为空");
    }
    if (childCode == null || childCode.isBlank()) {
      throw new DomainException("拆分后码不能为空");
    }
    StdCodeSplit e = new StdCodeSplit();
    e.setId(id);
    e.setTenantId(tenantId);
    e.domain = domain.code();
    e.parentCode = parentCode.trim();
    e.childCode = childCode.trim();
    e.splitRuleJson = splitRuleJson;
    e.effectiveFrom = now;
    e.status = "ACTIVE";
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  /** 停用拆分（保留行；解析层不再据此父码转人工拆分）。 */
  public void disable(Instant now) {
    this.status = "DISABLED";
    this.updatedAt = now;
  }
}
