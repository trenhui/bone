package com.bone.claim.infrastructure.policy;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.claim.domain.repository.ClaimPolicyRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 保单快照查询适配器（出站端口实现，必须 {@code *PortAdapter} 命名）。
 *
 * <p><b>一期形态变更（2026-10-31）：进程内存桩 → 真实保单表</b>。
 *
 * <p>此前本类是 {@code ConcurrentHashMap} 桩，且只有 {@code @Profile("dev")} 的 {@link
 * DevPolicySnapshotSeeder} 会往里灌两条种子。后果是<b>生产环境报案必然被拒</b>： {@code
 * ClaimCaseApplicationService#submit} 的前置校验走 {@code PolicyQueryPort#findActivePolicy}，非 dev
 * 环境查任何保单都返回空 ⇒ 恒抛 {@code SC_CLAIM_POLICY_NOT_ACTIVE}（422）。这不是"边缘功能不可用"，
 * 而是<b>理赔主链路在生产整体不可用</b>，且报错发生在业务校验层，看不出是"缺保单主数据"。
 *
 * <p>现在保单有主数据表（{@code t_claim_policy}），本类改为从它读取 ⇒ 保单管理页建的保单 立刻可用于报案，链路自洽。
 *
 * <p><b>防腐层价值仍在</b>：本类仍是 {@code PolicyQueryPort} 的唯一实现，未来切真实保司系统时 只动本类，领域与应用层零感知。为此本类<b>不直接把 {@link
 * ClaimPolicy} 实体抛出去</b>， 而是转成端口自己的 {@link PolicySnapshot} 记录——实体的字段集与业务口径并不相同 （实体有 {@code
 * configStatus}/{@code staleFlag} 等配置面字段，端口只有报案校验需要的最小集）。
 *
 * <p><b>两个查询方法的差别必须保留</b>（本仓已踩过的坑）：
 *
 * <ul>
 *   <li>{@link #findActivePolicy}：报案前置校验"这张保单今天还能不能收案"——必须<b>此刻生效中</b>， 已满期保单返回空；
 *   <li>{@link #findByPolicy}：额度年度裁决"这笔费用落在第几个保单年度"——<b>必须能回答已满期保单</b>，
 *       否则年末发票跨年理赔、已满期保单的补赔/申诉复议/退票红冲会被一律误判成"保单不存在"。
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PolicyQueryPortAdapter implements PolicyQueryPort {

  private final ClaimPolicyRepository claimPolicyRepository;

  /**
   * 报案前置校验用：<b>此刻生效中</b>（生效期内 + 规则启用 + 快照未过期）。
   *
   * <p>已满期保单返回空——这是"今天还能不能收案"的正确语义。
   */
  @Override
  public Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId) {
    ClaimPolicy policy = claimPolicyRepository.findUsablePolicy(policyNo);
    if (policy == null) {
      return Optional.empty();
    }
    return Optional.of(toSnapshot(policy, planId));
  }

  /**
   * 额度年度裁决用：<b>不按"此刻生效中"过滤</b>，只回答该保单的期间。
   *
   * <p>与 {@link #findActivePolicy} 的唯一差别就是<b>不做时间与开关过滤</b>：
   * 已满期保单的补赔/红冲仍要回到原保单年度记账，用生效中版本问会把这些案件判成"保单不存在"。 过滤逻辑的移除在这里是刻意的，不是遗漏。
   */
  @Override
  public Optional<PolicySnapshot> findByPolicy(String policyNo, Long planId) {
    ClaimPolicy policy = claimPolicyRepository.findByPolicyNo(policyNo);
    if (policy == null) {
      return Optional.empty();
    }
    return Optional.of(toSnapshot(policy, planId));
  }

  /**
   * 实体 → 端口快照。
   *
   * <p><b>{@code insuredIdHashes} 是整份被保人清单</b>（不是其中一个）：一张团险保单承保一群人，
   * 报案时针对其中任一人都应成立。若这里只取一个，则该保单下其余人的报案会被 {@code SC_CLAIM_INSURED_MISMATCH} 一律拒掉。
   */
  private PolicySnapshot toSnapshot(ClaimPolicy policy, Long planId) {
    return new PolicySnapshot(
        policy.getPolicyNo(),
        planId,
        policy.getPolicyVersion(),
        parseInsuredHashes(policy.getInsuredSnapshot()),
        policy.getEffectiveStart(),
        policy.getEffectiveEnd());
  }

  /**
   * 解析被保人清单 JSON 为假名化 ID 列表。
   *
   * <p>形如 {@code ["hash-a","hash-b"]}。刻意<b>不引 JSON 库</b>：清单格式由本仓自己写入（运营录入）， 结构固定为字符串数组；而一个"解析失败 ⇒
   * 空清单"的兜底会把"数据坏了"伪装成"该保单没承保任何人"， 表现为所有报案被 403 拒收却看不出原因。故解析失败时抛出，让问题显性化。
   *
   * <p>清单为空 ⇒ 返回空列表（合法：保单可先建后录被保人，此时无人可报案）。
   */
  private static java.util.List<String> parseInsuredHashes(String insuredSnapshot) {
    java.util.List<String> result = new java.util.ArrayList<>();
    if (insuredSnapshot == null || insuredSnapshot.isBlank()) {
      return result;
    }
    String trimmed = insuredSnapshot.trim();
    if (trimmed.equals("[]")) {
      return result;
    }
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
      throw new IllegalStateException("被保人清单格式非法（应为 JSON 字符串数组）: " + trimmed);
    }
    String body = trimmed.substring(1, trimmed.length() - 1).trim();
    if (body.isEmpty()) {
      return result;
    }
    for (String token : body.split(",")) {
      String item = token.trim();
      if (item.isEmpty()) {
        continue;
      }
      if (item.length() >= 2 && item.startsWith("\"") && item.endsWith("\"")) {
        result.add(item.substring(1, item.length() - 1));
      } else {
        throw new IllegalStateException("被保人清单含非字符串元素: " + item);
      }
    }
    return result;
  }
}
