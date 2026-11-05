/**
 * 拒赔 / 零赔付原因码字典。
 *
 * <p><b>为什么需要单独一份</b>：人工拒赔用「受控原因码」（数字码），理算引擎的零赔付归因用
 * 「ZERO_* 枚举 + 派生数字码」，AI 建议用「EVIDENCE_INSUFFICIENT 这类字符串码」。三者语义不同、
 * 取值不同，混用一个字典必然给出错的理由——而拒赔理由是审计与申诉举证的直接依据，错不得。
 *
 * <p><b>数字码取值来源</b>（不臆造，逐项有代码出处）：
 * <ul>
 *   <li>{@code 05 / 06 / 09} —— 由 {@code AdjustmentEngine#rejectionCodeOf} 从零赔付归因派生，
 *       即引擎真实会产出的码；改造前前端只提供 03/04/09/20/30/40，导致「引擎因免赔未达拒付(06)」
 *       在人工复核时选不到同义码，只能挑一个近似的，理由与事实不符；
 *   <li>{@code 03 / 04 / 20 / 30 / 40} —— 既有人工拒赔码（沿用 {@code ReviewWorkbenchPage} 原表，
 *       未经改动，避免改变在途案件的统计口径）。
 * </ul>
 *
 * <p>注意：后端目前只校验 `rejectType` 非空（{@code ClaimCase#decide}），尚未按白名单强校验，
 * 因此「前端只给受控选项」是当前唯一的口径收口点——不要在此处再放自由文本入口。
 */

import { REJECT_REASON_DICT, ZERO_REASON_DICT } from '../constants/dicts.generated';

export interface RejectReasonOption {
  /** 受控码（提交给后端的 rejectType） */
  value: string;
  /** 展示名 */
  label: string;
  /** 归类，用于 Select 分组 */
  group: '金额与责任' | '材料与合规' | '风险';
}

const REJECT_REASON_GROUP: Record<string, RejectReasonOption['group']> = {
  '03': '金额与责任',
  '04': '金额与责任',
  '05': '金额与责任',
  '06': '金额与责任',
  '09': '金额与责任',
  '20': '金额与责任',
  '30': '材料与合规',
  '40': '风险',
};

/** label 来自后端单一真源（dicts.generated.ts），分组为前端展示属性。 */
export const REJECT_REASON_OPTIONS: RejectReasonOption[] = Object.keys(REJECT_REASON_DICT).map(
  (code) => ({
    value: code,
    label: REJECT_REASON_DICT[code] ?? code,
    group: REJECT_REASON_GROUP[code] ?? '金额与责任',
  }),
);

const REJECT_REASON_MAP = new Map(REJECT_REASON_OPTIONS.map((o) => [o.value, o.label]));

/** 受控码 → 展示名；未知码回退原始码（降级可见，不编造理由）。 */
export function rejectReasonLabel(code?: string): string {
  if (!code) return '-';
  return REJECT_REASON_MAP.get(code) ?? code;
}

/** Select 分组选项（antd Select `options` 的 group 形态）。 */
export function rejectReasonGroups(): {
  label: string;
  options: { value: string; label: string }[];
}[] {
  const groups = new Map<string, { value: string; label: string }[]>();
  for (const o of REJECT_REASON_OPTIONS) {
    const bucket = groups.get(o.group) ?? [];
    bucket.push({ value: o.value, label: o.label });
    groups.set(o.group, bucket);
  }
  return [...groups.entries()].map(([label, options]) => ({ label, options }));
}

/* ===================== 理算零赔付归因（ZERO_*，与引擎常量逐字一致） ===================== */

/**
 * 零赔付归因码 → 中文。
 *
 * <p>取值与 {@code com.bone.claim.domain.model.adjustment.engine.AdjustmentEngine} 中的
 * `ZERO_*` 常量逐字对应；新增归因须同时改后端常量与本表，否则页面会显示原始码（可见降级）。
 */
/** 零赔付归因码 → 展示名；未知码回退原始码（单一真源 dicts.generated.ts）。 */
export const ZERO_REASON_LABELS = ZERO_REASON_DICT;

/** 零赔付归因码 → 展示名；未知码回退原始码。 */
export function zeroReasonLabel(code?: string): string {
  if (!code) return '-';
  return ZERO_REASON_LABELS[code] ?? code;
}

/* ===================== 理算行类型 ===================== */

/** 理算行类型（INPUT 输入快照 / RULE 规则处理 / OUTPUT 输出结论）。 */
export const ADJUSTMENT_LINE_TYPE_LABELS: Record<string, string> = {
  INPUT: '输入',
  RULE: '规则',
  OUTPUT: '输出',
};

export function adjustmentLineTypeLabel(code?: string): string {
  if (!code) return '-';
  return ADJUSTMENT_LINE_TYPE_LABELS[code] ?? code;
}
