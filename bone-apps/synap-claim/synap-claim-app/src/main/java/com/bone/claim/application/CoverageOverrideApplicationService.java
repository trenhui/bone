package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 两段式覆盖：标准配置（t_adj_liability / t_adj_liability_fee_rule）→ 保司覆盖 → 预览 → 发布。
 *
 * <p><b>合并永远是实时的</b>：{@code standard_value} 列只是创建/发布时刻的留痕快照，
 * 预览与生效裁决都直接读实时标准。这样「同步标准」不需要任何写操作——标准侧新增项 天然出现在预览里，而显式覆盖项不受影响（spec：同步为只读合并语义，不产生新的覆盖记录）。
 *
 * <p><b>与启停状态（PAUSE）兼容</b>：本服务只翻覆盖行自身的状态，从不触碰 {@code t_claim_policy.config_status}——PAUSE
 * 的保单不收案是收单闸的事；恢复 ACTIVE 后理算走 {@link EffectiveConfigViewService} 的合并结果，无需任何补录。
 *
 * <p><b>E-5.4 豁免（{@link com.bone.core.annotation.NoDomainEvent}）</b>：本类是配置资产状态迁移，不发领域事件——
 * 生效值由理算/预览侧实时合并读取（EffectiveConfigViewService），没有需要订阅实时通知的下游； 「停用即回落」同样只是回到标准的读语义，与
 * QuotaConfigApplicationService 的「运营配置无订阅方」豁免同口径。
 */
@Service
@NoDomainEvent
public class CoverageOverrideApplicationService {

  private static final ObjectMapper JSON = new ObjectMapper();

  public record CreateOverrideCommand(
      String bizIdentityCode, String coverageUuid, String overrideField, String overrideValue) {}

  /**
   * 合并预览行：标准值（实时）/ 覆盖值 / 生效值 / 来源。
   *
   * <p>{@code overrideId}/{@code overrideStatus} 是覆盖行自身的标识与状态（无覆盖时为 null）—— 前端按行「停用」需要
   * id，没有它就只能整个重发。
   */
  public record MergePreviewRow(
      String overrideField,
      String standardValue,
      String overrideValue,
      String effectiveValue,
      String source,
      Long overrideId,
      String overrideStatus) { // STANDARD / OVERRIDE
  }

  public record AffectedPoliciesView(String bizIdentityCode, long affectedCount) {}

  private final CoverageOverrideRepository overrideRepository;
  private final LiabilityRepository liabilityRepository;
  private final LiabilityFeeRuleRepository feeRuleRepository;
  private final ClaimPolicyRepository claimPolicyRepository;
  private final ClaimInsurerRepository claimInsurerRepository;
  private final TenantPort tenantPort;

  public CoverageOverrideApplicationService(
      CoverageOverrideRepository overrideRepository,
      LiabilityRepository liabilityRepository,
      LiabilityFeeRuleRepository feeRuleRepository,
      ClaimPolicyRepository claimPolicyRepository,
      ClaimInsurerRepository claimInsurerRepository,
      TenantPort tenantPort) {
    this.overrideRepository = overrideRepository;
    this.liabilityRepository = liabilityRepository;
    this.feeRuleRepository = feeRuleRepository;
    this.claimPolicyRepository = claimPolicyRepository;
    this.claimInsurerRepository = claimInsurerRepository;
    this.tenantPort = tenantPort;
  }

  // ===================== 覆盖项管理 =====================

  /** 创建或更新覆盖项：同一维度（UK）已存在则更新覆盖值（仅 DISABLED 行可改），否则新建 DISABLED 行。 标准值快照在写入时刷新，保证留痕与创建时刻的标准一致。 */
  @Transactional
  public MergePreviewRow upsert(CreateOverrideCommand command) {
    long tenantId = tenantPort.currentTenantId();
    assertFieldAllowed(command.overrideField());
    assertInsurerUsable(tenantId, command.bizIdentityCode());
    String standard = liveStandardValue(tenantId, command.coverageUuid(), command.overrideField());
    Instant now = Instant.now();
    ClaimCoverageOverride row =
        overrideRepository.findRow(
            tenantId, command.bizIdentityCode(), command.coverageUuid(), command.overrideField());
    if (row == null) {
      row =
          ClaimCoverageOverride.of(
              null,
              tenantId,
              command.bizIdentityCode(),
              command.coverageUuid(),
              command.overrideField(),
              standard,
              command.overrideValue());
    } else {
      row.updateValue(command.overrideValue(), now);
      row.refreshStandardSnapshot(standard, now);
    }
    overrideRepository.save(row);
    return toPreviewRow(row, standard);
  }

