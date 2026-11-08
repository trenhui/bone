package com.bone.claim.domain.model.mapping;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 保司码 → 标准码映射（{@code t_claim_code_mapping}）。
 *
 * <p><b>为什么当前有效性由 {@code activeFlag} 表达，而不是只看 {@code status}</b>：MySQL 唯一索引对 NULL 不判重，因此让 ACTIVE
 * 行固定 {@code 'Y'}、其余行留 NULL，可以把"同一 (保司, 域, 保司码, 消歧键) 任一时刻 仅一条 ACTIVE"变成<b>数据库强制</b>的事实。只靠 status 字段
 * + 代码自觉，并发双写必然出现两条 ACTIVE， 解析时就成了随机挑一条——赔错钱且查不出来。
 *
 * <p><b>{@code standardCode} 没有 setter</b>：映射错了要能追溯"当时指向哪个码"，直接改写会让历史案件 的解析结果凭空变化。换目标码只能"关闭旧行 +
 * 插入新行"，旧行由 {@link #supersede} 留痕。
 *
 * <p><b>{@code effectiveTo} 用 9999-12-31 而不是 NULL</b>：闭合区间让"某时刻生效的是哪条"这类范围查询 不必处理 NULL 的三值逻辑（{@code
 * NULL BETWEEN} 恒为 unknown，条件写漏一行就静默丢行）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_code_mapping")
public class CodeMapping extends TenantAggregateRoot<Long> {

  @Column(name = "insurer_code")
  private String insurerCode;

  @Column(name = "domain")
  private String domain;

  @Column(name = "insurer_item_code")
  private String insurerItemCode;

  /** 无消歧属性时为空串；有则必须是"能由收单字段确定性算出"的规范化键。 */
  @Column(name = "disambiguation_key")
  private String disambiguationKey = "";

  @Column(name = "standard_code")
  private String standardCode;

  @Column(name = "mapping_type")
  private String mappingType = MappingType.EXACT.code();

  @Column(name = "match_rule")
  private String matchRule = MatchRule.MANUAL.code();

  @Column(name = "match_score")
  private BigDecimal matchScore;

  @Column(name = "disambiguation_json")
  private String disambiguationJson;

  @Column(name = "source")
  private String source = MappingSource.MANUAL.code();

  @Column(name = "reason_code")
  private String reasonCode = "";

  @Column(name = "status")
  private String status = MappingStatus.ACTIVE.code();

  /** ACTIVE='Y'，其余 NULL——唯一键的一部分，不靠业务代码维护。 */
  @Column(name = "active_flag")
  private String activeFlag = "Y";

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Column(name = "superseded_by_id")
  private Long supersededById;

  @Column(name = "version_no")
  private String versionNo = "";

  @Column(name = "reviewed_by")
  private Long reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  /**
   * 生效止的闭合哨兵：用固定值而非 NULL，范围查询不踩三值逻辑。
   *
   * <p><b>必须是 UTC {@code 9999-12-31T15:59:59Z}（北京时间 9999-12-31 23:59:59）</b>： MySQL DATETIME 上限是
   * {@code 9999-12-31 23:59:59}（<b>会话本地时区</b>字面量）。若用 {@code 23:59:59Z}（UTC），东八区会话写入时换算成 {@code
   * 10000-01-01 07:59:59} 直接越界—— 发布任何映射都会 Data truncation（E2E 实测）。业务语义"开放到期"按北京日历日表达。
   */
  public static final Instant OPEN_ENDED = Instant.parse("9999-12-31T15:59:59Z");

  public static CodeMapping of(
      Long id,
      Long tenantId,
      String insurerCode,
      MasterDataDomain domain,
      String insurerItemCode,
      String disambiguationKey,
      String standardCode,
      MappingType mappingType,
      MatchRule matchRule,
      BigDecimal matchScore,
      String disambiguationJson,
      MappingSource source,
      String versionNo,
      Instant effectiveFrom,
      Instant now) {
    if (isBlank(insurerCode)) {
      throw new DomainException("保司编码不能为空");
    }
    if (domain == null) {
      throw new DomainException("主数据域不能为空");
    }
    if (isBlank(insurerItemCode)) {
      throw new DomainException("保司侧码值不能为空");
    }
    if (isBlank(standardCode)) {
      // 未命中就是未命中：不允许用空串充当标准码（下游会把它当合法值写进明细）
      throw new DomainException("标准码不能为空：未映射请走未命中队列，不要落空串");
    }
    CodeMapping e = new CodeMapping();
    e.setId(id);
    e.setTenantId(tenantId);
    e.insurerCode = insurerCode.trim();
    e.domain = domain.code();
    e.insurerItemCode = insurerItemCode.trim();
    e.disambiguationKey = disambiguationKey == null ? "" : disambiguationKey.trim();
    e.standardCode = standardCode.trim();
    e.mappingType = mappingType == null ? MappingType.EXACT.code() : mappingType.code();
    e.matchRule = matchRule == null ? MatchRule.MANUAL.code() : matchRule.code();
    e.matchScore = matchScore;
    e.disambiguationJson = disambiguationJson;
    e.source = source == null ? MappingSource.MANUAL.code() : source.code();
    e.status = MappingStatus.ACTIVE.code();
    e.activeFlag = "Y";
    e.effectiveFrom = effectiveFrom == null ? now : effectiveFrom;
    e.effectiveTo = OPEN_ENDED;
    e.versionNo = versionNo == null ? "" : versionNo.trim();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  /**
   * 关闭：停用但保留（历史单据要能解释"当时为什么映射到那个码"）。
   *
   * <p>{@code activeFlag} 置 NULL 而不是删除行——唯一键只约束 ACTIVE，历史行不占槽位， 于是"停用 → 重建 → 再停用"可以无限次发生。
   */
  public void close(Instant now, String reasonCode) {
    if (!isActive()) {
      throw new DomainException("映射已关闭，不能重复关闭: " + getId());
    }
    this.status = MappingStatus.DISABLED.code();
    this.activeFlag = null;
    this.effectiveTo = now;
    this.reasonCode = reasonCode == null ? "" : reasonCode.trim();
    this.updatedAt = now;
  }

  /** 被新版本替代：与 {@link #close} 的区别是留下"被谁替代"的回放链条。 */
  public void supersede(Long newMappingId, Instant now) {
    if (!isActive()) {
      throw new DomainException("映射已关闭，不能被替代: " + getId());
    }
    if (newMappingId == null) {
      throw new DomainException("替代映射ID不能为空");
    }
    this.status = MappingStatus.RETIRED.code();
    this.activeFlag = null;
    this.effectiveTo = now;
    this.supersededById = newMappingId;
    this.updatedAt = now;
  }

  public void markReviewed(Long reviewerId, Instant now) {
    this.reviewedBy = reviewerId;
    this.reviewedAt = now;
  }

  public boolean isActive() {
    return "Y".equals(activeFlag);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
