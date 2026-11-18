package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.LegalEntity;
import java.util.Optional;

/**
 * 法人身份的入站/出站端口。
 *
 * <p><b>为什么 LegalEntity 必须真的被持久化</b>（2026-11-16 独立审查 I-06/I-07 修复）：spec:27 要求「以统一社会信用代码/税号
 * 作为身份主键字段」。若 LegalEntity 只在领域里存在、从不落库，那么「同一统一社会信用代码被两个 partyId 登记」这件事 在任何一层都发现不了 ——
 * 同一个法人会并行存在两条供应链，正是主数据最需要防的问题。
 *
 * <p>本切片只提供内存实现（生产 adapter 与 {@code t_proc_*} DDL 属 R-2）。查询不到返回 empty；跨租户不可见。
 */
public interface LegalEntityRepositoryPort {

  /** 按统一社会信用代码查该租户下的法人；无则 empty。 */
  Optional<LegalEntity> findByUnifiedSocialCreditCode(
      long tenantId, String unifiedSocialCreditCode);

  /** 按税号查该租户下的法人；无则 empty。 */
  Optional<LegalEntity> findByTaxId(long tenantId, String taxId);

  /** 按统一身份查法人；无则 empty。 */
  Optional<LegalEntity> findByPartyId(long tenantId, String partyId);

  /** 保存（新增或覆盖同统一身份的法人）。{@code expectedVersion} 与当前版本不符时须抛冲突，不得静默覆盖。 */
  void save(LegalEntity legalEntity, long expectedVersion);
}
