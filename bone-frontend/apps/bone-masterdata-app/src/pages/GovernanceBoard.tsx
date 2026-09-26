import React, { useCallback, useEffect, useState } from 'react';
import {
  Button, Card, Col, Form, Input, Modal, Popconfirm, Row, Select, Space, Table, Tabs, Tag, message
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { feedbackApi, driftApi, governanceRoleApi, subscriptionApi } from '../services/api';

const driftTypeLabel: Record<string, string> = {
  FIELD_ADDED: '字段新增',
  FIELD_REMOVED: '字段删除',
  TYPE_CHANGED: '类型变化',
  NAME_CHANGED: '名称变化'
};

/** 治理看板（G3/G10/G16/G17）：订阅 · 治理角色 · 模型漂移 · 下游反馈，围绕主数据模型维度。 */
const GovernanceBoard: React.FC = () => {
  const [entityId, setEntityId] = useState<number | undefined>();
  const [subs, setSubs] = useState<any[]>([]);
  const [roles, setRoles] = useState<any[]>([]);
  const [drifts, setDrifts] = useState<any[]>([]);
  const [feedbacks, setFeedbacks] = useState<any[]>([]);
  const [roleOpen, setRoleOpen] = useState(false);
  const [roleForm] = Form.useForm();

  const loadAll = useCallback(async (eid?: number) => {
    if (!eid) {
      setSubs([]);
      setRoles([]);
      setDrifts([]);
      setFeedbacks([]);
      return;
    }
    const [s, r, d, f] = await Promise.all([
      subscriptionApi.byEntity(eid),
      governanceRoleApi.byEntity(eid),
      driftApi.byEntity(eid),
      feedbackApi.byEntity(eid)
    ]);
    setSubs(s.data ?? []);
    setRoles(r.data ?? []);
    setDrifts(d.data ?? []);
    setFeedbacks(f.data ?? []);
  }, []);

  useEffect(() => {
    if (entityId) void loadAll(entityId);
  }, [entityId, loadAll]);

  const subCols = [
    { title: '应用ID', dataIndex: 'appId', key: 'appId' },
    { title: '模式', dataIndex: 'subscribeMode', key: 'mode', render: (m: string) => <Tag>{m}</Tag> },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : s === 'PENDING' ? 'orange' : 'default'}>{s}</Tag>
    },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Space>
          {r.status === 'PENDING' && (
            <Button size="small" type="primary" onClick={async () => { await subscriptionApi.approve(r.id); message.success('已批准'); entityId && void loadAll(entityId); }}>
              批准
            </Button>
          )}
          {r.status === 'ACTIVE' && (
            <Popconfirm title="确认撤销订阅？" onConfirm={async () => { await subscriptionApi.revoke(r.id); entityId && void loadAll(entityId); }}>
              <Button size="small" danger>撤销</Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  const roleCols = [
    { title: '账号ID', dataIndex: 'accountId', key: 'accountId' },
    {
      title: '角色',
      dataIndex: 'roleType',
      key: 'role',
      render: (t: string) => <Tag color={t === 'OWNER' ? 'gold' : t === 'APPROVER' ? 'purple' : 'blue'}>{t}</Tag>
    },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Popconfirm title="移除该角色？" onConfirm={async () => { await governanceRoleApi.unassign(r.id); entityId && void loadAll(entityId); }}>
          <Button size="small" danger>移除</Button>
        </Popconfirm>
      )
    }
  ];

  const driftCols = [
    { title: '类型', dataIndex: 'driftType', key: 't', render: (t: string) => driftTypeLabel[t] ?? t },
    { title: '字段', dataIndex: 'fieldCode', key: 'f' },
    { title: '原值', dataIndex: 'oldValue', key: 'o' },
    { title: '新值', dataIndex: 'newValue', key: 'n' },
    {
      title: '破坏性',
      dataIndex: 'destructive',
      key: 'd',
      render: (v: boolean) => (v ? <Tag color="red">破坏性</Tag> : <Tag>否</Tag>)
    },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag>{s}</Tag> },
    {
      title: '处置',
      key: 'act',
      render: (_: unknown, r: any) =>
        r.status === 'PENDING' ? (
          <Space>
            <Button size="small" onClick={async () => { await driftApi.handle(r.id, 'SYNCED'); entityId && void loadAll(entityId); }}>已同步</Button>
            <Button size="small" onClick={async () => { await driftApi.handle(r.id, 'IGNORED'); entityId && void loadAll(entityId); }}>忽略</Button>
            <Button size="small" danger onClick={async () => { await driftApi.handle(r.id, 'BLOCKED'); entityId && void loadAll(entityId); }}>阻断</Button>
          </Space>
        ) : null
    }
  ];

  const fbCols = [
    { title: '类型', dataIndex: 'feedbackType', key: 't', render: (t: string) => <Tag>{t}</Tag> },
    { title: '内容', dataIndex: 'content', key: 'c', ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag>{s}</Tag> },
    {
      title: '处理',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Space>
          {r.status === 'PENDING' && (
            <Button size="small" type="primary" onClick={async () => { await feedbackApi.accept(r.id); entityId && void loadAll(entityId); }}>受理</Button>
          )}
          {r.status !== 'DONE' && r.status !== 'REJECTED' && (
            <Button size="small" onClick={async () => { await feedbackApi.complete(r.id, '已修正'); message.success('已完成'); entityId && void loadAll(entityId); }}>完成</Button>
          )}
          {r.status === 'PENDING' && (
            <Button size="small" danger onClick={async () => { await feedbackApi.reject(r.id, '不成立'); entityId && void loadAll(entityId); }}>驳回</Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <Card
      title="治理看板"
      extra={
        <Space>
          <Input.Search
            placeholder="输入主数据模型ID"
            enterButton="加载"
            style={{ width: 260 }}
            onSearch={(v) => {
              const n = Number(v);
              if (Number.isFinite(n) && n > 0) setEntityId(n);
              else message.warning('请输入有效模型ID');
            }}
          />
          {entityId && (
            <Button
              onClick={async () => {
                const res = await driftApi.reconcile(entityId);
                message.success(res.data?.length ? `发现 ${res.data.length} 条漂移（含历史）` : '对账完成，无漂移');
                void loadAll(entityId);
              }}
            >
              模型对账
            </Button>
          )}
        </Space>
      }
    >
      <Row gutter={16}>
        <Col span={10}>
          <Card size="small" title="使用说明" style={{ marginBottom: 16 }}>
            输入主数据模型 ID 后可查看该模型的订阅关系、治理角色、模型漂移与下游反馈；
            「模型对账」触发元数据↔主数据结构比对（UC-T10，只检测不自动同步）。
          </Card>
          <Button
            icon={<PlusOutlined />}
            disabled={!entityId}
            onClick={() => setRoleOpen(true)}
            style={{ marginBottom: 16 }}
          >
            指派治理角色
          </Button>
        </Col>
      </Row>

      <Tabs
        items={[
          { key: 'subs', label: `消费订阅 (${subs.length})`, children: <Table rowKey="id" size="small" columns={subCols} dataSource={subs} pagination={false} /> },
          { key: 'roles', label: `治理角色 (${roles.length})`, children: <Table rowKey="id" size="small" columns={roleCols} dataSource={roles} pagination={false} /> },
          { key: 'drifts', label: `模型漂移 (${drifts.length})`, children: <Table rowKey="id" size="small" columns={driftCols} dataSource={drifts} pagination={false} /> },
          { key: 'feedbacks', label: `下游反馈 (${feedbacks.length})`, children: <Table rowKey="id" size="small" columns={fbCols} dataSource={feedbacks} pagination={false} /> }
        ]}
      />

      <Modal
        title="指派治理角色"
        open={roleOpen}
        onCancel={() => setRoleOpen(false)}
        destroyOnClose
        onOk={async () => {
          const v = await roleForm.validateFields();
          await governanceRoleApi.assign({ ...v, masterDataEntityId: entityId, accountId: Number(v.accountId) });
          message.success('已指派');
          setRoleOpen(false);
          roleForm.resetFields();
          void loadAll(entityId);
        }}
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="accountId" label="IAM 账号ID" rules={[{ required: true, message: '账号ID不能为空' }]}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="roleType" label="角色" rules={[{ required: true }]} initialValue="STEWARD">
            <Select options={[
              { value: 'OWNER', label: 'OWNER 数据责任人' },
              { value: 'STEWARD', label: 'STEWARD 数据管家' },
              { value: 'APPROVER', label: 'APPROVER 审批人' }
            ]} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default GovernanceBoard;
