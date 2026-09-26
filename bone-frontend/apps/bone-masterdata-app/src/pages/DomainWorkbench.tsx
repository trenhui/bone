import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Popconfirm,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Steps,
  Table,
  Tabs,
  Tag,
} from 'antd';
import {
  AuditOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  ReconciliationOutlined,
  ReloadOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { MasterDataEntity, QualityCheck } from '../types';
import {
  dataQualityRuleApi,
  driftApi,
  feedbackApi,
  governanceRoleApi,
  masterDataEntityApi,
  masterDataFieldApi,
  masterDataRecordApi,
  qualityCheckApi,
  qualityIssueApi,
  subscriptionApi,
} from '../services/api';
import { useMessage } from '../App';

const driftTypeLabel: Record<string, string> = {
  FIELD_ADDED: '字段新增',
  FIELD_REMOVED: '字段删除',
  TYPE_CHANGED: '类型变化',
  NAME_CHANGED: '名称变化'
};

const statusColor = (s: string) =>
  s === 'ACTIVE' || s === 'PUBLISHED' || s === 'COMPLETED' || s === 'DONE' || s === 'CLOSED'
    ? 'green'
    : s === 'PENDING' || s === 'OPEN' || s === 'RUNNING'
      ? 'orange'
      : s === 'FAILED' || s === 'REJECTED'
        ? 'red'
        : 'default';

/** 端到端六步流程（UC-MD 全链路），按当前域的治理数据推导所处阶段。 */
const FLOW_STEPS = [
  { title: '建模定义', desc: '创建/实例化主数据模型', target: '/entities' },
  { title: '治理配置', desc: '任命 Owner/Steward · 订阅审批', target: '#governance' },
  { title: '数据采集', desc: '录入 / Excel 导入 / 集成接入', target: '/records' },
  { title: '质量检核', desc: '规则质检 → 整改工单闭环', target: '#quality' },
  { title: '审批发布', desc: '六态状态机 · SoD 审批', target: '/records' },
  { title: '分发消费', desc: '订阅生效 · 变更事件下发', target: '#subs' }
];

/**
 * 主数据域工作台（G7 域视图 · UC-MD 串联）：
 * 选中一个主数据域后，概览指标 + 六步流程导航 + 治理/质量/订阅/漂移/反馈一站式处置。
 */
const DomainWorkbench: React.FC = () => {
  const message = useMessage();
  const navigate = useNavigate();
  const [entities, setEntities] = useState<MasterDataEntity[]>([]);
  const [entityId, setEntityId] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  const [recordTotal, setRecordTotal] = useState(0);
  const [publishedTotal, setPublishedTotal] = useState(0);
  const [fields, setFields] = useState<any[]>([]);
  const [checks, setChecks] = useState<QualityCheck[]>([]);
  const [issues, setIssues] = useState<any[]>([]);
  const [openIssues, setOpenIssues] = useState(0);
  const [subs, setSubs] = useState<any[]>([]);
  const [roles, setRoles] = useState<any[]>([]);
  const [drifts, setDrifts] = useState<any[]>([]);
  const [feedbacks, setFeedbacks] = useState<any[]>([]);
  const [roleOpen, setRoleOpen] = useState(false);
  const [roleForm] = Form.useForm();

  useEffect(() => {
    void masterDataEntityApi.page({ pageNum: 1, pageSize: 100 }).then((res) => {
      if (res.code === 200) setEntities(res.data.list);
    });
  }, []);

  const loadDomain = useCallback(async (eid: number) => {
    setLoading(true);
    try {
      const [rec, pub, fld, chk, iss, cnt, sub, role, drf, fb] = await Promise.all([
        masterDataRecordApi.page({ masterDataEntityId: eid, pageNum: 1, pageSize: 1 }),
        masterDataRecordApi.page({ masterDataEntityId: eid, pageNum: 1, pageSize: 1, status: 'PUBLISHED' }),
        masterDataFieldApi.listByEntityId(eid),
        qualityCheckApi.list({ masterDataEntityId: eid }),
        qualityIssueApi.byEntity(eid),
        qualityIssueApi.openCount(eid),
        subscriptionApi.byEntity(eid),
        governanceRoleApi.byEntity(eid),
        driftApi.byEntity(eid),
        feedbackApi.byEntity(eid)
      ]);
      setRecordTotal(rec.data?.total ?? 0);
      setPublishedTotal(pub.data?.total ?? 0);
      setFields(fld.data ?? []);
      setChecks(chk.data ?? []);
      setIssues(iss.data ?? []);
      setOpenIssues(cnt.data ?? 0);
      setSubs(sub.data ?? []);
      setRoles(role.data ?? []);
      setDrifts(drf.data ?? []);
      setFeedbacks(fb.data ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (entityId) void loadDomain(entityId);
  }, [entityId, loadDomain]);

  const currentEntity = useMemo(
    () => entities.find((e) => e.id === entityId),
    [entities, entityId]
  );

  const latestCheck = useMemo(() => {
    const done = checks.filter((c) => c.status === 'COMPLETED' && (c.totalRecords ?? 0) > 0);
    if (done.length === 0) return undefined;
    return [...done].sort((a, b) => (b.startedAt > a.startedAt ? 1 : -1))[0];
  }, [checks]);

  const passRate = useMemo(() => {
    if (!latestCheck || !latestCheck.totalRecords) return undefined;
    return Math.round(((latestCheck.passedRecords ?? 0) / latestCheck.totalRecords) * 100);
  }, [latestCheck]);

  const activeSubs = subs.filter((s) => s.status === 'ACTIVE').length;
  const pendingDrifts = drifts.filter((d) => d.status === 'PENDING').length;
  const pendingFeedbacks = feedbacks.filter((f) => f.status === 'PENDING').length;

  const currentStep = useMemo(() => {
    if (fields.length === 0) return 0;
    if (roles.length === 0) return 1;
    if (recordTotal === 0) return 2;
    if (!latestCheck) return 3;
    if (publishedTotal === 0) return 4;
    return 5;
  }, [fields.length, roles.length, recordTotal, latestCheck, publishedTotal]);

  const handleRunCheck = async () => {
    if (!entityId) return;
    try {
      const res = await dataQualityRuleApi.executeCheck(entityId);
      if (res.code === 200) {
        message.success(`质检已完成，任务 ID：${res.data}`);
        void loadDomain(entityId);
      } else {
        message.error(res.message);
      }
    } catch {
      message.error('执行质检失败');
    }
  };

  const handleReconcile = async () => {
    if (!entityId) return;
    try {
      const res = await driftApi.reconcile(entityId);
      message.success(res.data?.length ? `发现 ${res.data.length} 条漂移（含历史）` : '对账完成，无漂移');
      void loadDomain(entityId);
    } catch {
      message.error('模型对账失败');
    }
  };

  const statCards = [
    { title: '记录总数', value: recordTotal, icon: <DatabaseOutlined />, color: '#185FA5', suffix: `（已发布 ${publishedTotal}）` },
    {
      title: '最新质检合规率',
      value: passRate ?? '-',
      icon: <SafetyCertificateOutlined />,
      color: passRate === undefined ? '#888780' : passRate >= 98 ? '#0F6E56' : passRate >= 90 ? '#854F0B' : '#A32D2D',
      suffix: '%'
    },
    { title: '未关闭工单', value: openIssues, icon: <AuditOutlined />, color: openIssues > 0 ? '#A32D2D' : '#0F6E56' },
    { title: '生效订阅', value: activeSubs, icon: <CloudUploadOutlined />, color: '#534AB7' },
    { title: '待处理漂移', value: pendingDrifts, icon: <ReconciliationOutlined />, color: pendingDrifts > 0 ? '#854F0B' : '#0F6E56' },
    { title: '待处理反馈', value: pendingFeedbacks, icon: <TeamOutlined />, color: pendingFeedbacks > 0 ? '#854F0B' : '#0F6E56' }
  ];

  const roleCols = [
    { title: '账号ID', dataIndex: 'accountId', key: 'accountId' },
    {
      title: '角色',
      dataIndex: 'roleType',
      key: 'role',
      render: (t: string) => (
        <Tag color={t === 'OWNER' ? 'gold' : t === 'APPROVER' ? 'purple' : 'blue'}>
          {t === 'OWNER' ? 'OWNER 数据责任人' : t === 'APPROVER' ? 'APPROVER 审批人' : 'STEWARD 数据管家'}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Popconfirm
          title="移除该角色？"
          onConfirm={async () => {
            await governanceRoleApi.unassign(r.id);
            if (entityId) void loadDomain(entityId);
          }}
        >
          <Button size="small" danger>移除</Button>
        </Popconfirm>
      )
    }
  ];

  const subCols = [
    { title: '应用ID', dataIndex: 'appId', key: 'appId' },
    {
      title: '模式',
      dataIndex: 'subscribeMode',
      key: 'mode',
      render: (m: string) => <Tag color={m === 'EVENT' ? 'geekblue' : 'cyan'}>{m === 'EVENT' ? 'EVENT 变更事件' : 'READ 只读取数'}</Tag>
    },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Space>
          {r.status === 'PENDING' && (
            <Button
              size="small"
              type="primary"
              onClick={async () => {
                await subscriptionApi.approve(r.id);
                message.success('已批准订阅');
                if (entityId) void loadDomain(entityId);
              }}
            >
              批准
            </Button>
          )}
          {r.status === 'ACTIVE' && (
            <Popconfirm
              title="确认撤销订阅？"
              onConfirm={async () => {
                await subscriptionApi.revoke(r.id);
                if (entityId) void loadDomain(entityId);
              }}
            >
              <Button size="small" danger>撤销</Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  const checkCols = [
    { title: '任务ID', dataIndex: 'id', key: 'id', width: 90 },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt' },
    { title: '结束时间', dataIndex: 'endedAt', key: 'endedAt', render: (v: string) => v ?? '-' },
    {
      title: '参检/通过/失败',
      key: 'num',
      render: (_: unknown, r: QualityCheck) => `${r.totalRecords ?? 0} / ${r.passedRecords ?? 0} / ${r.failedRecords ?? 0}`
    },
    {
      title: '合规率',
      key: 'rate',
      width: 180,
      render: (_: unknown, r: QualityCheck) => {
        if (!r.totalRecords) return '-';
        const rate = Math.round(((r.passedRecords ?? 0) / r.totalRecords) * 100);
        return <Progress percent={rate} size="small" status={rate >= 98 ? 'success' : rate >= 90 ? 'normal' : 'exception'} />;
      }
    },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> }
  ];

  const issueCols = [
    { title: '描述', dataIndex: 'issueDesc', key: 'd', ellipsis: true },
    {
      title: '严重度',
      dataIndex: 'severity',
      key: 'sev',
      render: (s: string) => <Tag color={s === 'HIGH' ? 'red' : s === 'MEDIUM' ? 'orange' : 'blue'}>{s}</Tag>
    },
    { title: '记录ID', dataIndex: 'recordId', key: 'r', render: (v: number) => v ?? '-' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 's',
      render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag>
    },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Space>
          {r.status === 'OPEN' && (
            <Button
              size="small"
              onClick={async () => {
                await qualityIssueApi.fix(r.id);
                message.success('已标记整改');
                if (entityId) void loadDomain(entityId);
              }}
            >
              整改
            </Button>
          )}
          {r.status === 'FIXED' && (
            <Button
              size="small"
              type="primary"
              onClick={async () => {
                await qualityIssueApi.close(r.id);
                message.success('复检通过已关闭');
                if (entityId) void loadDomain(entityId);
              }}
            >
              关闭
            </Button>
          )}
          {r.status === 'OPEN' && (
            <Button
              size="small"
              danger
              onClick={async () => {
                await qualityIssueApi.ignore(r.id);
                if (entityId) void loadDomain(entityId);
              }}
            >
              忽略
            </Button>
          )}
        </Space>
      )
    }
  ];

  const driftCols = [
    { title: '类型', dataIndex: 'driftType', key: 't', render: (t: string) => driftTypeLabel[t] ?? t },
    { title: '字段', dataIndex: 'fieldCode', key: 'f' },
    { title: '原值', dataIndex: 'oldValue', key: 'o', ellipsis: true },
    { title: '新值', dataIndex: 'newValue', key: 'n', ellipsis: true },
    {
      title: '破坏性',
      dataIndex: 'destructive',
      key: 'd',
      render: (v: boolean) => (v ? <Tag color="red">破坏性</Tag> : <Tag>否</Tag>)
    },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> },
    {
      title: '处置',
      key: 'act',
      render: (_: unknown, r: any) =>
        r.status === 'PENDING' ? (
          <Space>
            <Button
              size="small"
              onClick={async () => {
                await driftApi.handle(r.id, 'SYNCED');
                if (entityId) void loadDomain(entityId);
              }}
            >
              同步
            </Button>
            <Button
              size="small"
              onClick={async () => {
                await driftApi.handle(r.id, 'IGNORED');
                if (entityId) void loadDomain(entityId);
              }}
            >
              忽略
            </Button>
          </Space>
        ) : null
    }
  ];

  const feedbackCols = [
    { title: '类型', dataIndex: 'feedbackType', key: 't', render: (t: string) => <Tag>{t}</Tag> },
    { title: '内容', dataIndex: 'content', key: 'c', ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> },
    {
      title: '处理',
      key: 'act',
      render: (_: unknown, r: any) => (
        <Space>
          {r.status === 'PENDING' && (
            <Button
              size="small"
              type="primary"
              onClick={async () => {
                await feedbackApi.accept(r.id);
                if (entityId) void loadDomain(entityId);
              }}
            >
              受理
            </Button>
          )}
          {r.status !== 'DONE' && r.status !== 'REJECTED' && (
            <Button
              size="small"
              onClick={async () => {
                await feedbackApi.complete(r.id, '已修正');
                message.success('反馈已完成');
                if (entityId) void loadDomain(entityId);
              }}
            >
              完成
            </Button>
          )}
          {r.status === 'PENDING' && (
            <Button
              size="small"
              danger
              onClick={async () => {
                await feedbackApi.reject(r.id, '不成立');
                if (entityId) void loadDomain(entityId);
              }}
            >
              驳回
            </Button>
          )}
        </Space>
      )
    }
  ];

  return (
    <div style={{ padding: 24 }}>
      {/* 页头 */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 20, fontWeight: 500, color: 'rgba(0,0,0,0.88)' }}>主数据域工作台</div>
        <div style={{ marginTop: 4, color: 'rgba(0,0,0,0.55)' }}>
          建模 → 治理 → 采集 → 质检 → 审批发布 → 分发：选中一个主数据域，一站式处置全生命周期治理事务
        </div>
      </div>

      {/* 域选择 + 全局动作 */}
      <Card style={{ marginBottom: 16 }} styles={{ body: { padding: '16px 24px' } }}>
        <Space size="middle" wrap>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="选择主数据域（模型）"
            style={{ width: 320 }}
            value={entityId}
            onChange={(v) => setEntityId(v)}
            options={entities.map((e) => ({
              value: e.id,
              label: `#${e.id} ${e.name}${e.status === 'PUBLISHED' ? '（已发布）' : '（草稿）'}`
            }))}
          />
          {currentEntity && <Tag color={currentEntity.status === 'PUBLISHED' ? 'green' : 'blue'}>{currentEntity.status}</Tag>}
          <Button icon={<RocketOutlined />} disabled={!entityId} onClick={() => void handleRunCheck()}>
            执行质检
          </Button>
          <Button icon={<ReconciliationOutlined />} disabled={!entityId} onClick={() => void handleReconcile()}>
            模型对账
          </Button>
          <Button icon={<ReloadOutlined />} disabled={!entityId} onClick={() => entityId && void loadDomain(entityId)}>
            刷新
          </Button>
        </Space>
      </Card>

      {!entityId ? (
        <Card>
          <div style={{ textAlign: 'center', padding: '48px 0', color: 'rgba(0,0,0,0.45)' }}>
            <DatabaseOutlined style={{ fontSize: 48, marginBottom: 16 }} />
            <div>请先在上方选择一个主数据域，加载治理概览</div>
          </div>
        </Card>
      ) : (
        <>
          {/* 六步流程导航 */}
          <Card title="主数据全生命周期流程" style={{ marginBottom: 16 }} loading={loading}>
            <Steps
              current={currentStep}
              items={FLOW_STEPS.map((s, i) => ({
                title: s.title,
                description: (
                  <a
                    onClick={() => {
                      if (s.target.startsWith('#')) {
                        message.info(`请在下方「${i === 1 ? '治理角色' : i === 3 ? '质量检查' : '消费订阅'}」页签处置`);
                      } else {
                        navigate(s.target);
                      }
                    }}
                  >
                    {s.desc}
                  </a>
                )
              }))}
            />
          </Card>

          {/* 概览指标 */}
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            {statCards.map((c) => (
              <Col key={c.title} xs={12} sm={8} lg={4}>
                <Card styles={{ body: { padding: '16px 20px' } }}>
                  <Space direction="vertical" size={2} style={{ width: '100%' }}>
                    <Space size={8}>
                      <span style={{ fontSize: 18, color: c.color }}>{c.icon}</span>
                      <span style={{ color: 'rgba(0,0,0,0.55)', fontSize: 13 }}>{c.title}</span>
                    </Space>
                    <Statistic value={c.value} suffix={c.suffix} valueStyle={{ fontSize: 24, color: c.color }} />
                  </Space>
                </Card>
              </Col>
            ))}
          </Row>

          {/* 域详情页签 */}
          <Card>
            <Tabs
              items={[
                {
                  key: 'fields',
                  label: `结构字段 (${fields.length})`,
                  children: (
                    <Descriptions column={2} size="small" bordered>
                      {fields.map((f) => (
                        <Descriptions.Item key={f.id} label={`${f.name}${f.code ? `（${f.code}）` : ''}`}>
                          <Space size={6}>
                            <Tag>{f.type}</Tag>
                            {f.required && <Tag color="red">必填</Tag>}
                            {f.length ? <span style={{ color: 'rgba(0,0,0,0.45)' }}>长度 {f.length}</span> : null}
                          </Space>
                        </Descriptions.Item>
                      ))}
                      {fields.length === 0 && (
                        <Descriptions.Item label="暂无字段" span={2}>
                          先到「主数据模型 → 字段管理」定义结构
                        </Descriptions.Item>
                      )}
                    </Descriptions>
                  )
                },
                {
                  key: 'roles',
                  label: `治理角色 (${roles.length})`,
                  children: (
                    <>
                      <Button
                        type="primary"
                        ghost
                        icon={<TeamOutlined />}
                        style={{ marginBottom: 12 }}
                        onClick={() => setRoleOpen(true)}
                      >
                        指派治理角色
                      </Button>
                      <Table rowKey="id" size="small" columns={roleCols} dataSource={roles} pagination={false} />
                    </>
                  )
                },
                {
                  key: 'subs',
                  label: `消费订阅 (${subs.length})`,
                  children: <Table rowKey="id" size="small" columns={subCols} dataSource={subs} pagination={false} />
                },
                {
                  key: 'quality',
                  label: `质量检查 (${checks.length})`,
                  children: <Table rowKey="id" size="small" columns={checkCols} dataSource={checks} pagination={{ pageSize: 8 }} />
                },
                {
                  key: 'issues',
                  label: `整改工单 (${issues.length})`,
                  children: <Table rowKey="id" size="small" columns={issueCols} dataSource={issues} pagination={{ pageSize: 8 }} />
                },
                {
                  key: 'drifts',
                  label: `模型漂移 (${drifts.length})`,
                  children: (
                    <>
                      <div style={{ marginBottom: 12, color: 'rgba(0,0,0,0.45)' }}>
                        元数据↔主数据结构持续对账（UC-T10）：只检测不自动同步，破坏性变更须人工裁决
                      </div>
                      <Table rowKey="id" size="small" columns={driftCols} dataSource={drifts} pagination={false} />
                    </>
                  )
                },
                {
                  key: 'feedbacks',
                  label: `下游反馈 (${feedbacks.length})`,
                  children: <Table rowKey="id" size="small" columns={feedbackCols} dataSource={feedbacks} pagination={false} />
                }
              ]}
            />
          </Card>
      </>
      )}

      {/* 指派治理角色 */}
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
          if (entityId) void loadDomain(entityId);
        }}
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="accountId" label="IAM 账号ID" rules={[{ required: true, message: '账号ID不能为空' }]}>
            <Input type="number" />
          </Form.Item>
          <Form.Item name="roleType" label="角色" rules={[{ required: true }]} initialValue="STEWARD">
            <Select
              options={[
                { value: 'OWNER', label: 'OWNER 数据责任人' },
                { value: 'STEWARD', label: 'STEWARD 数据管家' },
                { value: 'APPROVER', label: 'APPROVER 审批人' }
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DomainWorkbench;
