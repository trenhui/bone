package com.bone.ailedger.domain.gateway;

import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.domain.model.LimitSnapshot;
import com.bone.ailedger.domain.model.LimitVerdict;
import java.math.BigDecimal;

/**
 * 多维额度台账端口（域中立确定性内核，供产品域理算 / 控额直接复用）。
 *
 * <p><b>为什么在 bone-ai 而不在业务模块</b>：这是从 pk-tpa-saas 控额引擎里抽出的<b>通用机械</b>——多维额度记账 + 两阶段占位 + 冻结解冻 +
 * 编码化结论，本身不含任何保险语义。保险语义（维度怎么定义、上限多少、免赔怎么算、超限走哪条后付责任）归产品域，在此内核上实现。 bone-ai
 * 提供内核与结论契约，产品域提供策略——与「平台只做证明与治理、不做判断与决策」一致。
 *
 * <p>默认实现 {@code InMemoryLimitLedger}（进程内、单机正确）。多副本 / 需全局一致时，以 {@code @Bean} 覆盖为共享存储实现 （Redis 等，属
 * L3 依赖）——端口不变，产品域调用代码零改动。
 *
 * <p>恒等式：{@code available = limit - consumed - reserved - frozen}。未登记上限的范围可用额为
 * 0（fail-closed：没配限额就不放行）。
 */
public interface LimitLedger {

  /** 登记 / 更新某维度范围的上限。 */
  LimitVerdict declareLimit(LimitScope scope, BigDecimal limit);

  /**
   * 预留额度（两阶段第一步）：持有 amount，可用额即时减少，但未计入已占用。
   *
   * <p>同一 {@code reservationId} 重复调用且参数一致时幂等返回已存在的预留（供重试 / 补偿队列安全重放）； 参数不一致则返回 {@code
   * RESERVATION_NOT_FOUND}（标识冲突），绝不静默改成另一笔。
   */
  LimitVerdict reserve(String reservationId, LimitScope scope, BigDecimal amount);

  /** 提交预留（两阶段第二步）：预留额转为已实际占用。 */
  LimitVerdict commit(String reservationId);

  /** 释放预留：作废占位，额度返还可用。 */
  LimitVerdict release(String reservationId);

  /** 冻结额度（直付占资）：可用额减少，但既不计占用也不产生预留单。 */
  LimitVerdict freeze(LimitScope scope, BigDecimal amount);

  /** 解冻额度：冻结额返还可用。 */
  LimitVerdict thaw(LimitScope scope, BigDecimal amount);

  /** 当前可用额度（未登记上限返回 0）。 */
  BigDecimal available(LimitScope scope);

  /** 维度范围完整快照（对账 / 监控）。 */
  LimitSnapshot snapshot(LimitScope scope);
}
