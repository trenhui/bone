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
 *
 * **N7 字段级角色可见性（本组件曾是唯一绕过同源纪律的渲染面）**：
 * 本组件此前不接ctx、不 import fieldVisible，直接把控件全量渲染出来。于是
 * 建模者给薪酬类字段配了 `props.roles: ['hr']`，表单里确实按角色隐藏了，
 * **但详情抽屉把该字段值原样显示给所有人**——前端渲染型「权限」最危险的失效形态：
 * 界面看起来做了权限，实际数据仍然外泄。现统一走
 * `controlBaseStates` → `fieldVisible`，与表单/预览同源（判定逻辑只有一份）。
 *
 * 注意与后端的边界：前端隐藏是**体验层**，不是安全边界。真要防「看到就截获」，
 * 仍需服务端在列级裁剪（当前 RowPolicySpec 只做行级 WHERE，无列级裁剪能力）。
 * 这里保证的是「配了 roles 就真的处处生效」，不谎称已做到数据层隔离。
 */
import { Descriptions, Empty, Space, Tag, Typography } from 'antd';
import { parseEnumValues } from '@bone/shared-types';
import type { MetaField } from '../types';
import type { Control, FormView, UiContext } from '../utils/uiSchemaEngine';
import { controlBaseStates, fieldVisible } from '../utils/uiSchemaEngine';

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

/** 页头与分区体是否允许展示某字段（与表单/预览同源：controlBaseStates + fieldVisible） */
function visibleControls(
  controls: Control[],
  visibleByField: Record<string, boolean>,
): Control[] {
  return (controls ?? []).filter((c) => visibleByField[c.fieldCode] !== false);
}

export function RuntimeDetailView({
  detail,
  fields,
  record,
  ctx = {},
}: {
  detail: FormView;
  fields: MetaField[];
  record: Record<string, unknown>;
  /** 运行时身份（UiContext.roles 决定 props.roles 字段是否展示）；缺省 = 空ctx */
  ctx?: UiContext;
}): React.ReactElement {
  const fieldMap = new Map(fields.map((f) => [f.code, f]));

  // 字段级可见性：与表单/预览走同一判定（唯一实现 fieldVisible，勿在本组件另写一套）。
  // 详情视图无联动规则，故只需把静态基线态（control.visible / props.roles / props.tier）
  // 交给 fieldVisible；未声明的字段按「可见」处理（与表单缺省态一致）。
  //
  // 口径：**门禁来源是分区控件的声明**。pageHeader 只是引用字段码，复用同一份
  // visibleByField——分区声明 roles 的字段被页头条引用时同样受限（页头值横排在顶部，
  // 漏判等于把受限数据摆在最显眼处）。
  //
  // 曾试过额外遍历 pageHeader 收集：变异验证证明那是**死代码**（分区遍历已把同字段算入
  // out；而只在页头条出现、分区未声明的字段本就无人声明 roles ⇒ 不限制）。故不保留。
  const visibleByField = (() => {
    const base = controlBaseStates(detail.sections ?? []);
    const out: Record<string, boolean> = {};
    for (const s of detail.sections ?? []) {
      for (const c of s.controls ?? []) {
        if (!c.fieldCode) continue;
        const st = { ...(base[c.fieldCode] ?? {}) };
        out[c.fieldCode] = fieldVisible(
          { visible: st.visible !== false, required: false, readonly: !!st.readonly, disabled: false, notice: '', ...st },
          ctx,
        );
      }
    }
    return out;
  })();

  // 过滤后再判空分区：授权角色看不到该分区全部字段时，连分区标题都不该出现
  // （否则「标题在、内容空」会暴露「这里有个受限分区」的存在性）
  const sections = (detail.sections ?? [])
    .map((s) => ({ ...s, controls: visibleControls(s.controls, visibleByField) }))
    .filter((s) => s.controls.length > 0);
  const pageHeader = (detail.pageHeader ?? []).filter((code) => visibleByField[code] !== false);
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
