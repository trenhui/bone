import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Select, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  formatStudioError,
  listExecutionLogs,
  type ExecutionLogRow,
} from '@/services/extensionApi';

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  RUNNING: 'processing',
  PENDING: 'default',
};

/**
 * 运行日志独立页面。
 * 从 SandboxManagement 抽出的日志查询逻辑在此集中定义，避免与沙箱页重复实现。
 */
const ExecutionLogPage: React.FC = () => {
  const [logs, setLogs] = useState<ExecutionLogRow[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const loadLogs = useCallback(
    async (status?: string, append = false, cursor?: string | null) => {
      setLoading(true);
      try {
        const page = await listExecutionLogs({
          status,
          limit: 20,
          cursor: append && cursor ? cursor : undefined,
        });
        setLogs((prev) => (append ? [...prev, ...page.rows] : page.rows));
        setNextCursor(page.nextCursor ?? null);
      } catch (e) {
        message.error(formatStudioError(e, '加载执行日志失败'));
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    loadLogs().catch((e) => message.error(formatStudioError(e, '加载执行日志失败')));
  }, [loadLogs]);

  const logColumns: ColumnsType<ExecutionLogRow> = [
    { title: '执行ID', dataIndex: 'executionId', width: 120, ellipsis: true },
    { title: '插件ID', dataIndex: 'pluginId', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (s: string) => <Tag color={statusColor[s] ?? 'default'}>{s}</Tag>,
    },
    { title: '耗时(ms)', dataIndex: 'durationMs', width: 90 },
    { title: '错误', dataIndex: 'errorMessage', ellipsis: true },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  return (
    <Card
      title="插件执行日志"
      extra={
        <Select
          allowClear
          placeholder="按状态筛选"
          style={{ width: 140 }}
          value={statusFilter}
          onChange={(v) => {
            setStatusFilter(v);
            loadLogs(v, false);
          }}
          options={[
            { value: 'SUCCESS', label: 'SUCCESS' },
            { value: 'FAILED', label: 'FAILED' },
            { value: 'RUNNING', label: 'RUNNING' },
          ]}
        />
      }
    >
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        columns={logColumns}
        dataSource={logs}
        pagination={false}
      />
      {nextCursor ? (
        <div style={{ marginTop: 12, textAlign: 'center' }}>
          <Button loading={loading} onClick={() => loadLogs(statusFilter, true, nextCursor)}>
            加载更多
          </Button>
        </div>
      ) : null}
    </Card>
  );
};

export default ExecutionLogPage;
