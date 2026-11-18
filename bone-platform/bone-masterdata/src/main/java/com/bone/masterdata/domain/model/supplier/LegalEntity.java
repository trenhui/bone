package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;

/**
 * 法人身份载体：租户 + 统一 partyId + 身份主键（统一社会信用代码 / 税号）+ 版本。
 *
 * <p><b>终局分工</b>：黄金记录（身份 + General data）的唯一 system of entry 是平台侧 Golden Record 内核，本类只是采购域视角下的身份引用，
 * 不重复实现去重与合并。合并/血缘一律调用 {@code GoldenRecordMerger} 与 {@code EntityKeyMappingRegistry}。
 *
 * <p><b>为什么 identity 字段可为空但不可同时为空</b>：潜在供应商在极早期可能只从寻源线索登记，尚未取得税号；但一个两项身份主键全空的「法人」无法参与任何
 * 身份冲突裁决，等于把错并风险留给下游，因此直接拒绝构造（fail-closed）。
 *
 * <p><b>为什么带 tenantId 与 version</b>（2026-11-16 独立审查 I-06/I-07 修复）：本类此前只是「有构造器但从不落库」的死代码，
 * 于是「同一统一社会信用代码被两个 partyId 登记」在任何一层都发现不了。带上租户与版本后，它成为真正的身份唯一性登记表：
 * 跨租户不可见（租户进主键维度），覆盖写必须带期望版本（并发登记不得静默覆盖）。
 */
public final class LegalEntity {

  private final long tenantId;
  private final String partyId;
  private final String unifiedSocialCreditCode;
  private final String taxId;
  private final long version;

  private LegalEntity(
      long tenantId, String partyId, String unifiedSocialCreditCode, String taxId, long version) {
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.unifiedSocialCreditCode = unifiedSocialCreditCode;
    this.taxId = taxId;
    this.version = version;
  }

  /**
   * 首次登记一个法人身份（版本 1）。
   *
   * @param tenantId 登录态租户，必填且须大于 0
   * @param partyId 统一身份号，必填
   * @param unifiedSocialCreditCode 统一社会信用代码，可空
   * @param taxId 税号，可空；与 unifiedSocialCreditCode 至少一项非空
   */
  public static LegalEntity of(
      long tenantId, String partyId, String unifiedSocialCreditCode, String taxId) {
    return create(tenantId, partyId, unifiedSocialCreditCode, taxId, 1L);
  }

  /** 从持久化状态重建（版本 >= 1）。 */
  public static LegalEntity rehydrate(
      long tenantId, String partyId, String unifiedSocialCreditCode, String taxId, long version) {
    if (version < 1L) {
      throw new DomainException("法人身份版本必须 >= 1");
    }
    return create(tenantId, partyId, unifiedSocialCreditCode, taxId, version);
  }

  /** 身份主键是否与另一法人冲突（同一租户内比较；跨租户不参与裁决）。 */
  public boolean identityConflictsWith(LegalEntity other) {
    if (other == null || other.tenantId != tenantId) {
      return false;
    }
    return sameIdentity(unifiedSocialCreditCode, other.unifiedSocialCreditCode)
        || sameIdentity(taxId, other.taxId);
  }

  private static LegalEntity create(
      long tenantId, String partyId, String unifiedSocialCreditCode, String taxId, long version) {
    if (tenantId <= 0L) {
      throw new DomainException("法人身份缺少有效租户，禁止构造");
    }
    if (isBlank(partyId)) {
      throw new DomainException("法人身份缺少 partyId，禁止构造");
    }
    String uscc = normalize(unifiedSocialCreditCode);
    String normalizedTaxId = normalize(taxId);
    if (uscc == null && normalizedTaxId == null) {
      throw new DomainException("法人身份缺少统一社会信用代码与税号，两项身份主键不可同时为空");
    }
    return new LegalEntity(tenantId, partyId.trim(), uscc, normalizedTaxId, version);
  }

  private static boolean sameIdentity(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private static String normalize(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public long tenantId() {
    return tenantId;
  }

  public String partyId() {
    return partyId;
  }

  public String unifiedSocialCreditCode() {
    return unifiedSocialCreditCode;
  }

  public String taxId() {
    return taxId;
  }

  public long version() {
    return version;
  }
}
