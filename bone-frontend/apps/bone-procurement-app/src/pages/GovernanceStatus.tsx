import React, { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Tag, Typography } from 'antd';
import { readinessApi } from '../services/readinessApi';
import { readinessText, verdictColor, verdictTextOrRaw, type AiReadinessDTO } from '../types/readinessView';

/** AI 治理状态只读页：从就绪度事实展示总闸与治理启用态，不触发任何写操作。 */
const GovernanceStatus: React.FC = () => {
  const [data, setData] = useState<AiReadinessDTO | null>(null);
  const [error, setError] = useState<string>('');

  useEffect(() => {
    let active = true;
    readinessApi
      .getReadiness()
      .then((resp) => {
        if (active) setData(resp.data ?? null);
      })
      .catch(() => {
        if (active) {
          setData(null);
          setError('无法确认');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <Card title="AI 治理状态">
      {error && <Alert type="warning" message={error} showIcon />}
      {data && !error && (
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="就绪度判词">
            <Tag color={verdictColor(data.verdict)}>{verdictTextOrRaw(data.verdict)}</Tag>
            <span>（{readinessText(data.verdict)}）</span>
          </Descriptions.Item>
          <Descriptions.Item label="治理能力">
            {data.governanceEnabled ? <Tag color="blue">已启用</Tag> : <Tag>未启用</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="全域总闸">
            {data.killSwitchEngaged ? <Tag color="red">已停用 AI</Tag> : <Tag color="green">正常运行</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="说明">
            <Typography.Text type="secondary">{data.authorizationNotice}</Typography.Text>
          </Descriptions.Item>
        </Descriptions>
      )}
    </Card>
  );
};

export default GovernanceStatus;
