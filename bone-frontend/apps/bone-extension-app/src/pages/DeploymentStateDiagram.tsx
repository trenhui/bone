import React from 'react';
import { Card, Empty, Space, Tag, Tooltip } from 'antd';
import type { DeploymentStateView } from '@/services/extensionApi';

const STATE_COLORS: Record<string, string> = {
  UPLOADED: 'blue',
  VALIDATED: 'cyan',
  REJECTED: 'red',
  STAGED: 'gold',
  ACTIVE: 'green',
  DEPRECATED: 'default',
};

const STATE_TITLES: Record<string, string> = {
  UPLOADED: '已上传',
  VALIDATED: '已校验',
  REJECTED: '已拒绝',
  STAGED: '已就绪',
  ACTIVE: '生效中',
  DEPRECATED: '已弃用',
};

interface DeploymentStateDiagramProps {
  state?: DeploymentStateView | null;
}

const DeploymentStateDiagram: React.FC<DeploymentStateDiagramProps> = ({ state }) => {
  if (!state) {
    return <Empty description="无部署状态" />;
  }
  const current = state.currentStatus;
  return (
    <Card size="small" title="部署状态机（详设 §3.3）">
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap>
          {state.allStates.map((s) => {
            const isCurrent = current === s;
            return (
              <Tooltip key={s} title={STATE_TITLES[s] ?? s}>
                <Tag
                  color={STATE_COLORS[s] ?? 'default'}
                  style={{
                    fontSize: 13,
                    padding: '4px 12px',
                    border: isCurrent ? '2px solid #1677ff' : undefined,
                    fontWeight: isCurrent ? 600 : 400,
                  }}
                >
                  {STATE_TITLES[s] ?? s}
                  {isCurrent ? '（当前）' : ''}
                </Tag>
              </Tooltip>
            );
          })}
        </Space>
        <div style={{ fontSize: 12, color: '#666' }}>
          允许迁移：
          {state.transitions.map((t, idx) => (
            <span key={`${t.from}-${t.to}-${idx}`} style={{ marginRight: 12 }}>
              <Tag>{STATE_TITLES[t.from] ?? t.from}</Tag>
              <span>→</span>
              <Tag color={STATE_COLORS[t.to] ?? 'default'}>{STATE_TITLES[t.to] ?? t.to}</Tag>
            </span>
          ))}
        </div>
      </Space>
    </Card>
  );
};

export default DeploymentStateDiagram;
