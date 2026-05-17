import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Card,
  Col,
  Descriptions,
  Row,
  Select,
  Spin,
  Statistic,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  getSandboxConfig,
  listExecutionLogs,
  type ExecutionLogRow,
  type SandboxConfig,
} from '@/services/extensionApi';

const statusColor: Record<string, string> = {
  SUCCESS: 'green',
  FAILED: 'red',
  RUNNING: 'processing',
  PENDING: 'default',
};

const SandboxManagement: React.FC = () => {
  const [config, setConfig] = useState<SandboxConfig | null>(null);
  const [logs, setLogs] = useState<ExecutionLogRow[]>([]);
  const [logTotal, setLogTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [loading, setLoading] = useState(true);
  const [logLoading, setLogLoading] = useState(false);

  const loadConfig = useCallback(async () => {
    setConfig(await getSandboxConfig());
  }, []);

  const loadLogs = useCallback(async (status?: string) => {
    setLogLoading(true);
    try {
      const page = await listExecutionLogs({ status, page: 1, size: 20 });
      setLogs(page.list);
      setLogTotal(page.total);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载执行日志失败');
    } finally {
      setLogLoading(false);
    }
  }, []);

  useEffect(() => {
    (async () => {
      try {
        await Promise.all([loadConfig(), loadLogs()]);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载沙箱数据失败');
      } finally {
        setLoading(false);
      }
    })();
  }, [loadConfig, loadLogs]);

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

  if (loading) {
    return <Spin />;
  }

  return (
    <Row gutter={[16, 16]}>
      <Col span={24}>
        <Alert
          type="info"
          showIcon
          message="Wasm 沙箱运行时"
          description="完整 WasmEdge 隔离执行在引擎层规划中；当前 Studio 通过运行时元数据同步完成路由，并在本页展示执行日志与资源策略。"
        />
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic title="扩展点" value={config?.extPointCount ?? 0} />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic title="插件" value={config?.pluginCount ?? 0} />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic
            title="执行成功率(%)"
            value={config?.successRate ?? 100}
            precision={1}
            valueStyle={{ color: (config?.successRate ?? 100) >= 95 ? '#3f8600' : '#cf1322' }}
          />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic title="执行总数" value={config?.executionTotal ?? 0} />
        </Card>
      </Col>
      <Col xs={24} md={8}>
        <Card>
          <Statistic title="内存上限 (MB)" value={config?.maxMemoryMb ?? 512} />
        </Card>
      </Col>
      <Col xs={24} md={8}>
        <Card>
          <Statistic title="CPU 上限 (核)" value={config?.maxCpuCores ?? 0.5} precision={1} />
        </Card>
      </Col>
      <Col xs={24} md={8}>
        <Card>
          <Statistic title="执行超时 (秒)" value={config?.timeoutSeconds ?? 30} />
        </Card>
      </Col>
      <Col span={24}>
        <Card title="沙箱策略">
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="运行时">{config?.runtime ?? 'WasmEdge'}</Descriptions.Item>
            <Descriptions.Item label="元数据同步">
              <Tag color={config?.syncEnabled ? 'green' : 'default'}>
                {config?.syncEnabled ? '已启用' : '未启用'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="成功 / 失败">
              {config?.executionSuccess ?? 0} / {config?.executionFailed ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label="隔离级别">进程内元数据路由 + 计划中的 Wasm 字节码沙箱</Descriptions.Item>
          </Descriptions>
        </Card>
      </Col>
      <Col span={24}>
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
                loadLogs(v);
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
            loading={logLoading}
            columns={logColumns}
            dataSource={logs}
            pagination={{ total: logTotal, pageSize: 20, showSizeChanger: false }}
          />
        </Card>
      </Col>
    </Row>
  );
};

export default SandboxManagement;
