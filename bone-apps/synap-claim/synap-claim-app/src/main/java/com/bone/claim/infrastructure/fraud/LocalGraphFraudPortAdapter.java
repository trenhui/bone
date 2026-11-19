package com.bone.claim.infrastructure.fraud;

import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 本地确定性图计算反欺诈适配器（出站端口实现，{@code *PortAdapter} 命名）。
 *
 * <p><b>能力边界（诚实声明）</b>：本实现不接 BONE-AI E2 图库（Nebula/TuGraph），而是用赔案仓储现成的跨案反查能力， 在 "案件 ↔ 发票 / 案件 ↔
 * 假名化证件"二部图上做确定性的共享锚点筛查（R-FRAUD-07 发票跨案、R-FRAUD-22 同证件短时高频）。 它比一期桩多出真实判别力，但覆盖的边类型少于 E2 图库——清单里以独立能力
 * ID {@code E2_GRAPH_LOCAL} 自报， 不得冒充 E2 平台图能力（监管 §15 分级管控）。
 *
 * <p><b>P1「AI 不碰钱」红线</b>：本适配器只产出 {@code hit}/{@code clusterId}/{@code available} 三个信号，
 * 不返回金额、不产拒赔建议、不改资金状态。 下游 {@code DecisionTriage} 对 {@code hit=true} 一律转人工（RISK_HIT，安全方向收紧）； 且
 * {@code hit=false} 也不会自动放行——仍需视觉/规则两路 {@code available} 且全过。图筛查是确定性代码，非模型推理。
 *
 * <p><b>双构造</b>：无参构造保留一期桩语义（{@code available=false}），供测试夹具与无仓储环境诚实降级； Spring 生产装配走带仓储构造， 执行真实筛查并自报
 * {@code available=true}。
 */
@Component
public class LocalGraphFraudPortAdapter implements GraphFraudPort {

  /** 同证件高频窗口：与本案报案时间向前看的天数。 */
  static final int HIGH_FREQ_WINDOW_DAYS = 90;

  /** 同证件高频阈值：窗口内除本案外达到该数量的关联案件即命中（含本案共 3 案）。 */
  static final int HIGH_FREQ_OTHER_CASE_THRESHOLD = 2;

  /** 独立能力 ID：与 E2 平台图库区分，清单诚实标注为本地实现。 */
  static final String CAPABILITY_ID = "E2_GRAPH_LOCAL";

  private final ClaimCaseRepository caseRepository;

  /** 无参构造：桩语义，供测试夹具与无仓储环境使用（available=false，绝不伪装无风险）。 */
  public LocalGraphFraudPortAdapter() {
    this(null);
  }

  /** Spring 生产装配：注入赔案仓储后执行本地图筛查。 */
  @Autowired
  public LocalGraphFraudPortAdapter(ClaimCaseRepository caseRepository) {
    this.caseRepository = caseRepository;
  }

  @Override
  public FraudResult screen(ClaimContextPort.ClaimContextView ctx) {
    // 无仓储 = 未接入真实数据通道：诚实降级为"未筛查"，交给决策图 fail-closed 转人工。
    if (caseRepository == null) {
      return new FraudResult(false, null, false);
    }
    ClaimCase self = caseRepository.findById(ctx.claimId());
    if (self == null) {
      // 取不到本案：无法构图，不可用绝不等同于无团伙。
      return new FraudResult(false, null, false);
    }

    // 边一：发票跨案共用（R-FRAUD-07，强信号）。DB 唯一约束是并发最后防线，这里做材料阶段提前拦截。
    if (self.getInvoiceCode() != null
        && !self.getInvoiceCode().isBlank()
        && self.getInvoiceNo() != null
        && !self.getInvoiceNo().isBlank()) {
      List<ClaimCase> invoicePeers =
          caseRepository.findByInvoice(
              self.getTenantId(), self.getInvoiceCode(), self.getInvoiceNo());
      long otherCount = countOtherCases(invoicePeers, self.getId());
      if (otherCount >= 1) {
        return new FraudResult(
            true, clusterId("INV", self.getInvoiceCode() + "|" + self.getInvoiceNo()), true);
      }
    }

    // 边二：同假名化证件短时高频（R-FRAUD-22，中信号）。必须排除本案自身，否则必然误报。
    if (self.getInsuredIdHash() != null && !self.getInsuredIdHash().isBlank()) {
      List<ClaimCase> insuredPeers =
          caseRepository.findByInsuredIdHash(self.getTenantId(), self.getInsuredIdHash());
      Instant selfTime = self.getCreatedAt();
      Instant windowStart =
          selfTime == null ? null : selfTime.minus(Duration.ofDays(HIGH_FREQ_WINDOW_DAYS));
      long otherCount =
          insuredPeers == null
              ? 0
              : insuredPeers.stream()
                  .filter(c -> c.getId() == null || !c.getId().equals(self.getId()))
                  .filter(c -> inWindow(c.getCreatedAt(), windowStart, selfTime))
                  .count();
      if (otherCount >= HIGH_FREQ_OTHER_CASE_THRESHOLD) {
        return new FraudResult(true, clusterId("INS", self.getInsuredIdHash()), true);
      }
    }

    // 两类锚点都查过且未命中 → 本次真实执行了筛查，available=true。
    return new FraudResult(false, null, true);
  }

  @Override
  public AiCapabilityDeclaration declaration() {
    if (caseRepository == null) {
      return GraphFraudPort.super.declaration();
    }
    return AiCapabilityDeclaration.ready(CAPABILITY_ID, "本地确定性图筛查（发票跨案/同证件高频），非 E2 平台图库；命中仅转人工不碰钱");
  }

  private static long countOtherCases(List<ClaimCase> peers, Long selfId) {
    if (peers == null) {
      return 0;
    }
    return peers.stream().filter(c -> c.getId() == null || !c.getId().equals(selfId)).count();
  }

  /** 时间窗包含端点：[windowStart, selfTime]；缺时间字段时不参与过滤（存量数据兼容）。 */
  private static boolean inWindow(Instant createdAt, Instant windowStart, Instant selfTime) {
    if (createdAt == null || windowStart == null || selfTime == null) {
      return true;
    }
    return !createdAt.isBefore(windowStart) && !createdAt.isAfter(selfTime);
  }

  /** 团伙标识：SHA-256 假名化，确定性可复现，明文锚点不出域。 */
  private static String clusterId(String prefix, String anchor) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(anchor.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(prefix).append("-");
      for (int i = 0; i < 8; i++) {
        sb.append(String.format("%02x", digest[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JDK 强制支持的算法，理论不可达；异常方向 fail-closed 为未知标识。
      return prefix + "-UNKNOWN";
    }
  }
}
