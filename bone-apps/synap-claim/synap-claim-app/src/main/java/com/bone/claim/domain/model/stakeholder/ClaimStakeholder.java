package com.bone.claim.domain.model.stakeholder;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 赔案干系人（报案人 / 被保人 / 申请人 / 领款人，§9.5 A1）。
 *
 * <p><b>敏感字段处理</b>：{@code person_id_hash} 为假名化 ID（PIPL 29 条擦除锚点）， {@code name_masked} / {@code
 * phone_masked} / {@code bank_ref} 为遮蔽或加密值，明文不落库。
 *
 * <p><b>可变表</b>：带 {@code @Version} 乐观锁（SDK 要求 version 列 NOT NULL DEFAULT 0）。审计列由 {@link
 * TenantAbstractEntity} 框架注入，但 {@code created_at}/{@code updated_at} 无默认值， 落库前必须显式赋值（否则 INSERT 报
 * ERROR 1054）。
 */
@Getter
@Table("t_claim_stakeholder")
public class ClaimStakeholder extends TenantAbstractEntity<Long> {

  /** 干系人角色。 */
  public static final String ROLE_REPORTER = "REPORTER";

  public static final String ROLE_INSURED = "INSURED";
  public static final String ROLE_APPLICANT = "APPLICANT";
  public static final String ROLE_PAYEE = "PAYEE";

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "person_id_hash", nullable = false)
  private String personIdHash;

  @Column(name = "name_masked", nullable = false)
  private String nameMasked;

  @Column(name = "relation")
  private String relation;

  @Column(name = "phone_masked")
  private String phoneMasked;

  @Column(name = "collect_type")
  private String collectType;

  @Column(name = "bank_ref")
  private String bankRef;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  ClaimStakeholder() {}

  public static ClaimStakeholder create(
      Long tenantId,
      Long claimId,
      String role,
      String personIdHash,
      String nameMasked,
      String relation,
      String phoneMasked,
      String collectType,
      String bankRef,
      Instant now) {
    ClaimStakeholder stakeholder = new ClaimStakeholder();
    stakeholder.setTenantId(tenantId);
    stakeholder.claimId = claimId;
    stakeholder.role = role == null ? ROLE_INSURED : role;
    stakeholder.personIdHash = personIdHash == null ? "" : personIdHash;
    stakeholder.nameMasked = nameMasked == null ? "" : nameMasked;
    stakeholder.relation = relation == null ? "" : relation;
    stakeholder.phoneMasked = phoneMasked == null ? "" : phoneMasked;
    stakeholder.collectType = collectType == null ? "" : collectType;
    stakeholder.bankRef = bankRef == null ? "" : bankRef;
    stakeholder.version = 0L;
    Date nowDate = Date.from(now);
    stakeholder.setCreatedAt(nowDate);
    stakeholder.setUpdatedAt(nowDate);
    return stakeholder;
  }
}
