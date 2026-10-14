/**
 * 前端协议一致性自检。
 *
 * 定位：保存前的"快速反馈"，不替代后端校验（后端 validate 才是发布闸门）。
 * 与后端 UI-REF-xx 码的关系：前端报的是**本地可判**的问题（引用悬空、配置缺失、props 越界），
 * 后端报的是**跨真源**的问题（字段是否真存在、动作码是否注册）。两者互补，不重叠。
 *
 * 每条带 `locate`，点击可跳到配置面板对应位置 —— 自检从"告诉你有问题"升级为"带你到问题那里"。
 */
import { parseEnumValues } from '@bone/shared-types';
import type { BoneUiSchema, UiCondition } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { FN_NEEDS_COMPARE, PROPS_PENDING_REVIEW, PROP_WHITELIST } from './constants';
import { dictOf, formOf } from './schemaOps';

export type LintLevel = 'error' | 'warning' | 'info';

export interface LintLocate {
  tab: 'structure' | 'rules' | 'actions' | 'list' | 'dict';
  /** 分区下标 / 控件下标 */
  si?: number;
  ci?: number;
  /** 规则下标 / then 下标 */
  ri?: number;
  ti?: number;
  /** 列下标 / 动作下标 */
  idx?: number;
  /** 字典键 */
  key?: string;
}

export interface LintIssue {
  level: LintLevel;
  message: string;
  locate?: LintLocate;
}

/** 递归收集条件里引用的字段 */
function condFields(c: UiCondition | undefined, out: string[] = []): string[] {
  if (!c) return out;
  if (c.all) c.all.forEach((x) => condFields(x, out));
  if (c.any) c.any.forEach((x) => condFields(x, out));
  if (c.not) condFields(c.not, out);
  if (c.fieldCode) out.push(c.fieldCode);
  if (c.valueType === 'field' && c.compareField) out.push(c.compareField);
  return out;
}

/**
 * 执行自检。
 * @param knownFields 实体真实字段 code 集合（用于判悬空引用）
 * @param enumFields  带枚举的字段 → 可选值 code 列表（用于判字典覆写越界）
 */
