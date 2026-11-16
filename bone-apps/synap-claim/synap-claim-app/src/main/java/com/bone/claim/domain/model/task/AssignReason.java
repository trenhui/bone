package com.bone.claim.domain.model.task;

/**
 * 归属理由码（写入任务行，回答「为什么是这个归属」）。
 *
 * <p><b>为什么理由码要落库而不是只写日志</b>：灰度窗口的命中率判据要按小时统计 「规则命中 / 回落入池」的比例；只写日志就得从日志里反推业务量，而日志会被轮转、采样、格式漂移。
 * 归属理由属于「归属是事实」的一部分（D4），与 assignee 同批写入同一事务。
 */
public enum AssignReason {
  /** 规则命中（{@code assign_rule_id} 非空）。 */
  RULE_MATCH,
  /** 无任何可用规则（规则表为空 / 全部不匹配）⇒ 入池，不臆造归属。 */
  NO_RULE,
  /** 有匹配规则但候选全部不可用（如目标主体为空或已失效）⇒ 入池，不臆造归属。 */
  ALL_INELIGIBLE,
  /** 存量回填（D6：按当前 first_reviewer_id 回填，其余入池）。 */
  BACKFILL
}
