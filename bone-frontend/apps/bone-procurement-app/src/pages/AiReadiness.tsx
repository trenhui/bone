import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from 'antd';
import { readinessApi } from '../services/readinessApi';
import {
  readinessErrorMessage,
  readinessText,
  verdictColor,
  verdictTextOrRaw,
  type AiReadinessDTO,
} from '../types/readinessView';

/** AI 就绪度只读页：如实展示配置/治理/总闸/评测四类事实，未确认不冒充可用。 */
const AiReadiness: React.FC = () => {
  const [data, setData] = useState<AiReadinessDTO | null>(null);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    let active = true;
    setLoading(true);
    setError('');
    readinessApi
      .getReadiness()
      .then((resp) => {
        if (active) setData(resp.data ?? null);
      })
      .catch(() => {
        if (active) {
          setData(null);
          setError(readinessErrorMessage());
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => load(), [load]);

  return (
    <Card title="采购 AI 就绪度" extra={<Button onClick={load} loading={loading}>刷新</Button>} loading={loading}>
      {error && <Alert type="warning" message={error} showIcon />}
      {data && !error && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Typography.Paragraph>
            当前判词：
            <Tag color={verdictColor(data.verdict)}>{verdictTextOrRaw(data.verdict)}</Tag>
            <span>（{readinessText(data.verdict)}）</span>
          </Typography.Paragraph>
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="真实 provider 已配置">
              {data.providerConfigured ? '是' : '否（当前为 Mock）'}
            </Descriptions.Item>
            <Descriptions.Item label="治理能力已启用">
              {data.governanceEnabled ? '是' : '否'}
            </Descriptions.Item>
            <Descriptions.Item label="全域总闸生效">
              {data.killSwitchEngaged ? '是（AI 已停用）' : '否'}
            </Descriptions.Item>
            <Descriptions.Item label="评测通过线已冻结">
              {data.evalFrozen ? '是' : '否（评估集未转正，不声明效果达标）'}
            </Descriptions.Item>
            <Descriptions.Item label="部署门禁通过">
              {data.deploymentGatesPassed ? '是' : '否'}
            </Descriptions.Item>
            <Descriptions.Item label="原因码">
              {data.reasons?.length ? data.reasons.join('、') : '无'}
            </Descriptions.Item>
          </Descriptions>
          <Alert type="info" showIcon message={data.authorizationNotice} />
        </Space>
      )}
    </Card>
  );
};

export default AiReadiness;
