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
 * 字段 → 选项集绑定（{@code t_claim_field_binding}）。
 *
 * <p><b>为什么标准基线用 {@code '*'} 而不是 NULL</b>：唯一索引对 NULL 不判重。用 NULL 表示"平台标准基线"，
 * 同一字段可以插进多条基线且<b>条条生效</b>，取值变成随机的（已在 MySQL 8 实测：两条 {@code insurer_code=NULL}
 * 的基线都能插入）。哨兵值让"一个字段只有一条基线"成为数据库事实。
 *
 * <p>值与险种/责任无关，所以只按 {@code (字段, 保司)} 定唯一性，不引入保单维度——引入后同一字段在不同保单 会绑到不同选项集，运营再也说不清"这个下拉为什么和别处不一样"。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_field_binding")
public class FieldBinding extends TenantAggregateRoot<Long> {

  /** 平台标准基线哨兵：非空，才能被唯一索引判重。 */
  public static final String BASELINE = "*";

  @Column(name = "field_code")
  private String fieldCode;

  @Column(name = "insurer_code")
  private String insurerCode = BASELINE;

  @Column(name = "source_type")
  private String sourceType;

  @Column(name = "ref_code")
  private String refCode;

  @Column(name = "version_no")
  private String versionNo = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static FieldBinding of(
      Long id,
      Long tenantId,
      String fieldCode,
      String insurerCode,
      String sourceType,
      String refCode,
      Instant now) {
    if (fieldCode == null || fieldCode.isBlank()) {
      throw new DomainException("字段编码不能为空");
    }
    if (refCode == null || refCode.isBlank()) {
      throw new DomainException("引用编码不能为空");
    }
    FieldBinding e = new FieldBinding();
    e.setId(id);
    e.setTenantId(tenantId);
    e.fieldCode = fieldCode.trim();
    e.insurerCode = insurerCode == null || insurerCode.isBlank() ? BASELINE : insurerCode.trim();
    e.sourceType = sourceType == null ? "" : sourceType.trim();
    e.refCode = refCode.trim();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  public void rebind(String sourceType, String refCode, Instant now) {
    if (refCode == null || refCode.isBlank()) {
      throw new DomainException("引用编码不能为空");
    }
    this.sourceType = sourceType == null ? this.sourceType : sourceType.trim();
    this.refCode = refCode.trim();
    this.updatedAt = now;
  }

  public boolean isBaseline() {
    return BASELINE.equals(insurerCode);
  }
}
