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
 * 标准码别名（{@code t_claim_std_code_alias}）：标准侧自己也会变（归并、更名）。
 *
 * <p><b>为什么标准码生命周期必须建模</b>：上一版方案只处理"保司码 → 标准码"，默认标准码永不改变。
 * 但医院合并、药品更名是常态；一旦标准码变了而映射仍指向旧码，解析就落在一个"库里已经不存在的码"上—— 比未命中更糟，因为它不会进队列，只会让理算拿到一个查不到的编码。
 *
 * <p><b>只承载 1:1（MERGE/RENAME）</b>：拆分是 1:N，没有单一目标，自动重定向必定猜错，因此拆到 {@link StdCodeSplit} 单独承载并强制转人工。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_std_code_alias")
public class StdCodeAlias extends TenantAggregateRoot<Long> {

  @Column(name = "domain")
  private String domain;

  @Column(name = "alias_code")
  private String aliasCode;

  @Column(name = "canonical_code")
  private String canonicalCode;

  @Column(name = "relation")
  private String relation;

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Column(name = "status")
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static StdCodeAlias of(
      Long id,
      Long tenantId,
      MasterDataDomain domain,
      String aliasCode,
      String canonicalCode,
      AliasRelation relation,
      Instant now) {
    if (domain == null) {
      throw new DomainException("主数据域不能为空");
    }
    if (aliasCode == null || aliasCode.isBlank()) {
      throw new DomainException("旧码不能为空");
    }
    if (canonicalCode == null || canonicalCode.isBlank()) {
      throw new DomainException("现行码不能为空（1:N 拆分请用 t_claim_std_code_split）");
    }
    if (aliasCode.trim().equals(canonicalCode.trim())) {
      throw new DomainException("旧码与现行码相同，不构成别名: " + aliasCode);
    }
    if (relation == null) {
      throw new DomainException("别名关系不能为空");
    }
    StdCodeAlias e = new StdCodeAlias();
    e.setId(id);
    e.setTenantId(tenantId);
    e.domain = domain.code();
    e.aliasCode = aliasCode.trim();
    e.canonicalCode = canonicalCode.trim();
    e.relation = relation.code();
    e.effectiveFrom = now;
    e.effectiveTo = CodeMapping.OPEN_ENDED;
    e.status = "ACTIVE";
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void disable(Instant now) {
    this.status = "DISABLED";
    this.effectiveTo = now;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }
}
