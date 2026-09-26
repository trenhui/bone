import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Spin,
  Statistic,
  Table,
  Tag,
  message,
} from 'antd';
import { formatDate } from '@bone/shared-utils';
import {
  formatStudioError,
  getSandboxConfig,
  listAuditLogs,
  type AuditLogRow,
  type SandboxConfig,
} from '@/services/extensionApi';

const SandboxManagement: React.FC = () => {
  const [config, setConfig] = useState<SandboxConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [auditRows, setAuditRows] = useState<AuditLogRow[]>([]);
  const [auditCursor, setAuditCursor] = useState<string | null>(null);
  const [auditLoading, setAuditLoading] = useState(false);

  const loadConfig = useCallback(async () => {
    setConfig(await getSandboxConfig());
  }, []);

  const loadAudit = useCallback(async (append = false, cursor?: string | null) => {
    setAuditLoading(true);
    try {
      const page = await listAuditLogs({
        limit: 15,
        cursor: append && cursor ? cursor : undefined,
      });
      setAuditRows((prev) => (append ? [...prev, ...page.rows] : page.rows));
      setAuditCursor(page.nextCursor ?? null);
    } catch (e) {
      message.error(formatStudioError(e, '加载审计日志失败'));
    } finally {
      setAuditLoading(false);
    }
  }, []);

  useEffect(() => {
    (async () => {
      try {
        await Promise.all([loadConfig(), loadAudit()]);
      } catch (e) {
        message.error(formatStudioError(e, '加载沙箱数据失败'));
      } finally {
        setLoading(false);
      }
    })();
  }, [loadConfig, loadAudit]);

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
        <Card title="操作审计">
          <Table<AuditLogRow>
            rowKey="id"
            size="small"
            loading={auditLoading}
            pagination={false}
            dataSource={auditRows}
            columns={[
              { title: '动作', dataIndex: 'action', width: 160 },
              { title: '资源', dataIndex: 'resourceType', width: 80 },
              { title: '资源ID', dataIndex: 'resourceId', width: 80 },
              {
                title: '结果',
                dataIndex: 'result',
                width: 80,
                render: (r: string) => (
                  <Tag color={r === 'SUCCESS' ? 'green' : 'red'}>{r}</Tag>
                ),
              },
              { title: 'traceId', dataIndex: 'traceId', ellipsis: true },
              {
                title: '时间',
                dataIndex: 'createdAt',
                width: 170,
                render: (v: string) => formatDate(v),
              },
            ]}
          />
          {auditCursor ? (
            <div style={{ marginTop: 12, textAlign: 'center' }}>
              <Button loading={auditLoading} onClick={() => loadAudit(true, auditCursor)}>
                加载更多
              </Button>
            </div>
          ) : null}
        </Card>
      </Col>
    </Row>
  );
};

export default SandboxManagement;
