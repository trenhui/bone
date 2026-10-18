/**
 * i18n 面板：命名空间 + *Key 清单 + 裸奔 key 预警。
 *
 * 协议 §11.4 收敛形态（key 引用式，对标 Salesforce Custom Labels / Amis localeKey）：
 * - 顶层 i18n.namespace（如 bone.ui.t_claim_case）；文案字段配 *Key 即走
 *   t(namespace:key, { defaultValue: 内联中文 })；
 * - 解析链：有 key → 查包；命中则用；miss 则用内联中文（禁裸奔 key——
 *   正好绕开 i18next miss 返裸 key 的已知坑）；
 * - 本面板把散落各处的 *Key 收拢为一张清单：哪里用了、有没有内联兜底、
 *   命名空间是否按实例隔离（服务端实例化时重写为 bone.ui.<entityCode>）。
 *
 * 翻译缺失是 WARNING 不阻断发布（协议 §11.4）；裸奔（无 key 且无内联）才是要修的。
 *
 * v1.2 补齐：
 * - 采集补全 columns.format.title / stats.trend / degradedBanner.text /
 *   dictionaries.labelOverride——此前漏采，"无裸奔"结论本身不完整；
 * - 行内编辑（Key / 兜底两列可直接改，写回对应槽位）：此前是只读报表，
 *   发现裸奔要回各子面板逐个找位置（对标 Salesforce Translation Workbench）。
 */
