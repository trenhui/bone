import React, { useState } from 'react';
import { Card, Table, InputNumber, Button, Space, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { DataQualityResult } from '@/types';
import { qualityResultApi } from '@/services/api';

const QualityResult: React.FC = () => {
  const [recordId, setRecordId] = useState<number | null>(null);
  const [results, setResults] = useState<DataQualityResult[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchResults = async () => {
    if (!recordId) return;
    setLoading(true);
    try {
      const res = await qualityResultApi.listByRecordId(recordId);
      setResults(res.data ?? []);
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 100 },
    { title: '记录 ID', dataIndex: 'masterDataRecordId', width: 120 },
    { title: '规则 ID', dataIndex: 'dataQualityRuleId', width: 120 },
    {
      title: '是否通过',
      dataIndex: 'passed',
      width: 100,
      render: (v: boolean) => (v ? <Tag color="green">通过</Tag> : <Tag color="red">未通过</Tag>),
    },
    { title: '消息', dataIndex: 'message' },
    { title: '时间', dataIndex: 'timestamp', width: 180 },
  ];

  return (
    <Card title="质量检查结果">
      <Space style={{ marginBottom: 16 }}>
        <InputNumber
          placeholder="输入记录 ID"
          value={recordId}
          onChange={(v) => setRecordId(v ?? null)}
          style={{ width: 200 }}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={fetchResults} loading={loading}>
          查询
        </Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={results}
        loading={loading}
        locale={{ emptyText: '请输入记录 ID 查询质量结果' }}
        scroll={{ x: 900 }}
      />
      {results.length === 0 && (
        <Typography.Text type="secondary">暂无质量结果，输入记录 ID 后点击查询。</Typography.Text>
      )}
    </Card>
  );
};

export default QualityResult;
