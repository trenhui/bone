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
 */
import { useMemo } from 'react';
import { Alert, Button, Empty, Input, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { GlobalOutlined } from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import { setI18nNamespace } from './schemaOps';

const { Text } = Typography;

interface KeyUsage {
  /** 出现位置（人话） */
  where: string;
  /** *Key 字段名 */
  field: string;
  key?: string;
  /** 内联兜底文案（有 = miss 时回落中文，无 = 裸奔） */
  fallback?: string;
}

/** 收集 schema 内全部 *Key 引用（含变体块；纯函数，面板与探针同源） */
export function collectKeyUsages(s: BoneUiSchema): KeyUsage[] {
  const out: KeyUsage[] = [];
  const walkView = (label: string, view: { sections?: { title?: string; titleKey?: string; controls?: { fieldCode: string; labelOverride?: string; labelKey?: string }[] }[]; pageHeader?: string[] } | undefined) => {
    (view?.sections ?? []).forEach((sec) => {
      if (sec.titleKey || sec.title) out.push({ where: `${label}·分区「${sec.title ?? sec.titleKey}」`, field: 'titleKey', key: sec.titleKey, fallback: sec.title });
      (sec.controls ?? []).forEach((c) => {
        if (c.labelKey || c.labelOverride) out.push({ where: `${label}·控件 ${c.fieldCode}`, field: 'labelKey', key: c.labelKey, fallback: c.labelOverride });
      });
    });
  };
  walkView('表单', s.views?.form);
  walkView('详情', s.views?.detail);
  (s.views?.list?.tabs ?? []).forEach((t, i) => {
    if (t.titleKey || t.title) out.push({ where: `列表·页签${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title });
  });
  (s.views?.list?.stats ?? []).forEach((t, i) => {
    if (t.titleKey || t.title) out.push({ where: `列表·统计卡${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title });
  });
  (s.linkageRules ?? []).forEach((r, i) => {
    (r.then ?? []).forEach((t, ti) => {
      if (t.noticeKey || t.notice) out.push({ where: `联动${i + 1}·动作${ti + 1}`, field: 'noticeKey', key: t.noticeKey, fallback: t.notice });
    });
  });
  (s.submitRules ?? []).forEach((r, i) => {
    if (r.messageKey || r.message) out.push({ where: `校验${i + 1}`, field: 'messageKey', key: r.messageKey, fallback: r.message });
  });
  (s.actionBar ?? []).forEach((a, i) => {
    if (a.confirm?.titleKey || a.confirm?.title) out.push({ where: `动作${i + 1}·确认标题`, field: 'titleKey', key: a.confirm.titleKey, fallback: a.confirm.title });
    if (a.confirm?.contentKey || a.confirm?.content) out.push({ where: `动作${i + 1}·确认正文`, field: 'contentKey', key: a.confirm.contentKey, fallback: a.confirm.content });
  });
  if (s.footer?.disclaimerKey || s.footer?.disclaimer) {
    out.push({ where: '合规条', field: 'disclaimerKey', key: s.footer.disclaimerKey, fallback: s.footer.disclaimer });
  }
  (s.variants ?? []).forEach((v) => {
    walkView(`变体${v.key}·表单`, v.views?.form);
    walkView(`变体${v.key}·详情`, v.views?.detail);
    (v.views?.list?.tabs ?? []).forEach((t, i) => {
      if (t.titleKey || t.title) out.push({ where: `变体${v.key}·页签${i + 1}`, field: 'titleKey', key: t.titleKey, fallback: t.title });
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
  const usages = useMemo(() => collectKeyUsages(schema), [schema]);
  const bareCount = usages.filter((u) => !u.key && !u.fallback).length;
  const keyedCount = usages.filter((u) => !!u.key).length;
  const inlineOnly = usages.filter((u) => !u.key && !!u.fallback).length;
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
              <Tooltip title={`按实例隔离命名空间，防多实例共用同一 namespace 导致词条串味（服务端实例化时重写）`}>
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
              render: (_: unknown, r) => r.key
                ? <Text code style={{ fontSize: 11 }}>{ns ? `${ns}:${r.key}` : r.key}</Text>
                : <Text type="secondary" style={{ fontSize: 11 }}>—</Text>,
            },
            {
              title: '内联兜底',
              render: (_: unknown, r) => r.fallback
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
