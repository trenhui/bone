package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.claim.domain.repository.CoverageOverrideRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生效配置读模型（理算装载前消费）：ACTIVE 覆盖 &gt; 实时标准。
 *
 * <p><b>为什么独立成类</b>：写侧（{@link CoverageOverrideApplicationService}）关心生命周期
 * 与预览，读侧只回答「此刻这个主体这个责任的值是什么」。理算装载将来把标准快照逻辑 替换为本服务调用时，只动一处，不拖出写侧依赖。
 *
 * <p><b>PAUSE 兼容</b>：保单配置状态（INACTIVE/ACTIVE/PAUSE）由收单闸裁决「能不能收案」， 本服务只裁决「值是什么」——PAUSE 保单恢复 ACTIVE
 * 后按本合并结果理算，无需补录。
 */
@Service
public class EffectiveConfigViewService {

  private final CoverageOverrideRepository overrideRepository;
  private final LiabilityRepository liabilityRepository;
  private final LiabilityFeeRuleRepository feeRuleRepository;
  private final TenantPort tenantPort;

  public EffectiveConfigViewService(
      CoverageOverrideRepository overrideRepository,
      LiabilityRepository liabilityRepository,
      LiabilityFeeRuleRepository feeRuleRepository,
      TenantPort tenantPort) {
    this.overrideRepository = overrideRepository;
    this.liabilityRepository = liabilityRepository;
    this.feeRuleRepository = feeRuleRepository;
    this.tenantPort = tenantPort;
  }

  public record FeeRuleView(
      String feeCode,
      String feeName,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal limitAmount,
      Integer socialOnly) {}

  public record EffectiveConfig(
      String limitHospitalCodes, String limitDiagnosisCodes, List<FeeRuleView> feeRules) {}

  /** ACTIVE 覆盖逐维替换实时标准；无覆盖主体输出与标准完全一致（继承语义）。 */
  @Transactional(readOnly = true)
  public EffectiveConfig effectiveConfig(String bizIdentityCode, String coverageUuid) {
    long tenantId = tenantPort.currentTenantId();
    List<ClaimCoverageOverride> actives =
        overrideRepository.findActiveByBizAndCoverage(tenantId, bizIdentityCode, coverageUuid);
    AdjLiability liability = liabilityRepository.findActiveByUuid(coverageUuid);
    String hospital = liability == null ? "" : liability.limitHospitalCodes();
    String diagnosis = liability == null ? "" : liability.limitDiagnosisCodes();
    List<FeeRuleView> feeRules =
        feeRuleRepository.findByLiabilityUuid(tenantId, coverageUuid).stream()
            .map(
                r ->
                    new FeeRuleView(
                        r.getFeeCode(),
                        r.getFeeName(),
                        r.getPayPercent(),
                        r.getLimitAmount(),
                        r.getSocialOnly()))
            .toList();
    for (ClaimCoverageOverride o : actives) {
      if (ClaimCoverageOverride.FIELD_LIMIT_HOSPITAL_CODES.equals(o.getOverrideField())) {
        hospital = o.getOverrideValue();
      } else if (ClaimCoverageOverride.FIELD_LIMIT_DIAGNOSIS_CODES.equals(o.getOverrideField())) {
        diagnosis = o.getOverrideValue();
      } else if (o.getOverrideField().startsWith(ClaimCoverageOverride.FEE_RULE_PREFIX)) {
        String feeCode =
            o.getOverrideField().substring(ClaimCoverageOverride.FEE_RULE_PREFIX.length());
        feeRules = replaceOrAppend(feeRules, feeCode, o.getOverrideValue());
      }
    }
    return new EffectiveConfig(hospital, diagnosis, List.copyOf(feeRules));
  }

  private List<FeeRuleView> replaceOrAppend(List<FeeRuleView> rules, String feeCode, String json) {
    List<FeeRuleView> result = new ArrayList<>();
    boolean replaced = false;
    for (FeeRuleView r : rules) {
      if (r.feeCode().equals(feeCode)) {
        result.add(parseFeeRule(feeCode, r.feeName(), json));
        replaced = true;
      } else {
        result.add(r);
      }
    }
    if (!replaced) {
      // 标准侧无此费用规则而覆盖侧有：feeName 取覆盖 JSON 里的 name（若有），其余字段照读
      result.add(parseFeeRule(feeCode, "", json));
    }
    return result;
  }

  private FeeRuleView parseFeeRule(String feeCode, String feeName, String json) {
    try {
      var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
      return new FeeRuleView(
          feeCode,
          feeName,
          node.path("payPercent").decimalValue(),
          node.path("limitAmount").decimalValue(),
          node.path("socialOnly").asInt(1));
    } catch (Exception e) {
      throw new IllegalStateException("费用规则覆盖值不是合法 JSON: " + feeCode, e);
    }
  }
}