import { useMemo } from 'react';
import { Alert, Button, Empty, Input, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import {
  setDegradedBanner, setDictLabel, setI18nNamespace, updateAction, updateColumn, updateFooter,
  updateLinkageThen, updateStat, updateSubmit, updateTab, viewOf, withView,
} from './schemaOps';

const { Text } = Typography;

interface KeyUsage {
  /** 出现位置（人话） */
  where: string;
  /** *Key 字段名 */
  field: string;
  key?: string;
  /** 内联兜底文案（有 = miss 时回落中文，无 = 裸奔） */
  fallback?: string;
  /** 行内编辑写回（仅 base 模板槽位提供；变体块只采集不编辑） */
  patch?: (p: { key?: string; fallback?: string }) => void;
}

/**
 * 收集 schema 内全部 *Key 引用（含变体块；纯函数，面板与探针同源）。
 *
 * @param write 传入时为基础模板槽位附带 patch 写回闭包（行内编辑用）；
 *              不传（探针场景）时只采集不挂闭包。
 */
export function collectKeyUsages(s: BoneUiSchema, write?: (next: BoneUiSchema) => void): KeyUsage[] {
  const out: KeyUsage[] = [];
  const push = (u: KeyUsage) => out.push(u);
  /** 有 write 才挂 patch（探针只采集；变体块不挂——编辑进变体面板） */
  const patcher = (fn: (p: { key?: string; fallback?: string }) => BoneUiSchema) =>
    write ? (p: { key?: string; fallback?: string }) => { write(fn(p)); } : undefined;

  const walkView = (
    label: string,
    view: { sections?: { title?: string; titleKey?: string; controls?: { fieldCode: string; labelOverride?: string; labelKey?: string }[] }[] } | undefined,
    viewKey: 'form' | 'detail',
  ) => {
    (view?.sections ?? []).forEach((sec, si) => {
      if (sec.titleKey || sec.title) {
        push({
          where: `${label}·分区「${sec.title ?? sec.titleKey}」`, field: 'titleKey', key: sec.titleKey, fallback: sec.title,
          patch: patcher((p) => {
            const cur = viewOf(s, viewKey);
            return withView(s, {
              ...cur,
              sections: cur.sections.map((x, i) => (i === si ? {
                ...x,
                ...(p.key !== undefined ? { titleKey: p.key || undefined } : {}),
                ...(p.fallback !== undefined ? { title: p.fallback || undefined } : {}),
              } : x)),
            }, viewKey);
          }),
        });
      }
      (sec.controls ?? []).forEach((c, ci) => {
        if (c.labelKey || c.labelOverride) {
          push({
            where: `${label}·控件 ${c.fieldCode}`, field: 'labelKey', key: c.labelKey, fallback: c.labelOverride,
            patch: patcher((p) => {
              const cur = viewOf(s, viewKey);
              return withView(s, {
                ...cur,
                sections: cur.sections.map((sec2, i) => (i === si ? {
                  ...sec2,
                  controls: sec2.controls.map((c2, j) => (j === ci ? {
                    ...c2,
                    ...(p.key !== undefined ? { labelKey: p.key || undefined } : {}),
                    ...(p.fallback !== undefined ? { labelOverride: p.fallback || undefined } : {}),
                  } : c2)),
                } : sec2)),
              }, viewKey);
            }),
          });
        }
      });
    });
  };
  walkView('表单', s.views?.form, 'form');
  walkView('详情', s.views?.detail, 'detail');
  (s.views?.list?.tabs ?? []).forEach((t, i) => {
    if (t.titleKey || t.title) {
      push({
        where: `列表·页签${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title,
        patch: patcher((p) => updateTab(s, i, {
          ...(p.key !== undefined ? { titleKey: p.key || undefined } : {}),
          ...(p.fallback !== undefined ? { title: p.fallback || undefined } : {}),
        })),
      });
    }
  });
  (s.views?.list?.stats ?? []).forEach((t, i) => {
    if (t.titleKey || t.title) {
      push({
        where: `列表·统计卡${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title,
        patch: patcher((p) => updateStat(s, i, {
          ...(p.key !== undefined ? { titleKey: p.key || undefined } : {}),
          ...(p.fallback !== undefined ? { title: p.fallback || undefined } : {}),
        })),
      });
    }
    // 趋势说明是纯文案槽位（无 *Key 形态），只采集兜底
    if (t.trend) {
      push({
        where: `列表·统计卡${i + 1}`, field: 'trend', fallback: t.trend,
        patch: patcher((p) => updateStat(s, i, p.fallback !== undefined ? { trend: p.fallback || undefined } : {})),
      });
    }
  });
  // 列标题覆盖（format.title：纯文案槽位）
  (s.views?.list?.columns ?? []).forEach((c, i) => {
    const title = c.format?.title as string | undefined;
    if (title) {
      push({
        where: `列表·列 ${c.fieldCode}`, field: 'format.title', fallback: title,
        patch: patcher((p) => updateColumn(s, i, p.fallback !== undefined ? { format: { ...c.format, title: p.fallback || undefined } } : {})),
      });
    }
  });
  // 降级横幅文案（纯文案槽位）
  if (s.views?.list?.degradedBanner?.text) {
    push({
      where: '列表·降级横幅', field: 'degradedBanner.text', fallback: s.views.list.degradedBanner.text,
      patch: patcher((p) => setDegradedBanner(s, {
        when: s.views?.list?.degradedBanner?.when ?? 'llm_unavailable',
        text: p.fallback ?? s.views?.list?.degradedBanner?.text ?? '',
      })),
    });
  }
  (s.linkageRules ?? []).forEach((r, i) => {
    (r.then ?? []).forEach((t, ti) => {
      if (t.noticeKey || t.notice) {
        push({
          where: `联动${i + 1}·动作${ti + 1}`, field: 'noticeKey', key: t.noticeKey, fallback: t.notice,
          patch: patcher((p) => updateLinkageThen(s, i, ti, {
            ...(p.key !== undefined ? { noticeKey: p.key || undefined } : {}),
            ...(p.fallback !== undefined ? { notice: p.fallback || undefined } : {}),
          })),
        });
      }
    });
  });
  (s.submitRules ?? []).forEach((r, i) => {
    if (r.messageKey || r.message) {
      push({
        where: `校验${i + 1}`, field: 'messageKey', key: r.messageKey, fallback: r.message,
        patch: patcher((p) => updateSubmit(s, i, {
          ...(p.key !== undefined ? { messageKey: p.key || undefined } : {}),
          ...(p.fallback !== undefined ? { message: p.fallback || undefined } : {}),
        })),
      });
    }
  });
  (s.actionBar ?? []).forEach((a, i) => {
    if (a.confirm?.titleKey || a.confirm?.title) {
      push({
        where: `动作${i + 1}·确认标题`, field: 'titleKey', key: a.confirm.titleKey, fallback: a.confirm.title,
        patch: patcher((p) => updateAction(s, i, {
          confirm: {
            ...a.confirm,
            ...(p.key !== undefined ? { titleKey: p.key || undefined } : {}),
            ...(p.fallback !== undefined ? { title: p.fallback || undefined } : {}),
          },
        })),
      });
    }
    if (a.confirm?.contentKey || a.confirm?.content) {
      push({
        where: `动作${i + 1}·确认正文`, field: 'contentKey', key: a.confirm.contentKey, fallback: a.confirm.content,
        patch: patcher((p) => updateAction(s, i, {
          confirm: {
            ...a.confirm,
            ...(p.key !== undefined ? { contentKey: p.key || undefined } : {}),
            ...(p.fallback !== undefined ? { content: p.fallback || undefined } : {}),
          },
        })),
      });
    }
  });
  if (s.footer?.disclaimerKey || s.footer?.disclaimer) {
    push({
      where: '合规条', field: 'disclaimerKey', key: s.footer.disclaimerKey, fallback: s.footer.disclaimer,
      patch: patcher((p) => updateFooter(s, {
        ...(p.key !== undefined ? { disclaimerKey: p.key || undefined } : {}),
        ...(p.fallback !== undefined ? { disclaimer: p.fallback || undefined } : {}),
      })),
    });
  }
  // 字典覆写 label（纯文案槽位：按 code 值覆写显示名）
  Object.entries(s.dictionaries ?? {}).forEach(([key, ov]) => {
    Object.entries((ov as { labelOverride?: Record<string, string> } | undefined)?.labelOverride ?? {}).forEach(([value, label]) => {
      push({
        where: `字典「${key}」选项 ${value}`, field: 'labelOverride', fallback: label,
        patch: patcher((p) => setDictLabel(s, key, value, p.fallback ?? label)),
      });
    });
  });
  // 变体块：只采集（编辑请进变体面板对应子面板），避免跨层写回复杂度
  (s.variants ?? []).forEach((v) => {
    const walk = (label: string, view: { sections?: { title?: string; titleKey?: string; controls?: { fieldCode: string; labelOverride?: string; labelKey?: string }[] }[] } | undefined) => {
      (view?.sections ?? []).forEach((sec) => {
        if (sec.titleKey || sec.title) push({ where: `${label}·分区「${sec.title ?? sec.titleKey}」`, field: 'titleKey', key: sec.titleKey, fallback: sec.title });
        (sec.controls ?? []).forEach((c) => {
          if (c.labelKey || c.labelOverride) push({ where: `${label}·控件 ${c.fieldCode}`, field: 'labelKey', key: c.labelKey, fallback: c.labelOverride });
        });
      });
    };
    walk(`变体${v.key}·表单`, v.views?.form);
    walk(`变体${v.key}·详情`, v.views?.detail);
    (v.views?.list?.tabs ?? []).forEach((t, i) => {
      if (t.titleKey || t.title) push({ where: `变体${v.key}·页签${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title });
    });
  });
  return out;
}

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  entityCode: string;
}

export default function I18nPanel({ schema, onChange, entityCode }: Props): React.ReactElement {
  const ns = schema.i18n?.namespace ?? '';
  // 传入 onChange：基础模板槽位获得行内编辑写回能力（发现裸奔 → 就地修）
  const usages = useMemo(() => collectKeyUsages(schema, onChange), [schema, onChange]);
  const bareCount = usages.filter((u) => !u.key && !u.fallback).length;
  const keyedCount = usages.filter((u) => !!u.key).length;
  const inlineOnly = usages.filter((u) => !u.key && !!u.fallback).length;
  const editableCount = usages.filter((u) => !!u.patch).length;
  const suggestedNs = `bone.ui.${entityCode}`;

  return (
    <div>
      <Alert
        type="info" showIcon style={{ marginBottom: 12 }}
        message="文案解析链：有 *Key → 查语言包；miss → 回落内联中文（禁裸奔 key）"
        description="语言包按 namespace 分段维护在 shared-utils/i18n/locales/{zh-CN,en-US}.json，随模板资产包版本走；翻译缺失是 WARNING 不阻断发布。"
      />

      <Space direction="vertical" size={10} style={{ width: '100%', marginBottom: 14 }}>
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>命名空间（i18n.namespace）</Text>
          <div style={{ display: 'flex', gap: 8, marginTop: 4, alignItems: 'center' }}>
            <Input
              style={{ maxWidth: 360 }} placeholder={suggestedNs}
              value={ns}
              onChange={(e) => onChange(setI18nNamespace(schema, e.target.value.trim()))}
              addonBefore={<GlobalOutlined />}
            />
            {ns !== suggestedNs && (
              <Tooltip title={'按实例隔离命名空间，防多实例共用同一 namespace 导致词条串味（服务端实例化时重写）'}>
                <Button size="small" onClick={() => onChange(setI18nNamespace(schema, suggestedNs))}>
                  用建议值 {suggestedNs}
                </Button>
              </Tooltip>
            )}
          </div>
          {!ns && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              未设置命名空间：*Key 将无法解析（回落内联中文）；建议按实体隔离。
            </Text>
          )}
        </div>
      </Space>

      <Space wrap style={{ marginBottom: 10 }}>
        <Tag color="blue">已配 Key {keyedCount}</Tag>
        <Tag color="green">内联兜底 {inlineOnly}</Tag>
        {bareCount > 0 ? <Tag color="red">裸奔 {bareCount}</Tag> : <Tag color="green">无裸奔</Tag>}
        {editableCount > 0 && <Tooltip title="Key / 兜底两列可直接行内编辑，写回对应配置槽位（变体块请进变体面板编辑）"><Tag color="geekblue">可行内编辑 {editableCount}</Tag></Tooltip>}
      </Space>

      {usages.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前 schema 没有文案槽位（内联文案同样可用，按需再迁到 Key 引用式）" style={{ padding: '18px 0' }} />
      ) : (
        <Table<KeyUsage>
          size="small" rowKey={(r) => `${r.where}-${r.field}`}
          pagination={false} dataSource={usages}
          columns={[
            { title: '位置', dataIndex: 'where', width: 200 },
            { title: '槽位', dataIndex: 'field', width: 110, render: (v: string) => <Text code style={{ fontSize: 11 }}>{v}</Text> },
            {
              title: 'Key', width: 180,
              render: (_: unknown, r) => r.patch ? (
                <Input
                  size="small" placeholder="命名空间下的 key" value={r.key ?? ''}
                  onChange={(e) => r.patch?.({ key: e.target.value.trim() || undefined })}
                />
              ) : r.key
                ? <Text code style={{ fontSize: 11 }}>{ns ? `${ns}:${r.key}` : r.key}</Text>
                : <Text type="secondary" style={{ fontSize: 11 }}>—</Text>,
            },
            {
              title: '内联兜底',
              render: (_: unknown, r) => r.patch ? (
                <Input
                  size="small" placeholder="miss 时回落的中文" value={r.fallback ?? ''}
                  onChange={(e) => r.patch?.({ fallback: e.target.value })}
                />
              ) : r.fallback
                ? <Text style={{ fontSize: 12 }}>{r.fallback}</Text>
                : <Tag color="red" style={{ fontSize: 11 }}>无兜底</Tag>,
            },
            {
              title: '状态', width: 90,
              render: (_: unknown, r) => r.key
                ? <Tag color="blue" style={{ fontSize: 11 }}>Key 引用</Tag>
                : r.fallback
                  ? <Tag color="green" style={{ fontSize: 11 }}>内联</Tag>
                  : <Tag color="red" style={{ fontSize: 11 }}>裸奔</Tag>,
            },
          ]}
        />
      )}
    </div>
  );
}
