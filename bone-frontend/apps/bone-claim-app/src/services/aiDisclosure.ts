/**
 * AI 参与决策告知（金发〔2026〕8号 §21 显著标识 / §22 人工最终决策 / PIPL 第 24 条）。
 *
 * <p>后端唯一真源：`GET /api/v1/claims/{claimId}/decision/disclosure`，只读，基于已落库的
 * 决策执行与轨迹事实生成。**三态不可压平**：`UNDETERMINED`（无法确认）绝不能被前端翻译成
 * 「AI 未参与」或「无风险」——查不清就必须显示查不清。
 *
 * <p>受控码字典与后端 `AiDecisionDisclosure.Involvement` / `RIGHTS` 对齐；
 * 后端新增码而本表未跟随时展示**原始码**并给出警示，绝不编造一句中文把未知状态伪装成正常态。
 */

import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';

export type AiInvolvement = 'AI_ASSISTED' | 'AI_UNAVAILABLE' | 'UNDETERMINED';

export interface AiDisclosureEvidence {
  graphVersion: string;
  ruleVersion: string;
  declaredModelVersion: string;
  declaredPromptVersion: string;
  reportedModelVersion: string;
  reportedPromptVersion: string;
  decisionResult: string;
  triageReason: string;
  consumptionReason: string;
  status: string;
  finishedAt: string | null;
  traceNodeCount: number;
  llmNodeActionResult: string;
  generatedFields: string[];
  rejectReasons: string[];
  traceGaps: string[];
}

export interface AiDisclosure {
  claimId: string;
  traceId: string;
  involvement: AiInvolvement;
  involvementReason: string;
  label: string;
  aiRole: string;
  rights: string[];
  humanFinalDecision: string;
  retentionNote: string;
  evidence: AiDisclosureEvidence;
  scopeNote: string;
}

export type DisclosureTone = 'info' | 'warning';

export interface InvolvementMeta {
  label: string;
  tone: DisclosureTone;
  hint: string;
}

/** 已知受控码。未知码必须降级显示原始码，不得乐观映射。 */
export const INVOLVEMENT_META: Record<AiInvolvement, InvolvementMeta> = {
  AI_ASSISTED: {
    label: '含 AI 辅助生成内容',
    tone: 'info',
    hint: '本次决策的 LLM 节点真实执行，AI 产出建议、置信度与解释材料；最终决定仍由人工作出。',
  },
  AI_UNAVAILABLE: {
    label: 'AI 能力未可用（规则降级）',
    tone: 'warning',
    hint: 'AI 节点降级为规则路径。这不代表风险已排除，也不等于「AI 未参与」。',
  },
  UNDETERMINED: {
    label: '无法确认 AI 是否参与',
    tone: 'warning',
    hint: '缺少可判定的运行期证据（无执行记录 / 无 LLM 节点 / 输出不可解析 / 状态键缺失）。不得据此认定 AI 未参与。',
  },
};

export function involvementMeta(code: string): InvolvementMeta {
  const known = INVOLVEMENT_META[code as AiInvolvement];
  return known ?? {
    label: `未登记状态：${code}`,
    tone: 'warning',
    hint: '后端返回了本字典未登记的受控码，请连同 traceId 反馈；不得按「AI 未参与」处理。',
  };
}

/** 权利受控码 → 中文（对齐后端 AiDecisionDisclosure.RIGHTS）。 */
export const RIGHTS_LABELS: Record<string, string> = {
  REQUEST_HUMAN_REVIEW: '要求人工复核',
  APPEAL_WITHIN_48H: '48 小时内申诉',
  EXPLANATION_OF_DECISION: '要求说明决定依据',
};

export function rightLabel(code: string): string {
  return RIGHTS_LABELS[code] ?? `未登记权利：${code}`;
}

const claimClient = createApiClient('/api/v1');

export const aiDisclosureApi = {
  /** 只读披露：不带 traceId 取该案最近一次执行；无记录返回 UNDETERMINED 而非 404。 */
  disclosure(claimId: string): Promise<ApiResponse<AiDisclosure>> {
    return claimClient.get<never, ApiResponse<AiDisclosure>>(
      `/claims/${claimId}/decision/disclosure`,
    );
  },
};
