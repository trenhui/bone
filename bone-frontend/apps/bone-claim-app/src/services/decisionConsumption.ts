/**
 * 决策结论「消费结果」字典 —— **唯一真源**。
 *
 * <p>取值来自后端 `AutoApprovalApplicationService.AutoApprovalOutcome.reason()`（受控码），
 * 落库在 `claim_decision_execution.consumption_reason`，读侧经
 * `GET /review/tasks/{id}/execution` 返回。后端新增取值而本表未跟随时，
 * {@link consumptionReasonMeta} 回退显示**原始码**——降级可见，而不是编造一句中文把未知状态
 * 伪装成正常态（那会让「为什么没放款」永远查不清）。
 *
 * <p>为什么只在前端做文案、不做判断：要不要放款只由后端开关与上限决定，前端不重算
 * （重算会得到「现在重跑会怎样」，而不是「当时为什么没放款」）。这里只负责翻译与配色。
 */
import { CONSUMPTION_REASON_DICT } from '../constants/dicts.generated';

export type ConsumptionTone = 'success' | 'warning' | 'neutral';

export interface ConsumptionReasonMeta {
  /** 短标签（放 Tag 里） */
  label: string;
  /** 一句话解释（放 Alert 描述里） */
  hint: string;
  /** 语义色：success=资金已动；warning=该动没动，需要人看；neutral=本就没有消费动作 */
  tone: ConsumptionTone;
}

/** 已知受控码。新增码必须在后端 `AutoApprovalApplicationService` 同时登记，否则属契约缺口。 */
const CONSUMPTION_REASON_BASE: Record<string, { hint: string; tone: ConsumptionTone }> = {
  APPLIED: {
    hint: '结论为自动通过，案件已结案并生成结算单（资金链有单）。',
    tone: 'success',
  },
  AUTO_APPROVE_DISABLED: {
    hint: '结论为自动通过，但自动放款总开关未开启（策略待签核），案件保持已初审，需人工核赔。',
    tone: 'warning',
  },
  CAP_EXCEEDED: {
    hint: '结论为自动通过，但理算金额超过自动放款单笔上限，按策略转人工核赔。',
    tone: 'warning',
  },
  ALREADY_SETTLED: {
    hint: '结论为自动通过，但案件在此刻已结案（可能是另一个人工路径先完成），本次未重复出金。',
    tone: 'neutral',
  },
  NO_DECISION: {
    hint: '消费时找不到该案的决策执行记录——属数据缺陷，请连同 traceId 反馈。',
    tone: 'warning',
  },
  CLAIM_NOT_FOUND: {
    hint: '消费时找不到案件聚合（可能已被删除或租户上下文漂移）——属数据缺陷。',
    tone: 'warning',
  },
  DUAL_REVIEW_REQUIRED: {
    hint: '结论为自动通过，但金额超过双人复核门，按策略转双人复核流程。',
    tone: 'warning',
  },
  RISK_GATE_BLOCKED: {
    hint: '结论为自动通过，但决策链显式判否（风控命中 / 风险闸），按策略转人工。',
    tone: 'warning',
  },
};

/** label 来自后端单一真源（dicts.generated.ts），提示/语气为前端展示属性。 */
export const CONSUMPTION_REASON_META: Record<string, ConsumptionReasonMeta> = Object.entries(
  CONSUMPTION_REASON_BASE,
).reduce<Record<string, ConsumptionReasonMeta>>((acc, [code, base]) => {
  acc[code] = { ...base, label: CONSUMPTION_REASON_DICT[code] ?? code };
  return acc;
}, {});

const NOT_AUTO_APPROVE_PREFIX = 'NOT_AUTO_APPROVE:';

/**
 * 消费结果 → 展示元信息。
 *
 * @param reason 后端受控码；`null`/`undefined` 表示本次执行没有自动通过结论可消费
 * @returns 未知码返回「原始码 + neutral」，`null` 返回 `null`（调用方据此不渲染告警）
 */
export function consumptionReasonMeta(reason?: string | null): ConsumptionReasonMeta | null {
  if (!reason) {
    return null;
  }
  const known = CONSUMPTION_REASON_META[reason];
  if (known) {
    return known;
  }
  if (reason.startsWith(NOT_AUTO_APPROVE_PREFIX)) {
    const decision = reason.slice(NOT_AUTO_APPROVE_PREFIX.length);
    return {
      label: `非自动通过（${decision}）`,
      hint: `决策结论为 ${decision}，没有可消费的自动放款动作。`,
      tone: 'neutral',
    };
  }
  return { label: reason, hint: '未登记的消费结果码，请反馈以便补登记。', tone: 'neutral' };
}

/**
 * 是否需要人工关注（「该放款却没放」这一类）。
 *
 * <p>判据刻意不写成「不等于 APPLIED」：`ALREADY_SETTLED` 与「非自动通过」都不是异常，
 * 把它们一并标黄会让告警失去信噪比——运维很快学会无视它。
 */
export function needsAttention(reason?: string | null): boolean {
  const meta = consumptionReasonMeta(reason);
  return meta?.tone === 'warning';
}
