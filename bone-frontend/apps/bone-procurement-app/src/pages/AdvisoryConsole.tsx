import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Select, Space, Typography } from 'antd';
import { advisoryApi } from '../services/advisoryApi';
import {
  ADVISORY_MANUAL_NOTICE,
  adviceErrorMessage,
  adviceText,
  type AdvisoryAdviceDTO,
} from '../types/advisoryView';

/** AI 建议态工作台：只展示真实后端建议态与降级事实，不把建议误读为自动决策许可。 */
const AdvisoryConsole: React.FC = () => {
  const [capabilities, setCapabilities] = useState<string[]>([]);
  const [selected, setSelected] = useState<string>('');
  const [advice, setAdvice] = useState<AdvisoryAdviceDTO | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const loadCapabilities = useCallback(() => {
    let active = true;
    setLoading(true);
    setError('');
    advisoryApi
      .listCapabilities()
      .then((resp) => {
        if (!active) return;
        const codes = resp.data ?? [];
        setCapabilities(codes);
        setSelected((current) => (codes.includes(current) ? current : codes[0] ?? ''));
        if (codes.length === 0) {
          setAdvice(null);
          setError('无可确认的能力登记');
        }
      })
      .catch((err: unknown) => {
        if (active) {
          setCapabilities([]);
          setSelected('');
          setAdvice(null);
          setError(adviceErrorMessage(err));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const loadAdvice = useCallback((code: string) => {
    if (!code) return;
    let active = true;
    setError('');
    advisoryApi
      .getAdvice(code)
      .then((resp) => {
        if (active) setAdvice(resp.data ?? null);
      })
      .catch((err: unknown) => {
        if (active) {
          setAdvice(null);
          setError(adviceErrorMessage(err));
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => loadCapabilities(), [loadCapabilities]);
  useEffect(() => loadAdvice(selected), [loadAdvice, selected]);

  return (
    <Card
      title="AI 建议态工作台"
      extra={
        <Button onClick={loadCapabilities} loading={loading}>
          刷新
        </Button>
      }
      loading={loading}
    >
      <Alert type="info" showIcon message={ADVISORY_MANUAL_NOTICE} />
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Space>
          <span>能力码</span>
          <Select
            aria-label="能力码"
            value={selected || undefined}
            onChange={setSelected}
            style={{ minWidth: 260 }}
            options={capabilities.map((code) => ({ value: code, label: code }))}
          />
        </Space>
        {error && <Alert type="warning" message={error} showIcon />}
        {advice && !error && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="能力名称">{advice.displayName}</Descriptions.Item>
            <Descriptions.Item label="AI 参与态">
              {adviceText(advice.aiAvailable)}（原始码：{advice.aiAvailable}）
            </Descriptions.Item>
            <Descriptions.Item label="降级路径">{advice.degradedTo ?? '无'}</Descriptions.Item>
            <Descriptions.Item label="原因码">{advice.reasonCode}</Descriptions.Item>
            <Descriptions.Item label="显式标识">{advice.explicitLabel ?? '无'}</Descriptions.Item>
            <Descriptions.Item label="隐式标识">{advice.implicitMetadata ?? '无'}</Descriptions.Item>
            <Descriptions.Item label="证据引用">{advice.evidenceRef ?? '无'}</Descriptions.Item>
            <Descriptions.Item label="后端披露">
              {advice.disclosure || ADVISORY_MANUAL_NOTICE}
            </Descriptions.Item>
          </Descriptions>
        )}
        <Typography.Text type="secondary">
          AVAILABLE 只代表工程护栏放行后的建议态；评测未转正、效果未声明，任何采购裁决仍由人工终决。
        </Typography.Text>
      </Space>
    </Card>
  );
};

export default AdvisoryConsole;