  /** 单条停用：回落标准（回滚方式，spec）。 */
  @Transactional
  public void disable(long overrideId) {
    ClaimCoverageOverride row = requireRow(overrideId);
    row.disable(Instant.now());
    overrideRepository.save(row);
  }

  // ===================== 预览 / 发布 / 受影响保单 =====================

  /**
   * 合并预览（只读）：逐维度展示「标准值 / 覆盖值 / 最终生效值 / 来源」。
   *
   * <p>标准行取实时配置（未覆盖维度也在列）；覆盖行叠加其上——ACTIVE 生效、DISABLED 仅展示 不生效。预览不写任何状态，重复调用无副作用。
   */
  @Transactional(readOnly = true)
  public List<MergePreviewRow> preview(String bizIdentityCode, String coverageUuid) {
    long tenantId = tenantPort.currentTenantId();
    List<MergePreviewRow> rows = new ArrayList<>();
    rows.add(
        standardRow(
            tenantId,
            bizIdentityCode,
            coverageUuid,
            ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES));
    rows.add(
        standardRow(
            tenantId,
            bizIdentityCode,
            coverageUuid,
            ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES));
    for (LiabilityFeeRule rule : liveFeeRules(tenantId, coverageUuid)) {
      rows.add(
          standardRow(
              tenantId,
              bizIdentityCode,
              coverageUuid,
              ClaimCoverageOverride.FEE_RULE_PREFIX + rule.getFeeCode()));
    }
    // 覆盖行若指向标准侧已不存在的维度（费用规则被删）也列出——幽灵覆盖可见，不是静默丢弃
    for (ClaimCoverageOverride o : overrides(tenantId, bizIdentityCode, coverageUuid)) {
      if (rows.stream().noneMatch(r -> r.overrideField().equals(o.getOverrideField()))) {
        rows.add(ghostStandardRow(o));
      }
    }
    return rows;
  }

  /** 受影响保单数：发布确认页展示（规则开关 ACTIVE 且挂该主体的保单）。 */
  @Transactional(readOnly = true)
  public AffectedPoliciesView affectedPolicies(String bizIdentityCode) {
    long tenantId = tenantPort.currentTenantId();
    return new AffectedPoliciesView(
        bizIdentityCode, claimPolicyRepository.countActiveByBizIdentity(tenantId, bizIdentityCode));
  }

  /**
   * 发布：该主体该责任的全部 DISABLED 覆盖行 → ACTIVE，逐行刷新标准快照后生效。 前端先取 {@link #affectedPolicies} 展示确认；受影响保单为 0
   * 也允许发布（先备好覆盖，新保单即生效）。
   */
  @Transactional
  public int publish(String bizIdentityCode, String coverageUuid) {
    long tenantId = tenantPort.currentTenantId();
    int published = 0;
    for (ClaimCoverageOverride row :
        overrideRepository.findByBizAndCoverage(tenantId, bizIdentityCode, coverageUuid)) {
      if (row.isActive()) {
        continue;
      }
      row.refreshStandardSnapshot(
          liveStandardValue(tenantId, coverageUuid, row.getOverrideField()), Instant.now());
      row.publish(Instant.now());
      overrideRepository.save(row);
      published++;
    }
    return published;
  }

  // ===================== 内部 =====================

  private List<ClaimCoverageOverride> overrides(
      Long tenantId, String bizIdentityCode, String coverageUuid) {
    return overrideRepository.findByBizAndCoverage(tenantId, bizIdentityCode, coverageUuid);
  }

  private MergePreviewRow standardRow(
      Long tenantId, String bizIdentityCode, String coverageUuid, String field) {
    String standard = liveStandardValue(tenantId, coverageUuid, field);
    ClaimCoverageOverride o =
        overrideRepository.findRow(tenantId, bizIdentityCode, coverageUuid, field);
    if (o == null) {
      return new MergePreviewRow(field, standard, "", standard, "STANDARD", null, null);
    }
    return toPreviewRow(o, standard);
  }

