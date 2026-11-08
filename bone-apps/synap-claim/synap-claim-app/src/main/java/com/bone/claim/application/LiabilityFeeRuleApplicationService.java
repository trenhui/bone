package com.bone.claim.application;

import com.bone.claim.adapter.web.dto.request.LiabilityRequests;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.liability.LiabilityFeeRule;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 责任费用类别规则应用服务（0094）。
 *
 * <p>在责任的统一比例/限额之上按费用类别差异化（手术费 100%、材料费 60% 且限 5000…）。 保存走<b>全量替换</b>（软删旧行 + 插新行）： 前端抽屉整表提交，逐行
 * PATCH 会留下"改了一半"的中间态且难做类别唯一性；替换是原子的，UI 即所见即所得。
 */
@Service
@RequiredArgsConstructor
public class LiabilityFeeRuleApplicationService {

  private final LiabilityFeeRuleRepository feeRuleRepository;
  private final LiabilityRepository liabilityRepository;
  private final RefFeeCategoryRepository feeCategoryRepository;
  private final TenantPort tenantPort;

  /** 按责任 ID 列出费用规则（按 liabilityUuid 全量）。 */
  @Transactional(readOnly = true)
  public List<FeeRuleView> listByLiabilityId(long liabilityId) {
    long tenantId = tenantPort.currentTenantId();
    String liabilityUuid = requireLiabilityUuid(liabilityId);
    return feeRuleRepository.findByLiabilityUuid(tenantId, liabilityUuid).stream()
        .map(FeeRuleView::from)
        .toList();
  }

  /** 全量替换该责任的费用规则（软删旧行 + 插新行；同事务原子）。 */
  @Transactional
  public List<FeeRuleView> saveAll(long liabilityId, List<LiabilityRequests.FeeRuleItem> items) {
    long tenantId = tenantPort.currentTenantId();
    String liabilityUuid = requireLiabilityUuid(liabilityId);
    List<LiabilityRequests.FeeRuleItem> safeItems = items == null ? List.of() : items;

    // 校验：类别编码须存在于目录、不重复、比例 0-100、限额非负——配置错误拦在保存时。
    Set<String> seen = new HashSet<>();
    for (LiabilityRequests.FeeRuleItem item : safeItems) {
      String feeCode = item.feeCode() == null ? "" : item.feeCode().trim();
      if (feeCode.isEmpty()) {
        throw new DomainException("费用类别不能为空");
      }
      if (!seen.add(feeCode)) {
        throw new DomainException("费用类别重复: " + feeCode);
      }
      if (feeCategoryRepository.findByFeeCode(tenantId, feeCode) == null) {
        throw new DomainException("费用类别不存在于基础目录库: " + feeCode);
      }
      BigDecimal percent = parsePercent(item.payPercent());
      if (percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
        throw new DomainException("赔付比例必须在 0-100 之间: " + item.payPercent());
      }
      BigDecimal limit = parseAmount(item.limitAmount());
      if (limit.signum() < 0) {
        throw new DomainException("限额不能为负: " + item.limitAmount());
      }
    }

    feeRuleRepository.removeByLiabilityUuid(tenantId, liabilityUuid);
    List<LiabilityFeeRule> rows = new ArrayList<>();
    for (LiabilityRequests.FeeRuleItem item : safeItems) {
      String feeCode = item.feeCode().trim();
      // 类别名称冗余落库（免联表展示）；目录里必存在（上面已校验）
      var category = feeCategoryRepository.findByFeeCode(tenantId, feeCode);
      rows.add(
          LiabilityFeeRule.of(
              com.bone.core.util.DistributedIdGenerator.generateLongId(),
              tenantId,
              liabilityUuid,
              feeCode,
              category == null ? "" : category.getFeeName(),
              parsePercent(item.payPercent()),
              parseAmount(item.limitAmount()),
              item.socialOnly() == null ? 1 : item.socialOnly()));
    }
    if (!rows.isEmpty()) {
      feeRuleRepository.batchSave(rows);
    }
    return feeRuleRepository.findByLiabilityUuid(tenantId, liabilityUuid).stream()
        .map(FeeRuleView::from)
        .toList();
  }

  // ===================== 内部 =====================

  private String requireLiabilityUuid(long liabilityId) {
    var liability = liabilityRepository.findById(liabilityId);
    if (liability == null) {
      throw new DomainException("责任不存在: " + liabilityId);
    }
    return liability.getLiabilityUuid();
  }

  private static BigDecimal parsePercent(String value) {
    return parse(value, "赔付比例");
  }

  private static BigDecimal parseAmount(String value) {
    return parse(value, "限额");
  }

  private static BigDecimal parse(String value, String label) {
    if (value == null || value.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      throw new DomainException(label + "不是合法数值: \"" + value.trim() + "\"");
    }
  }

  /** 费用规则视图（金额转 plain string，与责任视图同一契约）。 */
  public record FeeRuleView(
      String id,
      String feeCode,
      String feeName,
      String payPercent,
      String limitAmount,
      Integer socialOnly) {

    static FeeRuleView from(LiabilityFeeRule r) {
      return new FeeRuleView(
          String.valueOf(r.getId()),
          r.getFeeCode(),
          r.getFeeName(),
          r.getPayPercent() == null ? "0" : r.getPayPercent().toPlainString(),
          r.getLimitAmount() == null ? "0" : r.getLimitAmount().toPlainString(),
          r.getSocialOnly());
    }
  }
}
