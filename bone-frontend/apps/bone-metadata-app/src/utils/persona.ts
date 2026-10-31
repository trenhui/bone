import type { MetaField } from '../types';
import { parseEnumValues } from '@bone/shared-types';

/**
 * 角色化可见性（persona）——「同一份元数据，两个承载面」的判据层。
 *
 * <p><b>业界依据</b>：元数据驱动产品普遍把「构建面」与「消费面」分开承载，但**不维护两份
 * 列表页**——Salesforce 是 Setup vs Lightning App Page（同一 object 元数据 + Search Layouts），
 * ServiceNow 是 App Engine Studio vs Workspace（同一张表两套 View），Odoo 是同一 URL 的
 * 开发者模式开关。共同点：**形态一致、判据集中、按角色切换信息密度**。
 *
 * <p>本模块把判据做成纯函数，理由与前序 {@code projectRuntimeColumns} 一致——
 * 「同一处判定」比「多处各写一遍再靠测试保证」更可靠；任何 UI 分支都调用这里，
 * 禁止在 JSX 里散写 `code === 'tenant_id'` 这类字面量。
 */

/** 观看者模式：consume=业务用户（默认），implement=实施/建模工程师 */
export type ViewerMode = 'consume' | 'implement';

/** 本地偏好键（同 overlay 的定位：纯本机偏好，不进服务端契约） */
export const VIEWER_MODE_KEY = 'bone.meta.viewerMode';

/** 代理键的物理类型（业务单号/金额不会落在这些类型上） */
const SURROGATE_KEY_TYPES = new Set(['LONG', 'INTEGER', 'BIGINT']);

/** 显式业务字段名：命中即**不是**内部代理键 */
const BUSINESS_KEY_CODES = new Set(['code', 'name', 'title', 'label']);

/**
 * 是否为「内部代理键」字段——**消费面不展示**（如 `approver_id` 这类内部外键）。
 *
 * <p>判据（同时成立）：① 字段类型是整型；② code 以 `_id` 结尾；③ 不是显式业务字段名。
 * REFERENCE 类型不在判据内：它已被运行时 enrich 成 `__label`（人名/类别名），本来就不露 ID。
 *
 * <p><b>为什么用约定而非随字段配置</b>：根治办法是建模时把这些列声明为 REFERENCE 并按 label
 * 回显，那属于**数据模型层**改动，留给实施者在实施模式下做；本函数是**展示层兜底**，
 * 并保证实施者始终看得见（不删除信息，只是按角色分层）。
 *
 * <p><b>required 例外</b>：必填字段不隐藏——隐藏必填项会让表单提交缺列，
 * 把「显示问题」放大成「写入事故」。
 */
export function isTechnicalKeyField(field: MetaField): boolean {
  const code = (field.code || '').toLowerCase();
  const type = (field.type || '').toUpperCase();
  if (!SURROGATE_KEY_TYPES.has(type)) return false;
  if (BUSINESS_KEY_CODES.has(code)) return false;
  return code.endsWith('_id');
}

/**
 * 按观看者模式投影可读字段集合。
 * implement ⇒ 原样；consume ⇒ 剔除内部代理键（必填除外）。
 */
export function visibleFieldsFor(fields: MetaField[], mode: ViewerMode): MetaField[] {
  if (mode === 'implement') return fields;
  return fields.filter((f) => !isTechnicalKeyField(f) || f.required === true);
}

/** 当前模式下「因角色被隐藏」的技术字段：实施模式下自证「我藏了什么」（对标 Odoo 开发者模式） */
export function hiddenTechnicalFields(fields: MetaField[], mode: ViewerMode): MetaField[] {
  if (mode === 'implement') return [];
  return fields.filter((f) => isTechnicalKeyField(f) && f.required !== true);
}

/**
 * 业务主键（给业务用户看的记录标识）—— 优先「单号/编码/名称」，取不到则不暴露 ID。
 *
 * <p>为什么不用 {@code record.id}：雪花 ID 是**代理键**，用户对它既无认知也无用途
 * （DAMA：业务用户通过业务键定位实体）；把它印在弹窗标题里，是把物理实现泄漏进业务语义层。
 */
export function businessKeyOf(
  record: Record<string, unknown> | null | undefined,
  fields: MetaField[],
): { label: string; text: string } | null {
  if (!record) return null;
  const ordered = [...fields].sort((a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999));
  const pick =
    ordered.find((f) => /(^|_)(no|code)$/i.test(f.code) && (f.type || '').toUpperCase() === 'STRING') ??
    ordered.find((f) => BUSINESS_KEY_CODES.has((f.code || '').toLowerCase())) ??
    ordered.find((f) => (f.type || '').toUpperCase() === 'STRING');
  if (!pick) return null;
  const v = record[pick.code];
  if (v == null || v === '') return null;
  return { label: pick.displayName || pick.code, text: String(v) };
}

/**
 * 记录标识文案：实施面保留主键（排障必需），消费面只给业务键。
 */
export function recordIdentityOf(
  record: Record<string, unknown> | null | undefined,
  fields: MetaField[],
  mode: ViewerMode,
): string {
  if (!record) return '未选中记录';
  const biz = businessKeyOf(record, fields);
  const id = record.id != null ? String(record.id) : null;
  if (mode === 'implement') {
    return biz ? `主记录：${id ?? '—'}（${biz.label}：${biz.text}）` : `主记录：${id ?? '—'}`;
  }
  return biz ? `${biz.label}：${biz.text}` : '该记录';
}

/**
 * 枚举缺中文标签（早期「字符串数组」形态：label === code）。
 *
 * <p>这不是渲染缺陷而是**建模缺口**：没有中文 label，运行时只能显示 `APPROVED` 这类裸码。
 * 只在实施面提示（并对跳转入口负责），不对业务用户暴露——他既读不懂也无法修复。
 */
export function isLabelLessEnum(field: MetaField): boolean {
  if ((field.type || '').toUpperCase() !== 'ENUM') return false;
  const opts = parseEnumValues(field.enumValues);
  return opts.length > 0 && opts.every((o) => o.label === o.code);
}
