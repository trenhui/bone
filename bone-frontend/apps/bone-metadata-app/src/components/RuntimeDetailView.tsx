/**
 * 运行时记录详情视图（协议 views.detail 的运行时投影，P0-3）。
 *
 * 背景（2026-10-18 诊断）：设计器创作侧对「详情视图」全线支持（编辑入口/预览/自检/
 * 变体合并/后端校验），运行时详情抽屉却是全字段平铺 Descriptions——views.detail 是
 * 死配置，建模者配的分区/页头/排序在线上完全看不到。本组件补上最后一环：
 * 按发布快照（executionSchema，已随 ctx 做变体裁决）投影详情布局。
 *
 * 口径：
 * - pageHeader（协议 §13.2）→ 顶部关键信息条（label: value 横排）；
 * - sections → 分区标题 + kind 徽标 + 逐控件 Descriptions（ENUM 回显 label）；
 * - 未配置 views.detail（或全空）→ 调用方回落平铺 Descriptions（存量行为零变化）。
 */
import { Descriptions, Empty, Space, Tag, Typography } from 'antd';
import { parseEnumValues } from '@bone/shared-types';
import type { MetaField } from '../types';
import type { FormView } from '../utils/uiSchemaEngine';

const { Text } = Typography;

/** 详情值回显：ENUM → label（与列表/表单同口径，避免详情显示裸 code 让用户以为录错），空 → '—' */
export function detailValueOf(field: MetaField | undefined, raw: unknown): string {
  if (raw == null || raw === '') return '—';
  if (field && (field.type || '').toUpperCase() === 'ENUM') {
    const hit = parseEnumValues(field.enumValues).find((v) => String(v.code) === String(raw));
    if (hit?.label) return hit.label;
  }
  if (typeof raw === 'object') return JSON.stringify(raw);
  return String(raw);
}

export function RuntimeDetailView({
  detail,
  fields,
  record,
}: {
  detail: FormView;
  fields: MetaField[];
  record: Record<string, unknown>;
}): React.ReactElement {
  const fieldMap = new Map(fields.map((f) => [f.code, f]));
  const sections = (detail.sections ?? []).filter((s) => (s.controls ?? []).length > 0);
  const pageHeader = detail.pageHeader ?? [];
  if (!sections.length && !pageHeader.length) {
    return <Empty description="该实体配置了详情视图但为空" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }
  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {pageHeader.length > 0 && (
        <div
          style={{
            background: '#f6f8fb',
            border: '1px solid #eef0f5',
            borderRadius: 8,
            padding: '10px 14px',
            display: 'flex',
            flexWrap: 'wrap',
            gap: '6px 20px',
          }}
        >
          {pageHeader.map((code) => {
            const f = fieldMap.get(code);
            return (
              <span key={code} style={{ fontSize: 13 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {f?.displayName ?? code}：
                </Text>
                <Text strong>{detailValueOf(f, record[code])}</Text>
              </span>
            );
          })}
        </div>
      )}
      {sections.map((sec, si) => (
        <div key={sec.id ?? si}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <Text strong>{sec.title ?? sec.id}</Text>
            {sec.kind && sec.kind !== 'form' && (
              <Tag style={{ fontSize: 11, marginInlineEnd: 0 }}>{sec.kind}</Tag>
            )}
          </div>
          <Descriptions
            size="small"
            column={1}
            bordered
            styles={{ label: { width: 132 } }}
            items={(sec.controls ?? []).map((c, ci) => {
              const f = fieldMap.get(c.fieldCode);
              return {
                key: `${c.fieldCode}-${ci}`,
                label: c.labelOverride || f?.displayName || c.fieldCode,
                children: detailValueOf(f, record[c.fieldCode]),
              };
            })}
          />
        </div>
      ))}
    </Space>
  );
}

export default RuntimeDetailView;
