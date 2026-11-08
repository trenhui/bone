package com.bone.claim.application.port.out;

import java.util.List;
import java.util.Optional;

/**
 * 保单快照查询端口（出站，对接保单/承保系统，§3.5）。
 *
 * <p>报案前置校验用：保单生效中、被保人匹配、计划版本锁定值。 一期由本地 {@code *PortAdapter} 以存量同步数据应答；二期切真实保单系统。
 */
public interface PolicyQueryPort {

  /** 保单快照（**按"此刻生效中"过滤**，不存在/已过期返回 {@link Optional#empty()}）。 */
  Optional<PolicySnapshot> findActivePolicy(String policyNo, Long planId);

  /**
   * 按<b>保司侧保单号</b>查"此刻生效中"的保单（可能 0 条、1 条或多条）。
   *
   * <p><b>为什么必须返回列表、且不能在这里替调用方"取第一条"</b>：保司保单号在多保司/团险场景下不唯一——
   * 一张团险保单可以按成员或计划拆成多张平台保单，续保时也可能沿用同一保司号。若端口层静默取首条，
   * "这笔赔案记在哪张保单、扣哪个额度池"就由数据库返回顺序决定，属于<b>无提示的错记</b>（金额风险）， 而不是"查不到"这种显性失败。
   *
   * <p>消歧责任在应用层：先按被保人清单收敛，仍不唯一则 fail-closed（要求调用方补平台保单号）。
   *
   * @param insurerPolicyNo 保司侧保单号（调用方保证非空白）
   * @param planId 计划 ID（透传进快照，不参与本查询的过滤）
   * @return 命中列表（无命中返回空列表，不返回 {@code null}）
   */
  List<PolicySnapshot> findActivePoliciesByInsurerNo(String insurerPolicyNo, Long planId);

  /**
   * 保单快照（**不按"此刻是否生效中"过滤**，只回答该保单的期间）。
   *
   * <p><b>为什么必须与 {@link #findActivePolicy} 并存而不是加个参数</b>：两者回答的是两个不同问题，混用会把业务判断搞错——
   *
   * <ul>
   *   <li>{@code findActivePolicy}：报案前置校验"这张保单今天还能不能收案"。已满期保单必须返回空。
   *   <li>{@code findByPolicy}：额度年度归属"这笔费用落在第几个保单年度"。<b>必须能回答已满期保单</b>——
   *       年末发票跨年理赔、已满期保单的补赔/申诉复议/退票红冲，都要回到原保单年度记账。用"生效中"版本来问， 这些案件会一律查不到期间，被误判成"保单不存在"。
   * </ul>
   *
   * <p>返回的期间供 {@code QuotaYearResolver} 计算保单年度；实现方须保证同一 {@code policyNo + planId}
   * 恒返回同一期间（生效日是不可变事实），否则额度账会在年度边界上分裂成两本。
   *
   * @return 快照；查不到返回 {@link Optional#empty()}（**不得**返回"当天起一年"这类猜测值）
   */
  Optional<PolicySnapshot> findByPolicy(String policyNo, Long planId);

  /** 保单快照（报案校验最小字段集）。 */
  record PolicySnapshot(
      /** 平台保单号（本仓对外业务号，赔案表 {@code policy_no} 只存这个）。 */
      String policyNo,
      /**
       * 保司侧保单号（§16-2 双保单号）。
       *
       * <p><b>为什么快照要带它</b>：渠道可能同时上报平台号与保司号。只按平台号解析、把保司号丢掉，
       * 就无法发现"两个号指向不同保单"这种录入错误——赔案会静默记到平台号那张保单上，而渠道侧认的是另一张， 对账时才暴露。带上它才能做交叉校验（fail-closed）。
       */
      String insurerPolicyNo,
      Long planId,
      String planVersion,
      /**
       * 该保单承保的<b>被保人集合</b>（假名化 ID）。
       *
       * <p><b>为什么是集合而不是单个 ID</b>：一张团险保单承保的是<b>一群人</b>（参考实现里"保单下的被保人名单"
       * 是一张清单），报案时只能针对其中任一人生效。若这里只存一个 ID，则该保单下除此人以外的所有报案 都会被 {@code
       * SC_CLAIM_INSURED_MISMATCH}（403）拒掉——用集合才与"承保一群人"的业务事实一致。
       *
       * <p>实现方须保证集合内元素为服务端计算的假名化 ID（不落明文证件）。
       */
      java.util.List<String> insuredIdHashes,
      java.time.Instant effectiveFrom,
      java.time.Instant effectiveTo) {

    /** 该被保人是否在本保单承保范围内（集合为空 ⇒ 一概不承保）。 */
    public boolean covers(String insuredIdHash) {
      return insuredIdHash != null
          && !insuredIdHash.isBlank()
          && insuredIdHashes != null
          && insuredIdHashes.contains(insuredIdHash);
    }
  }
}
