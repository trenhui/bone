import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Button, Card, Descriptions, Empty, Form, Input, InputNumber, List, Modal,
  Popconfirm, Row, Col, Select, Space, Table, Tabs, Tag, Typography, message, Badge, Tooltip,
} from 'antd';
import {
  PlusOutlined, ArrowLeftOutlined, EditOutlined, DeleteOutlined, FieldStringOutlined,
  ReloadOutlined, FieldNumberOutlined, CalendarOutlined, ThunderboltOutlined,
  ApartmentOutlined, CheckCircleOutlined, WarningOutlined, InfoCircleOutlined, AimOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { errorMessage, metadataEntityApi, metadataFieldApi, metadataRelationApi } from '../services/metadataApi';
import PublishPreviewModal from '../components/PublishPreviewModal';
import FieldWizardModal from '../components/FieldWizardModal';
import { touchRecent } from '../utils/recent';
import type {
  EntityValidationIssue, MetaEntity, MetaField, MetaRelation,
  CreateMetaFieldReq, CreateMetaRelationReq, UpdateMetaFieldReq, UpdateMetaRelationReq,
} from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, FIELD_TYPE_MAP, RELATION_TYPES } from '../types';

const { Text } = Typography;

const EntityDetail: React.FC = () => {
  const { id, appId, moduleId } = useParams<{ id: string; appId?: string; moduleId?: string }>();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [entity, setEntity] = useState<MetaEntity | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingField, setEditingField] = useState<MetaField | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [activeTab, setActiveTab] = useState('fields');

  // 关系（2b §6-4：独立页并入 Detail Tab）
  const [relations, setRelations] = useState<MetaRelation[]>([]);
  const [relationLoading, setRelationLoading] = useState(false);
  const [relationModalOpen, setRelationModalOpen] = useState(false);
  const [editingRelation, setEditingRelation] = useState<MetaRelation | null>(null);
  const [relationForm] = Form.useForm();

  // 校验（2b F7：问题面板，字段级问题可点击跳转）
  const [issues, setIssues] = useState<EntityValidationIssue[] | null>(null);
  const [validationLoading, setValidationLoading] = useState(false);

  // 关系目标实体候选（新建关系时选择目标实体）
  const [allEntities, setAllEntities] = useState<MetaEntity[]>([]);

  // 加载实体详情
  useEffect(() => {
    if (!id) return;
    metadataEntityApi.detail(id!).then((res) => {
      if (res.code === 200) {
        setEntity(res.data);
        // 「继续建模」最近编辑记录（工作台首页消费）
        touchRecent({
          id: res.data.id,
          code: res.data.code,
          displayName: res.data.displayName,
          appId,
          moduleId,
        });
      } else message.error(res.message);
    });
  }, [id, appId, moduleId]);

  // 加载字段列表
  const loadFields = useCallback(async () => {
    if (!id) return;
    setFieldLoading(true);
    try {
      const res = await metadataFieldApi.page(id!, { pageNum: 1, pageSize: 200 });
      if (res.code === 200) {
        // 按 sortOrder 排序
        const sorted = (res.data.list || []).sort(
          (a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999),
        );
        setFields(sorted);
      }
    } catch {
      message.error('加载字段失败');
    } finally {
      setFieldLoading(false);
    }
  }, [id]);

  useEffect(() => { loadFields(); }, [loadFields]);

  useEffect(() => {
    metadataEntityApi.page({ pageNum: 1, pageSize: 200 }).then((res) => {
      if (res.code === 200) setAllEntities(res.data.list);
    });
  }, []);

  // 关系 Tab：加载与当前实体相关的关系（作为源或目标），两个方向合并去重
  const loadRelations = useCallback(async () => {
    if (!id) return;
    setRelationLoading(true);
    try {
      const [asSource, asTarget] = await Promise.all([
        metadataRelationApi.page({ pageNum: 1, pageSize: 200, sourceEntityId: id! }),
        metadataRelationApi.page({ pageNum: 1, pageSize: 200, targetEntityId: id! }),
      ]);
      const merged = new Map<number, MetaRelation>();
      [...(asSource.code === 200 ? asSource.data.list : []),
       ...(asTarget.code === 200 ? asTarget.data.list : [])].forEach((r) => merged.set(r.id, r));
      setRelations([...merged.values()]);
    } catch {
      message.error('加载关系失败');
    } finally {
      setRelationLoading(false);
    }
  }, [id]);

  // 校验 Tab：调 UC-W5 静态校验
  const loadValidation = useCallback(async () => {
    if (!id) return;
    setValidationLoading(true);
    try {
      const res = await metadataEntityApi.validate(id!);
      if (res.code === 200) setIssues(res.data);
      else message.error(errorMessage(res));
    } catch {
      message.error('校验请求失败');
    } finally {
      setValidationLoading(false);
    }
  }, [id]);

  // 字段级问题跳转（F7）：切回字段 Tab 并打开该字段的编辑 Modal
  const jumpToField = (issue: EntityValidationIssue) => {
    if (issue.fieldId == null) return;
    const target = fields.find((f) => f.id === issue.fieldId);
    if (target) {
      setActiveTab('fields');
      openEdit(target);
    } else {
      message.warning(`字段 ${issue.fieldName ?? issue.fieldId} 可能已被删除，请刷新字段列表`);
    }
  };

  // 关系新建/编辑
  const openRelationCreate = () => {
    setEditingRelation(null);
    relationForm.resetFields();
    relationForm.setFieldsValue({ type: RELATION_TYPES[1] });
    setRelationModalOpen(true);
  };

  const openRelationEdit = (r: MetaRelation) => {
    setEditingRelation(r);
    relationForm.setFieldsValue({
      name: r.name, type: r.type, foreignKeyField: r.foreignKeyField,
      required: r.required, cascadeType: r.cascadeType,
    });
    setRelationModalOpen(true);
  };

  const handleRelationSubmit = async () => {
    if (!id) return;
    const values = await relationForm.validateFields();
    if (editingRelation) {
      const body: UpdateMetaRelationReq = {
        name: values.name, type: values.type,
        foreignKeyField: values.foreignKeyField,
        required: values.required, cascadeType: values.cascadeType,
      };
      const res = await metadataRelationApi.update(editingRelation.id, body);
      if (res.code === 200) { message.success('更新成功'); setRelationModalOpen(false); loadRelations(); }
      else message.error(errorMessage(res));
    } else {
      const body: CreateMetaRelationReq = { ...values, sourceEntityId: id! };
      const res = await metadataRelationApi.create(body);
      if (res.code === 200) { message.success('创建成功'); setRelationModalOpen(false); loadRelations(); }
      else message.error(errorMessage(res));
    }
  };

  const handleRelationDelete = async (relId: number) => {
    const res = await metadataRelationApi.delete(relId);
    if (res.code === 200) { message.success('已删除'); loadRelations(); }
    else message.error(res.message);
  };

  // 字段向导（F10）提交：第一步选类型、第二步填属性，最终走同一 create API
  const handleWizardSubmit = async (values: CreateMetaFieldReq) => {
    if (!id) return;
    const res = await metadataFieldApi.create(id!, values);
    if (res.code === 200) {
      message.success('创建成功');
      setWizardOpen(false);
      loadFields();
    } else {
      message.error(errorMessage(res));
    }
  };

  // 打开编辑字段
  const openEdit = (field: MetaField) => {
    setEditingField(field);
    // 编辑时只允许修改显示名、长度、必填、排序
    form.setFieldsValue({
      displayName: field.displayName,
      description: field.comment,
      length: field.length,
      required: field.required,
      sortOrder: field.sortOrder ?? 9999,
    });
    setModalOpen(true);
  };

  // 查看字段详情
  const openDetail = (field: MetaField) => {
    setEditingField(field);
    setDetailModalOpen(true);
  };

  // 提交字段
  const handleSubmit = async () => {
    if (!id) return;
    const values = await form.validateFields();
    try {
      if (editingField) {
        const body: UpdateMetaFieldReq = {
          displayName: values.displayName,
          type: editingField.type,
          comment: values.description,
          length: values.length,
          required: values.required,
          sortOrder: values.sortOrder,
        };
        const res = await metadataFieldApi.update(id!, editingField.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          loadFields();
        } else {
          message.error(errorMessage(res));
        }
      } else {
        const body: CreateMetaFieldReq = {
          name: values.name,
          code: values.code,
          displayName: values.displayName,
          type: values.type,
          length: values.length,
          required: values.required,
          comment: values.description,
          sortOrder: values.sortOrder ?? 9999,
        };
        const res = await metadataFieldApi.create(id!, body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          loadFields();
        } else {
          message.error(errorMessage(res));
        }
      }
    } catch {
      message.error('保存失败');
    }
  };

  // 删除字段
  const handleDeleteField = async (fieldId: number) => {
    if (!id) return;
    const res = await metadataFieldApi.delete(id!, fieldId);
    if (res.code === 200) {
      message.success('已删除');
      loadFields();
    } else {
      message.error(res.message);
    }
  };

  // 字段类型图标
  const fieldTypeIcon = (type: string) => {
    const iconMap: Record<string, React.ReactNode> = {
      STRING: <FieldStringOutlined style={{ color: '#52c41a' }} />,
      INTEGER: <FieldNumberOutlined style={{ color: '#1890ff' }} />,
      LONG: <FieldNumberOutlined style={{ color: '#13c2c2' }} />,
      DECIMAL: <FieldNumberOutlined style={{ color: '#722ed1' }} />,
      BOOLEAN: <FieldNumberOutlined style={{ color: '#fa8c16' }} />,
      DATE: <CalendarOutlined style={{ color: '#eb2f96' }} />,
      DATETIME: <CalendarOutlined style={{ color: '#f5222d' }} />,
    };
    return iconMap[type] || <FieldStringOutlined />;
  };

  // 字段表格列定义
  const fieldColumns: ColumnsType<MetaField> = [
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 60,
      render: (v: number) => <Text type="secondary">{v ?? '—'}</Text>,
    },
    {
      title: '字段名',
      dataIndex: 'displayName',
      key: 'displayName',
      width: 160,
      ellipsis: true,
      render: (name: string, record) => (
        <Space>
          {fieldTypeIcon(record.type)}
          <Typography.Text
            ellipsis={{ tooltip: name }}
            style={{ color: '#1668dc', cursor: 'pointer', maxWidth: 120, marginBottom: 0 }}
            onClick={() => openDetail(record)}
          >
            {name}
          </Typography.Text>
        </Space>
      ),
    },
    { title: '编码', dataIndex: 'code', key: 'code', width: 120, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (t: string) => {
        const colorMap: Record<string, string> = {
          STRING: 'green', INTEGER: 'blue', LONG: 'cyan',
          DECIMAL: 'purple', BOOLEAN: 'orange', TEXT: 'geekblue',
          DATE: 'magenta', DATETIME: 'volcano',
        };
        return <Tag color={colorMap[t] ?? 'default'}>{t}</Tag>;
      },
    },
    {
      title: '长度',
      dataIndex: 'length',
      key: 'length',
      width: 60,
      render: (v: number | null) => v ?? '—',
    },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 50,
      render: (v: boolean) => v ? <Tag color="red">是</Tag> : <Tag>否</Tag>,
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record) => (
        <Space>
          <Tooltip title="编辑字段属性">
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} />
          </Tooltip>
          {entity?.status === 1 ? (
            // 已发布实体（UC-W6）：字段删除=结构破坏，前端禁用 + 引导；后端域校验兜底
            <Tooltip title="已发布实体不可删除字段；结构性变更须先归档或回退草稿">
              <Button type="link" size="small" danger icon={<DeleteOutlined />} disabled />
            </Tooltip>
          ) : (
            <Popconfirm
              title="确认删除？"
              description="删除字段可能导致运行时数据异常，请谨慎操作。"
              onConfirm={() => handleDeleteField(record.id)}
            >
              <Tooltip title="删除字段">
                <Button type="link" size="small" danger icon={<DeleteOutlined />} />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  if (!entity) return <div className="page"><Text>加载中...</Text></div>;

  return (
    <div className="page">
      {/* 面包屑导航 */}
      <Space style={{ marginBottom: 16 }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ padding: 0 }} onClick={() => navigate('/entities')}>
          返回实体列表
        </Button>
        <Text type="secondary">/</Text>
        <Text strong>{entity.displayName}</Text>
      </Space>

      {/* 实体基本信息卡片 */}
      <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: 16 } }}>
        <Row gutter={[16, 12]}>
          <Col span={6}>
            <Text type="secondary">编码</Text>
            <div><Text code>{entity.code}</Text></div>
          </Col>
          <Col span={6}>
            <Text type="secondary">数据库表</Text>
            <div><Text code>{entity.tableName}</Text></div>
          </Col>
          <Col span={4}>
            <Text type="secondary">交付模式</Text>
            <div><Tag color={entity.deliveryMode === 1 ? 'purple' : 'default'}>{DELIVERY_MODE[entity.deliveryMode ?? 0]}</Tag></div>
          </Col>
          <Col span={4}>
            <Text type="secondary">状态</Text>
            <div><Tag color={entity.status === 1 ? 'green' : 'blue'}>{ENTITY_STATUS[entity.status]}</Tag></div>
          </Col>
          <Col span={4}>
            <Text type="secondary">字段数</Text>
            <div><Badge count={fields.length} showZero style={{ backgroundColor: '#1890ff' }} /></div>
          </Col>
        </Row>
        {entity.description && (
          <Row style={{ marginTop: 8 }}>
            <Col span={24}>
              <Text type="secondary">描述：</Text>
              <Text>{entity.description}</Text>
            </Col>
          </Row>
        )}
      </Card>

      {/* 已发布受限编辑引导（UC-W6 / 2b F3）：后端域校验兜底，前端预先告知 */}
      {entity.status === 1 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="已发布实体"
          description="当前实体已发布：仅允许新增字段或修改非破坏属性（显示名/长度/描述/排序）；删除字段与结构性变更须先归档或回退草稿。新增字段将在下次发布后对运行时生效。"
        />
      )}

      {/* Tabs 布局（2b §6-4）：字段 / 关系 / 校验 三页合一，替代旧独立页 */}
      <Card size="small" styles={{ body: { paddingTop: 4 } }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          tabBarExtraContent={
            <Space style={{ paddingRight: 8 }}>
              {entity.status === 0 && (
                <Button type="primary" icon={<ThunderboltOutlined />} size="small" onClick={() => setPreviewOpen(true)}>
                  发布
                </Button>
              )}
              {activeTab === 'fields' && (
                <>
                  <Button icon={<ReloadOutlined />} size="small" onClick={loadFields}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} size="small" onClick={() => setWizardOpen(true)}>
                    新建字段
                  </Button>
                </>
              )}
              {activeTab === 'relations' && (
                <>
                  <Button icon={<ReloadOutlined />} size="small" onClick={loadRelations}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} size="small" onClick={openRelationCreate}>
                    新建关系
                  </Button>
                </>
              )}
              {activeTab === 'validation' && (
                <Button icon={<ReloadOutlined />} size="small" onClick={loadValidation}>重新校验</Button>
              )}
            </Space>
          }
          items={[
            {
              key: 'fields',
              label: (
                <Space size={4}>
                  <FieldStringOutlined />
                  <span>字段</span>
                  <Badge count={fields.length} style={{ backgroundColor: '#1890ff' }} />
                </Space>
              ),
              children: (
                <Table
                  rowKey="id"
                  loading={fieldLoading}
                  columns={fieldColumns}
                  dataSource={fields}
                  pagination={false}
                  size="small"
                  scroll={{ x: 'max-content' }}
                />
              ),
            },
            {
              key: 'relations',
              label: (
                <Space size={4}>
                  <ApartmentOutlined />
                  <span>关系</span>
                  <Badge count={relations.length} style={{ backgroundColor: '#722ed1' }} />
                </Space>
              ),
              children: (
                <Table
                  rowKey="id"
                  loading={relationLoading}
                  pagination={false}
                  size="small"
                  scroll={{ x: 'max-content' }}
                  dataSource={relations}
                  columns={[
                    { title: '关系名称', dataIndex: 'name', key: 'name', width: 160, ellipsis: true },
                    {
                      title: '方向',
                      key: 'direction',
                      width: 180,
                      render: (_, r) => {
                        const asSource = r.sourceEntityId === entity.id;
                        const other = asSource ? r.targetEntityId : r.sourceEntityId;
                        const otherName = allEntities.find((e) => e.id === other)?.displayName ?? String(other);
                        return (
                          <Space size={4}>
                            <Tag color={asSource ? 'blue' : 'purple'}>{asSource ? '作为源' : '作为目标'}</Tag>
                            <span>{asSource ? `→ ${otherName}` : `${otherName} →`}</span>
                          </Space>
                        );
                      },
                    },
                    { title: '类型', dataIndex: 'type', key: 'type', width: 120, render: (t: string) => <Tag>{t}</Tag> },
                    { title: '外键字段', dataIndex: 'foreignKeyField', key: 'foreignKeyField', width: 140, render: (v: string) => v || '—' },
                    {
                      title: '操作',
                      key: 'action',
                      width: 110,
                      render: (_, r) => (
                        <Space>
                          <Button type="link" size="small" onClick={() => openRelationEdit(r)}>编辑</Button>
                          <Popconfirm title="确认删除该关系？" onConfirm={() => handleRelationDelete(r.id)}>
                            <Button type="link" size="small" danger>删除</Button>
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                />
              ),
            },
            {
              key: 'validation',
              label: (
                <Space size={4}>
                  <CheckCircleOutlined />
                  <span>校验</span>
                  {issues && issues.some((i) => i.level === 'ERROR') && (
                    <Badge count={issues.filter((i) => i.level === 'ERROR').length} size="small" />
                  )}
                </Space>
              ),
              children: (
                <div style={{ paddingBottom: 12 }}>
                  {issues === null ? (
                    <Empty description="尚未校验">
                      <Button type="primary" size="small" loading={validationLoading} onClick={loadValidation}>
                        开始校验
                      </Button>
                    </Empty>
                  ) : (
                    <List
                      size="small"
                      loading={validationLoading}
                      dataSource={issues}
                      locale={{
                        emptyText: (
                          <Space>
                            <CheckCircleOutlined style={{ color: '#52c41a' }} />
                            <span>未发现问题，模型符合规范</span>
                          </Space>
                        ),
                      }}
                      renderItem={(issue) => (
                        <List.Item
                          actions={
                            issue.fieldId != null
                              ? [
                                  <Button key="jump" type="link" size="small" icon={<AimOutlined />} onClick={() => jumpToField(issue)}>
                                    定位字段
                                  </Button>,
                                ]
                              : undefined
                          }
                        >
                          <Space size={8} align="start">
                            {issue.level === 'ERROR' && <WarningOutlined style={{ color: '#ff4d4f', marginTop: 3 }} />}
                            {issue.level === 'WARNING' && <WarningOutlined style={{ color: '#faad14', marginTop: 3 }} />}
                            {issue.level === 'INFO' && <InfoCircleOutlined style={{ color: '#1890ff', marginTop: 3 }} />}
                            <div>
                              <Space size={6}>
                                <Tag color={issue.level === 'ERROR' ? 'red' : issue.level === 'WARNING' ? 'orange' : 'blue'}>
                                  {issue.level}
                                </Tag>
                                <Text code style={{ fontSize: 12 }}>{issue.code}</Text>
                                {issue.fieldName && <Text strong style={{ fontSize: 12 }}>{issue.fieldName}</Text>}
                              </Space>
                              <div><Text style={{ fontSize: 13 }}>{issue.message}</Text></div>
                            </div>
                          </Space>
                        </List.Item>
                      )}
                    />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* 编辑字段 Modal（新建走 FieldWizardModal，F10） */}
      <Modal
        title="编辑字段"
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        forceRender
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
            <Input placeholder="如：姓名、邮箱" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="字段的业务含义" />
          </Form.Item>
          <Form.Item name="length" label="长度">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="required" label="必填">
            <Select options={[
              { value: true, label: '是' },
              { value: false, label: '否' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 字段详情 Modal */}
      <Modal
        title="字段详情"
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={<Button onClick={() => setDetailModalOpen(false)}>关闭</Button>}
        width={480}
      >
        {editingField && (
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="名称">{editingField.name}</Descriptions.Item>
            <Descriptions.Item label="编码">{editingField.code}</Descriptions.Item>
            <Descriptions.Item label="显示名">{editingField.displayName}</Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag color={FIELD_TYPE_MAP[editingField.type]?.color}>{editingField.type}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="长度">{editingField.length ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="必填">{editingField.required ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="排序号">{editingField.sortOrder ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{editingField.createdAt ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{editingField.comment || '—'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* 字段向导（F10）：分组类型面板 → 属性 */}
      <FieldWizardModal
        open={wizardOpen}
        onCancel={() => setWizardOpen(false)}
        onSubmit={handleWizardSubmit}
      />

      {/* 新建/编辑关系 Modal（关系 Tab，§6-4 并入详情） */}
      <Modal
        title={editingRelation ? '编辑关系' : '新建关系'}
        open={relationModalOpen}
        onOk={handleRelationSubmit}
        onCancel={() => setRelationModalOpen(false)}
        destroyOnHidden
        forceRender
        width={520}
      >
        <Form form={relationForm} layout="vertical">
          <Form.Item name="name" label="关系名称" rules={[{ required: true }]}>
            <Input placeholder="如：客户拥有订单" />
          </Form.Item>
          {!editingRelation && (
            <>
              <Form.Item label="源实体">
                <Input value={entity.displayName} disabled />
              </Form.Item>
              <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true, message: '请选择目标实体' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={allEntities
                    .filter((e) => e.id !== entity.id)
                    .map((e) => ({ value: e.id, label: `${e.displayName} (${e.code})` }))}
                />
              </Form.Item>
            </>
          )}
          <Form.Item name="type" label="关系类型" rules={[{ required: true }]}>
            <Select options={RELATION_TYPES.map((t) => ({ value: t, label: t }))} />
          </Form.Item>
          <Form.Item name="foreignKeyField" label="外键字段">
            <Input placeholder="如：customer_id" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 发布摘要预览（UC-W7）：详情页发布入口 */}
      <PublishPreviewModal
        open={previewOpen}
        entityId={entity?.id ?? null}
        entityName={entity?.displayName}
        onClose={() => setPreviewOpen(false)}
        onPublished={() => {
          metadataEntityApi.detail(id!).then((res) => {
            if (res.code === 200) setEntity(res.data);
          });
          loadFields();
        }}
      />
    </div>
  );
};

export default EntityDetail;
