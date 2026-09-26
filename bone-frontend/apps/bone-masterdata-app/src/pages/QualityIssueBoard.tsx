import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { CheckOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import { qualityIssueApi } from '../services/api';
import type { QualityIssue } from '../types/governance';

const sevColor = (s: string) => (s === 'HIGH' ? 'red' : s === 'MEDIUM' ? 'orange' : 'blue');
const statusColor = (s: string) =>
  s === 'OPEN' ? 'red' : s === 'FIXED' ? 'orange' : s === 'CLOSED' ? 'green' : 'default';

/** 质量整改工单看板（G11 / UC-T9）：OPEN → FIXED → CLOSED。 */
const QualityIssueBoard: React.FC = () => {
  const [entityId, setEntityId] = useState<number | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [rows, setRows] = useState<QualityIssue[]>([]);
  const [openCount, setOpenCount] = useState<number>(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async (eid?: number, st?: string) => {
    if (!eid) return;
    const [list, count] = await Promise.all([
      qualityIssueApi.byEntity(eid, st),
      qualityIssueApi.openCount(eid)
    ]);
    setRows(list.data ?? []);
    setOpenCount(count.data ?? 0);
  }, []);

  useEffect(() => {
    if (entityId) void load(entityId, status);
  }, [entityId, status, load]);

  const columns = [
    { title: '描述', dataIndex: 'issueDesc', key: 'd', ellipsis: true },
    { title: '严重度', dataIndex: 'severity', key: 'sev', render: (s: string) => <Tag color={sevColor(s)}>{s}</Tag> },
    { title: '记录ID', dataIndex: 'recordId', key: 'r' },
    { title: '期限', dataIndex: 'dueAt', key: 'due' },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: QualityIssue) => (
        <Space>
          {r.status === 'OPEN' && (
            <Button size="small" icon={<CheckOutlined />} onClick={async () => { await qualityIssueApi.fix(r.id); message.success('已标记整改'); void load(entityId, status); }}>
              整改
            </Button>
          )}
          {r.status === 'FIXED' && (
            <Button size="small" type="primary" onClick={async () => { await qualityIssueApi.close(r.id); message.success('复检通过已关闭'); void load(entityId, status); }}>
              关闭
            </Button>
          )}
          {r.status === 'OPEN' && (
            <Button size="small" icon={<CloseOutlined />} onClick={async () => { await qualityIssueApi.ignore(r.id); void load(entityId, status); }}>
              忽略
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <Card
      title={`质量整改工单（未关闭 ${openCount} 单 · 发布软门禁）`}
      extra={
        <Space>
          <Input.Search
            placeholder="输入主数据模型ID"
            enterButton="加载"
            style={{ width: 240 }}
            onSearch={(v) => {
              const n = Number(v);
              if (Number.isFinite(n) && n > 0) setEntityId(n);
              else message.warning('请输入有效模型ID');
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            onChange={(v) => setStatus(v)}
            options={['OPEN', 'FIXED', 'CLOSED', 'IGNORED'].map((s) => ({ value: s, label: s }))}
          />
          <Button type="primary" icon={<PlusOutlined />} disabled={!entityId} onClick={() => setCreateOpen(true)}>
            建工单
          </Button>
        </Space>
      }
    >
      <Table rowKey="id" columns={columns} dataSource={rows} pagination={{ pageSize: 10 }} />

      <Modal
        title="新建整改工单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        destroyOnClose
        onOk={async () => {
          const v = await form.validateFields();
          await qualityIssueApi.create({ ...v, masterDataEntityId: entityId, assigneeId: v.assigneeId ? Number(v.assigneeId) : undefined });
          message.success('工单已创建');
          setCreateOpen(false);
          form.resetFields();
          void load(entityId, status);
        }}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="issueDesc" label="问题描述" rules={[{ required: true, message: '描述不能为空' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="severity" label="严重度" initialValue="MEDIUM">
            <Select options={['HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))} />
          </Form.Item>
          <Form.Item name="recordId" label="关联记录ID（可空）">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="assigneeId" label="指派处理人ID（可空）">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default QualityIssueBoard;