  private MergePreviewRow toPreviewRow(ClaimCoverageOverride o, String liveStandard) {
    boolean active = o.isActive();
    return new MergePreviewRow(
        o.getOverrideField(),
        liveStandard,
        o.getOverrideValue(),
        active ? o.getOverrideValue() : liveStandard,
        active ? "OVERRIDE" : "STANDARD",
        o.getId(),
        o.getStatus());
  }

  private MergePreviewRow ghostStandardRow(ClaimCoverageOverride o) {
    boolean active = o.isActive();
    return new MergePreviewRow(
        o.getOverrideField(),
        o.getStandardValue(),
        o.getOverrideValue(),
        active ? o.getOverrideValue() : o.getStandardValue(),
        active ? "OVERRIDE" : "STANDARD",
        o.getId(),
        o.getStatus());
  }

  /** 实时标准值：名单维度取 ACTIVE 责任版本；费用规则维度取该责任当前规则行（无规则 = 空对象）。 */
  private String liveStandardValue(Long tenantId, String coverageUuid, String overrideField) {
    if (ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES.equals(overrideField)) {
      AdjLiability liability = liabilityRepository.findActiveByUuid(coverageUuid);
      return liability == null ? "" : liability.limitHospitalCodes();
    }
    if (ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES.equals(overrideField)) {
      AdjLiability liability = liabilityRepository.findActiveByUuid(coverageUuid);
      return liability == null ? "" : liability.limitDiagnosisCodes();
    }
    if (overrideField.startsWith(ClaimCoverageOverride.FEE_RULE_PREFIX)) {
      String feeCode = overrideField.substring(ClaimCoverageOverride.FEE_RULE_PREFIX.length());
      return liveFeeRules(tenantId, coverageUuid).stream()
          .filter(r -> r.getFeeCode().equals(feeCode))
          .findFirst()
          .map(r -> feeRuleJson(r.getPayPercent(), r.getLimitAmount(), r.getSocialOnly()))
          .orElse(feeRuleJson(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1));
    }
    throw ClaimErrors.of(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "未知覆盖维度: " + overrideField);
  }

  private List<LiabilityFeeRule> liveFeeRules(Long tenantId, String coverageUuid) {
    return feeRuleRepository.findByLiabilityUuid(tenantId, coverageUuid);
  }

  private String feeRuleJson(
      java.math.BigDecimal payPercent, java.math.BigDecimal limitAmount, Integer socialOnly) {
    try {
      return JSON.writeValueAsString(
          java.util.Map.of(
              "payPercent", payPercent == null ? java.math.BigDecimal.ZERO : payPercent,
              "limitAmount", limitAmount == null ? java.math.BigDecimal.ZERO : limitAmount,
              "socialOnly", socialOnly == null ? 1 : socialOnly));
    } catch (Exception e) {
      throw new IllegalStateException("费用规则序列化失败", e);
    }
  }

  /** 可覆盖维度白名单：名单两列 + 费用规则。开放任意字段名等于开放任意列覆盖，必须收口。 */
  private void assertFieldAllowed(String overrideField) {
    if (overrideField == null || overrideField.isBlank()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "覆盖维度不能为空");
    }
    String f = overrideField.trim();
    boolean allowed =
        ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES.equals(f)
            || ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES.equals(f)
            || f.startsWith(ClaimCoverageOverride.FEE_RULE_PREFIX);
    if (!allowed) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "不支持的覆盖维度: " + f);
    }
  }

  private void assertInsurerUsable(Long tenantId, String bizIdentityCode) {
    var insurer =
        claimInsurerRepository.findByInsurerCode(
            tenantId, bizIdentityCode == null ? "" : bizIdentityCode.trim());
    if (insurer == null || !insurer.isActive()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_BIZ_IDENTITY_INVALID, "覆盖项必须挂 ACTIVE 业务主体");
    }
  }

  private ClaimCoverageOverride requireRow(long id) {
    ClaimCoverageOverride row = overrideRepository.findById(id);
    if (row == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED, "覆盖项不存在: " + id);
    }
    return row;
  }
}
