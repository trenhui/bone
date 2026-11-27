import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import { sourcingApi, type AwardDTO, type QuotationComparisonDTO } from '../services/sourcingApi';
import {
  awardConfirmedByText,
  bidStatusTextOrRaw,
  toQuotationRows,
} from '../types/sourcingView';

/**
 * 寻源工作台：RFx 授标记录与报价比较只读呈现 + 具名人工授标入口。
 * fail-closed：取数失败/能力未启用不显示成功；AI 建议只作建议，不自动授标。
 */
const SourcingConsole: React.FC = () => {
  const [award, setAward] = useState<AwardDTO | null>(null);
  const [awardDegraded, setAwardDegraded] = useState<string | null>('无法确认');
  const [quotes, setQuotes] = useState<QuotationComparisonDTO[]>([]);
  const [quoteDegraded, setQuoteDegraded] = useState<string | null>('无法确认');

  useEffect(() => {
    let alive = true;
    sourcingApi.getAward('RFX-DEMO').then((r) => {
      if (!alive) return;
      setAward(r.data);
      setAwardDegraded(r.success ? null : r.degradedReason);
    });
    sourcingApi.listQuotations('RFX-DEMO', 'CNY').then((r) => {
      if (!alive) return;
      setQuotes(r.data ?? []);
      setQuoteDegraded(r.success ? null : r.degradedReason);
    });
    return () => {
      alive = false;
    };
  }, []);

  const onAward = async () => {
    const r = await sourcingApi.award('RFX-DEMO', {
      supplierId: award?.supplierId ?? '',
      reason: '人工复核确认',
    });
    if (!r.success) message.error(r.degradedReason ?? '无法确认');
    else message.success('授标已记录');
  };

  const rows = toQuotationRows(quotes);

  return (
    <div>
      <Typography.Title level={3}>寻源工作台</Typography.Title>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Card title="授标记录">
          {awardDegraded ? (
            <Alert type="warning" message="无法确认" description={awardDegraded} />
          ) : (
            <div>
              <div>供应商：{award?.supplierId ?? '无法确认'}</div>
              <div>确认人：{awardConfirmedByText(award?.confirmedBy ?? null)}</div>
              <div>投标状态：{bidStatusTextOrRaw('SUBMITTED')}</div>
              <Button onClick={onAward}>人工授标</Button>
            </div>
          )}
        </Card>
        <Card title="报价比较">
          {quoteDegraded ? (
            <Alert type="warning" message="无法确认" description={quoteDegraded} />
          ) : (
            <>
              <Table
                rowKey="quoteId"
                dataSource={rows.valid}
                columns={[
                  { title: '报价', dataIndex: 'amountText' },
                  {
                    title: 'AI 建议',
                    dataIndex: 'aiSuggestion',
                    render: (v: unknown) => (v ? <Tag color="blue">建议：{String(v)}</Tag> : '—'),
                  },
                ]}
              />
              {rows.invalid.length > 0 ? (
                <Alert
                  type="info"
                  message="不可比报价"
                  description={rows.invalid.map((r) => r.reasonText).join('；')}
                />
              ) : null}
            </>
          )}
        </Card>
      </Space>
    </div>
  );
};

export default SourcingConsole;
