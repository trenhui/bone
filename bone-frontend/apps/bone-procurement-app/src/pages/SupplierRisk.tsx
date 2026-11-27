import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Table, Typography, message } from 'antd';
import { supplierRiskApi } from '../services/supplierRiskApi';
import {
  FETCH_FAILED_TEXT,
  severityTextOrRaw,
  toPerformanceRows,
} from '../types/supplierRiskView';

/**
 * 供应商风险与绩效看板：预警处置与绩效只读呈现。
 * fail-closed：取数失败显示「无法确认」而非「无风险」；未知严重度保留原始码并转人工。
 */
const SupplierRisk: React.FC = () => {
  const [degraded, setDegraded] = useState<string | null>('无法确认');
  const [scores, setScores] = useState<ReturnType<typeof toPerformanceRows>>([]);

  useEffect(() => {
    let alive = true;
    supplierRiskApi.getPerformance('').then((r) => {
      if (!alive) return;
      if (r.success) {
        setScores(toPerformanceRows(r.data));
        setDegraded(null);
      } else {
        setScores([]);
        setDegraded(FETCH_FAILED_TEXT);
      }
    });
    return () => {
      alive = false;
    };
  }, []);

  const onEscalate = async () => {
    const r = await supplierRiskApi.disposeAlert({ alertId: 'A-DEMO', decision: '人工升级复核' });
    if (!r.success) message.error(r.degradedReason ?? '无法确认');
    else message.success('预警已转人工');
  };

  return (
    <div>
      <Typography.Title level={3}>供应商风险与绩效</Typography.Title>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Card title="预警处置">
          <Alert
            type="info"
            message="严重度未知时转人工"
            description={`当前严重度：${severityTextOrRaw(null)}`}
          />
          <Button onClick={onEscalate}>转人工复核</Button>
        </Card>
        <Card title="绩效看板（只读）">
          {degraded ? (
            <Alert type="warning" message="无法确认" description={degraded} />
          ) : (
            <Table
              rowKey="supplierId"
              dataSource={scores}
              columns={[
                { title: '供应商', dataIndex: 'supplierId' },
                { title: '评分', dataIndex: 'scoreText' },
                {
                  title: '等级',
                  dataIndex: 'gradeText',
                  render: (v: unknown) => {
                    const text = String(v ?? '');
                    return /^无法确认|原始码/.test(text) ? <span>需人工确认：{text}</span> : text;
                  },
                },
              ]}
            />
          )}
        </Card>
      </Space>
    </div>
  );
};

export default SupplierRisk;