export function lintSchema(
  s: BoneUiSchema,
  knownFields: Set<string>,
  enumFields: Record<string, string[]>,
): LintIssue[] {
  const issues: LintIssue[] = [];
  const form = formOf(s);
  const sections = form.sections ?? [];

  /* ---------- 结构 ---------- */
  if (!sections.length) {
    issues.push({ level: 'warning', message: '表单没有任何分区，运行时将以默认布局渲染', locate: { tab: 'structure' } });
  }

  const seen = new Map<string, LintLocate>();
  sections.forEach((sec, si) => {
    if (!sec.controls?.length) {
      issues.push({ level: 'warning', message: `分区「${sec.title || sec.id}」没有任何控件`, locate: { tab: 'structure', si } });
    }
    (sec.controls ?? []).forEach((c, ci) => {
      const at = { tab: 'structure' as const, si, ci };

      if (!c.fieldCode) {
        issues.push({ level: 'error', message: `分区「${sec.title || sec.id}」第 ${ci + 1} 个控件未绑定字段`, locate: at });
      } else if (c.virtual) {
        // 协议 v1.2：STAT/CHART/TIMELINE/COMPUTED 虚拟控件不是 meta_field 引用（经 dataRef/lineage 解析），
        // 不做实体字段存在性校验；但 fieldCode 禁与真实字段码碰撞（否则联动/字典会误命中）。
        if (knownFields.has(c.fieldCode)) {
          issues.push({ level: 'error', message: `虚拟控件 ${c.fieldCode} 与实体真实字段码碰撞（协议禁止）`, locate: at });
        }
      } else {
        if (seen.has(c.fieldCode)) {
          issues.push({ level: 'error', message: `字段 ${c.fieldCode} 在表单中重复出现，联动将互相覆盖`, locate: at });
        } else {
          seen.set(c.fieldCode, at);
        }
        if (!knownFields.has(c.fieldCode)) {
          issues.push({ level: 'error', message: `控件引用了实体中不存在的字段 ${c.fieldCode}`, locate: at });
        }
      }

      Object.keys(c.props ?? {}).forEach((k) => {
        if (PROPS_PENDING_REVIEW.includes(k)) {
          issues.push({ level: 'warning', message: `控件 ${c.fieldCode} 用了 props.${k}（v1.2 待评审，尚未进协议白名单）`, locate: at });
        } else if (!(PROP_WHITELIST as readonly string[]).includes(k)) {
          issues.push({ level: 'error', message: `控件 ${c.fieldCode} 的 props.${k} 不在协议白名单（schema.json additionalProperties=false，越界属性运行时不做保证）`, locate: at });
        }
      });
    });
  });

  /* ---------- 联动规则 ---------- */
  (s.linkageRules ?? []).forEach((r, ri) => {
    const at = { tab: 'rules' as const, ri };
    const tag = r.code || r.name || `规则${ri + 1}`;

    if (!r.when || Object.keys(r.when).length === 0) {
      issues.push({ level: 'warning', message: `联动「${tag}」没有 when 条件，将始终命中`, locate: at });
    }
    if (!r.then?.length) {
      issues.push({ level: 'error', message: `联动「${tag}」没有 then 动作，配置后无任何效果`, locate: at });
    }

    condFields(r.when).forEach((fc) => {
      if (fc && !seen.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: `联动「${tag}」的 when 引用了不存在的字段 ${fc}`, locate: at });
      }
    });

    (r.then ?? []).forEach((t, ti) => {
      if (!t.fieldCode) {
        issues.push({ level: 'error', message: `联动「${tag}」第 ${ti + 1} 个动作未指定字段`, locate: { ...at, ti } });
      } else if (!seen.has(t.fieldCode) && !knownFields.has(t.fieldCode)) {
        issues.push({ level: 'warning', message: `联动「${tag}」作用于表单外的字段 ${t.fieldCode}，运行时看不到效果`, locate: { ...at, ti } });
      }
    });

    if (r.enabled === false) {
      issues.push({ level: 'info', message: `联动「${tag}」已停用（运行期不生效）`, locate: at });
    }
  });

  /* ---------- 提交校验 ---------- */
  (s.submitRules ?? []).forEach((r, ri) => {
    const at = { tab: 'rules' as const, ri };
    const tag = r.code || r.name || `校验${ri + 1}`;

    if (!r.fields?.length) {
      issues.push({ level: 'error', message: `校验「${tag}」未指定 fields，引擎无从取数`, locate: at });
    }
    if (!r.message) {
      issues.push({ level: 'warning', message: `校验「${tag}」缺少 message，失败时用户看不到原因`, locate: at });
    }
    if (FN_NEEDS_COMPARE.includes(r.function) && !r.compare) {
      issues.push({ level: 'error', message: `校验「${tag}」的 ${r.function} 缺 compare 右值，引擎将按 fail-closed 阻断`, locate: at });
    }
    if (r.compare?.valueType === 'field' && !r.compare.fieldCode) {
      issues.push({ level: 'error', message: `校验「${tag}」的比较值选了「字段」但未填字段编码`, locate: at });
    }
    if (r.function === 'balance' && (r.fields?.length ?? 0) < 2) {
      issues.push({ level: 'error', message: `校验「${tag}」balance 需要两组字段（借/贷）`, locate: at });
    }

    (r.fields ?? []).forEach((fc) => {
      const root = String(fc).split('.')[0];
      if (root && !seen.has(root) && !knownFields.has(root)) {
        issues.push({ level: 'warning', message: `校验「${tag}」引用了不存在的字段 ${fc}`, locate: at });
      }
    });
    condFields(r.when).forEach((fc) => {
      if (fc && !seen.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: `校验「${tag}」的 when 引用了不存在的字段 ${fc}`, locate: at });
      }
    });

    if (r.enabled === false) {
      issues.push({ level: 'info', message: `校验「${tag}」已停用（运行期不生效）`, locate: at });
    }
  });

  /* ---------- 动作条 ---------- */
  (s.actionBar ?? []).forEach((a, i) => {
    const at = { tab: 'actions' as const, idx: i };
    if (!a.actionCode) {
      issues.push({ level: 'error', message: `动作条第 ${i + 1} 项未填 actionCode`, locate: at });
    }
    condFields(a.visibleWhen).forEach((fc) => {
      if (fc && !seen.has(fc) && !knownFields.has(fc)) {
        issues.push({ level: 'warning', message: `动作 ${a.actionCode || i + 1} 的门控引用了不存在的字段 ${fc}`, locate: at });
      }
    });
  });

  /* ---------- 列表列 ---------- */
  (s.views.list?.columns ?? []).forEach((col, idx) => {
    if (col.fieldCode && !knownFields.has(col.fieldCode)) {
      issues.push({ level: 'info', message: `列表列 ${col.fieldCode} 不在实体字段中（聚合/关联列属正常）`, locate: { tab: 'list', idx } });
    }
  });

  /* ---------- 字典覆写（协议 §3.1：只引用，不新造） ---------- */
  Object.keys(s.dictionaries ?? {}).forEach((key) => {
    const at = { tab: 'dict' as const, key };
    const known = enumFields[key];
    if (!known) {
      issues.push({ level: 'error', message: `dictionaries 覆写了实体中不存在的码表 ${key}`, locate: at });
      return;
    }
    const ov = dictOf(s, key);
    (ov?.hidden ?? []).forEach((v) => {
      if (!known.includes(v)) {
        issues.push({ level: 'error', message: `码表 ${key} 隐藏了不存在的选项 ${v}（UI 层不得新造选项）`, locate: at });
      }
    });
    Object.keys(ov?.labelOverride ?? {}).forEach((v) => {
      if (!known.includes(v)) {
        issues.push({ level: 'error', message: `码表 ${key} 改写了不存在的选项 ${v}（UI 层不得新造选项）`, locate: at });
      }
    });
    // 全隐藏 = 运行时该字段无可选值
    if (known.length && (ov?.hidden ?? []).length >= known.length) {
      issues.push({ level: 'warning', message: `码表 ${key} 的所有选项都被隐藏，该字段运行时将无值可选`, locate: at });
    }
  });

  return issues;
}

/** 从实体字段推导「带枚举的字段 → 可选值 code 列表」 */
export function enumFieldsOf(fields: MetaField[]): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  fields.forEach((f) => {
    const opts = parseEnumValues(f.enumValues);
    if (opts.length) out[f.code] = opts.map((o) => o.code);
  });
  return out;
}

/** 统计各级别数量 */
export function countLevels(issues: LintIssue[]): Record<LintLevel, number> {
  return {
    error: issues.filter((i) => i.level === 'error').length,
    warning: issues.filter((i) => i.level === 'warning').length,
    info: issues.filter((i) => i.level === 'info').length,
  };
}
