/**
 * 发布变更预告弹层（第五轮：上线前用业务语言看清「这次发布会改变什么」）。
 *
 * 分层纪律：结构化 diff 在 `utils/uiSchemaChangePreview.ts`（纯函数、可单测），
 * 本组件只负责「结构化项 → 人话句子」的渲染；规则类句子复用 describe.tsx
 * （联动/提交校验/动作条同一套组句真源），不另写文案。
 *
 * 对标：GitHub PR summary / Figma 版本对比 / 飞书「草稿已启用」提示——
 * 共识是「变更可见性 + 业务语言摘要」，而非字段/DDL 结构视角。
 *
 * v1.2 补齐：①超组上限可「展开全部」（此前硬截断 + 「保存后逐项核对」= 转嫁成本）；
 * ②条目可点击定位到配置面板对应位置（与 LintPanel onLocate 同路径，
 * 对标 Figma 版本对比逐条可定位）。
 */
import { useState } from 'react';
import { Empty, Space, Tag, Typography } from 'antd';
import { EditOutlined, MinusCircleOutlined, PlusCircleOutlined } from '@ant-design/icons';
import { describeAction, describeLinkage, describeSubmit } from './describe';
import type { LintLocate } from './lint';
import type { ChangeGroup, ChangeItem, ChangePreview } from '../../utils/uiSchemaChangePreview';

const { Text } = Typography;

/** 每组默认展示的条目数（超出折叠计数，避免预告清单淹没弹层；可展开） */
const PER_GROUP_CAP = 6;

const KIND_ICON = {
  add: <PlusCircleOutlined style={{ color: '#52c41a' }} />,
  remove: <MinusCircleOutlined style={{ color: '#ff4d4f' }} />,
  modify: <EditOutlined style={{ color: '#faad14' }} />,
};

/** 规则类变更项 → 自然语言句子（复用 describe.tsx 单一真源） */
function ruleSentence(item: ChangeItem): React.ReactNode {
  if (item.group === 'linkage' && item.rule) return describeLinkage(item.rule as Parameters<typeof describeLinkage>[0]);
  if (item.group === 'submit' && item.rule) return describeSubmit(item.rule as Parameters<typeof describeSubmit>[0]);
  if (item.group === 'action' && item.rule) return describeAction(item.rule as Parameters<typeof describeAction>[0]);
  return item.text ?? '（无描述）';
}

function GroupBlock({ group, onLocate }: { group: ChangeGroup; onLocate?: (l: LintLocate) => void }) {
  const [expanded, setExpanded] = useState(false);
  const adds = group.items.filter((i) => i.kind === 'add').length;
  const removes = group.items.filter((i) => i.kind === 'remove').length;
  const modifies = group.items.filter((i) => i.kind === 'modify').length;
  const shown = expanded ? group.items : group.items.slice(0, PER_GROUP_CAP);
  return (
    <div style={{ marginBottom: 10 }}>
      <Space size={4} wrap style={{ marginBottom: 4 }}>
        <Text strong style={{ fontSize: 13 }}>{group.label}</Text>
        {adds > 0 && <Tag color="green" style={{ margin: 0, fontSize: 11 }}>+{adds}</Tag>}
        {removes > 0 && <Tag color="red" style={{ margin: 0, fontSize: 11 }}>-{removes}</Tag>}
        {modifies > 0 && <Tag color="gold" style={{ margin: 0, fontSize: 11 }}>~{modifies}</Tag>}
      </Space>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        {shown.map((item, i) => {
          const clickable = !!item.locate && !!onLocate;
          return (
            <div
              key={`${item.kind}:${item.key}:${i}`}
              onClick={clickable ? () => onLocate?.(item.locate as LintLocate) : undefined}
              title={clickable ? '点击定位到配置面板对应位置' : undefined}
              style={{
                display: 'flex', gap: 6, fontSize: 12, lineHeight: 1.6,
                cursor: clickable ? 'pointer' : undefined,
                borderRadius: 4, padding: clickable ? '1px 4px' : undefined,
                margin: clickable ? '0 -4px' : undefined,
              }}
              onMouseEnter={(e) => { if (clickable) e.currentTarget.style.background = '#f0f7ff'; }}
              onMouseLeave={(e) => { if (clickable) e.currentTarget.style.background = 'transparent'; }}
            >
              <span style={{ flex: '0 0 auto', marginTop: 2 }}>{KIND_ICON[item.kind]}</span>
              <span>{item.rule ? ruleSentence(item) : item.text}</span>
            </div>
          );
        })}
        {group.items.length > PER_GROUP_CAP && (
          <Typography.Link style={{ fontSize: 11 }} onClick={() => setExpanded((v) => !v)}>
            {expanded ? '收起' : `展开全部 ${group.items.length} 条`}
          </Typography.Link>
        )}
      </div>
    </div>
  );
}

export default function ChangePreviewPanel({
  preview, mode, onLocate,
}: {
  preview: ChangePreview;
  /** pending=发布后将改变（草稿 vs 线上）；first=首次发布将创建（无线上快照） */
  mode: 'pending' | 'first';
  /** 点击条目定位到配置面板（与 LintPanel 同路径） */
  onLocate?: (l: LintLocate) => void;
}): React.ReactElement {
  if (preview.total === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="线上发布版与已存草稿一致，无待发布变更" />;
  }
  return (
    <div style={{ maxWidth: 460, maxHeight: '52vh', overflow: 'auto' }}>
      <div style={{ marginBottom: 10, fontSize: 12 }}>
        <Text type="secondary">{mode === 'first' ? '首次发布将创建：' : '发布后将改变：'}</Text>
        <Text strong>{preview.summary}</Text>
      </div>
      {preview.groups.map((g) => <GroupBlock key={g.key} group={g} onLocate={onLocate} />)}
      <Text type="secondary" style={{ fontSize: 11 }}>
        {mode === 'first'
          ? '以上为当前配置的全量内容；发布后对终端用户生效。带下划线反应的条目可点击定位。'
          : '对照基线为线上发布快照（后端 published_schema_json）；发布后终端用户立即看到以上变化。鼠标悬停高亮的条目可点击定位。'}
      </Text>
    </div>
  );
}
