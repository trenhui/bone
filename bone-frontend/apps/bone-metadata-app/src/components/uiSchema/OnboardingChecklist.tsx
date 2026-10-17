/**
 * 0-1 新手指引进度卡（对标 Notion / Linear / Salesforce Guided Setup 的 onboarding checklist）。
 *
 * 与 Spotlight 引导的分工：
 * - 引导（DesignerTour）负责「第一次有人带」——六步走完即消失；
 * - 进度卡负责「长期指南针」——每次打开设计器都能看到 0-1 还差哪几步，点一行直达对应面板。
 *   里程碑判定全部来自 utils/onboarding 纯函数（schema 真状态），不是点击计数——
 *   用户绕开引导直接配，进度同样亮（完成态由配置推定，不由路径推定）。
 *
 * 可关闭（按实体 localStorage 记忆）；五项全亮时收起为庆祝条，不再占版面。
 */
import { useState } from 'react';
import { Button, Progress, Tooltip, Typography } from 'antd';
import { CheckCircleFilled, CloseOutlined, DownOutlined, RocketOutlined, UpOutlined } from '@ant-design/icons';
import type { OnboardingMilestone } from '../../utils/onboarding';

const { Text } = Typography;

interface Props {
  milestones: OnboardingMilestone[];
  onNavigate: (tab: 'structure' | 'list' | 'rules' | 'actions' | 'variants' | 'lint') => void;
  /** publish 里程碑的直达（设计器内无发布面板，由实体详情页签跳转提供） */
  onGoPublish?: () => void;
  onDismiss: () => void;
}

export default function OnboardingChecklist({ milestones, onNavigate, onGoPublish, onDismiss }: Props): React.ReactElement | null {
  const [expanded, setExpanded] = useState(false);
  const doneCount = milestones.filter((m) => m.done).length;
  const allDone = doneCount === milestones.length;

  if (allDone) {
    return (
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', marginBottom: 8,
        background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 8,
      }}>
        <span style={{ fontSize: 14 }}>🎉</span>
        <Text style={{ fontSize: 12, color: '#389e0d', flex: 1 }}>
          0-1 上手完成：表单 / 列表 / 规则 / 自检 / 发布全部就绪
        </Text>
        <Tooltip title="关闭进度卡（仍可从工具栏「帮助」重看引导）">
          <Button type="text" size="small" icon={<CloseOutlined />} aria-label="关闭进度卡" onClick={onDismiss} />
        </Tooltip>
      </div>
    );
  }

  if (!expanded) {
    return (
      <div
        role="button" tabIndex={0}
        aria-label="展开 0-1 新手指引进度卡"
        onClick={() => setExpanded(true)}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setExpanded(true); }}
        style={{
          display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', marginBottom: 8,
          background: '#fff', border: '1px solid #e6eaf0', borderRadius: 8, cursor: 'pointer',
        }}
      >
        <RocketOutlined style={{ color: '#1677ff' }} />
        <Text style={{ fontSize: 12, fontWeight: 600 }}>0-1 新手指引</Text>
        <Text type="secondary" style={{ fontSize: 11 }}>{doneCount}/{milestones.length} 步</Text>
        <div style={{ flex: 1, maxWidth: 160 }}>
          <Progress percent={Math.round((doneCount / milestones.length) * 100)} showInfo={false} size="small" strokeColor="#1677ff" />
        </div>
        <DownOutlined style={{ color: '#bfbfbf', fontSize: 10 }} />
        <Tooltip title="关闭进度卡（按实体记忆；五项全亮后自动收起为庆祝条）">
          <Button
            type="text" size="small" icon={<CloseOutlined />} aria-label="关闭进度卡"
            onClick={(e) => { e.stopPropagation(); onDismiss(); }}
          />
        </Tooltip>
      </div>
    );
  }

  return (
    <div style={{
      padding: '10px 12px', marginBottom: 8, background: '#fff',
      border: '1px solid #e6eaf0', borderRadius: 10,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
        <RocketOutlined style={{ color: '#1677ff', marginRight: 6 }} />
        <Text strong style={{ fontSize: 13 }}>0-1 上手指引</Text>
        <Text type="secondary" style={{ fontSize: 11, marginLeft: 8 }}>{doneCount}/{milestones.length}</Text>
        <span style={{ flex: 1 }} />
        <Button type="text" size="small" icon={<UpOutlined />} aria-label="收起进度卡" onClick={() => setExpanded(false)} />
        <Button type="text" size="small" icon={<CloseOutlined />} aria-label="关闭进度卡" onClick={onDismiss} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        {milestones.map((m) => (
          <div
            key={m.key}
            style={{
              display: 'flex', alignItems: 'flex-start', gap: 8, padding: '6px 8px', borderRadius: 8,
              background: m.done ? '#f6ffed' : '#fafbfd',
            }}
          >
            {m.done
              ? <CheckCircleFilled style={{ color: '#52c41a', marginTop: 2 }} />
              : <span style={{
                  width: 14, height: 14, marginTop: 2, borderRadius: '50%',
                  border: '1.5px solid #d9d9d9', display: 'inline-block', flex: '0 0 auto',
                }} />}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12, fontWeight: m.done ? 400 : 600, textDecoration: m.done ? 'line-through' : undefined, color: m.done ? '#00000073' : undefined }}>
                {m.label}
              </div>
              <div style={{ fontSize: 11, color: '#00000066', lineHeight: 1.5 }}>{m.hint}</div>
            </div>
            {!m.done && m.key === 'publish' && onGoPublish && (
              <Button size="small" type="link" onClick={onGoPublish} style={{ flex: '0 0 auto' }}>去发布 →</Button>
            )}
            {!m.done && m.key !== 'publish' && m.tab && (
              <Button size="small" type="link" onClick={() => onNavigate(m.tab!)} style={{ flex: '0 0 auto' }}>去做 →</Button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
