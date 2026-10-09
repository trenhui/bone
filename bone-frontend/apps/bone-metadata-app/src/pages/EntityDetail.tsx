import { useCallback, useEffect, useState, createContext, useContext } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert, Button, Card, Descriptions, Empty, Form, Input, InputNumber, List, Modal,
  Popconfirm, Row, Col, Select, Space, Table, Tabs, Tag, Typography, message, Badge, Tooltip, Switch,
} from 'antd';
import {
  PlusOutlined, ArrowLeftOutlined, EditOutlined, DeleteOutlined, FieldStringOutlined,
  ReloadOutlined, FieldNumberOutlined, CalendarOutlined, ThunderboltOutlined,
  ApartmentOutlined, CheckCircleOutlined, WarningOutlined, InfoCircleOutlined, AimOutlined, TableOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { FormInstance } from 'antd';
import { errorMessage, metadataEntityApi, metadataFieldApi, metadataRelationApi } from '../services/metadataApi';
import { getLastPublish, setLastPublish, isDirtySincePublish } from '../utils/publishState';
import PublishPreviewModal from '../components/PublishPreviewModal';
import PublishPreviewPanel from '../components/PublishPreviewPanel';
import FieldWizardModal from '../components/FieldWizardModal';
import InterfaceTab from '../components/InterfaceTab';
import RuntimeDataManagement from './RuntimeDataManagement';
import { touchRecent } from '../utils/recent';
import type {
  EntityValidationIssue, MetaEntity, MetaField, MetaRelation,
  CreateMetaFieldReq, CreateMetaRelationReq, UpdateMetaFieldReq, UpdateMetaRelationReq,
} from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, FIELD_TYPE_MAP, RELATION_TYPES, DATA_CLASSIFICATION_MAP, SENSITIVITY_LEVEL_MAP, META_DELIVERY_RUNTIME, META_ENTITY_PUBLISHED } from '../types';

const { Text, Title } = Typography;

const EditableContext = createContext<FormInstance | null>(null);

interface EditableRowProps {
  record?: MetaField;
  onSave?: (field: MetaField, patch: Partial<MetaField>) => Promise<boolean>;
  [key: string]: unknown;
}

const EditableRow: React.FC<EditableRowProps> = ({ ...restProps }) => {
  const [form] = Form.useForm();
  return (
    <Form form={form} component={false}>
      <EditableContext.Provider value={form}>
        <tr {...(restProps as React.HTMLAttributes<HTMLTableRowElement>)} />
      </EditableContext.Provider>
    </Form>
  );
};

interface EditableCellProps {
  editable?: boolean;
  cellType?: 'text' | 'number' | 'boolean';
  dataIndex?: string;
  record?: MetaField;
  onSave?: (field: MetaField, patch: Partial<MetaField>) => Promise<boolean>;
  className?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode;
  [key: string]: unknown;
}

const EditableCell: React.FC<EditableCellProps> = (props) => {
  const { editable, cellType = 'text', dataIndex, record, onSave, children, className, style, ...restProps } = props;
  const form = useContext(EditableContext);
  const [editing, setEditing] = useState(false);

  const save = async () => {
    if (!record || !dataIndex || !onSave || !form) return;
    try {
      const values = await form.validateFields();
      const ok = await onSave(record, { [dataIndex]: values[dataIndex] } as Partial<MetaField>);
      if (ok) setEditing(false);
    } catch {
      // 校验失败或被取消：保持编辑态，不丢改动
    }
  };

  if (editing && record && dataIndex && form) {
    const current = (record as unknown as Record<string, unknown>)[dataIndex];
    let input: React.ReactNode;
    if (cellType === 'boolean') {
      input = (
        <Form.Item name={dataIndex} valuePropName="checked" initialValue={!!current} style={{ margin: 0 }}>
          <Switch
            autoFocus
            checkedChildren="是"
            unCheckedChildren="否"
            onChange={(checked) => { form.setFieldsValue({ [dataIndex]: checked }); save(); }}
          />
        </Form.Item>
      );
    } else if (cellType === 'number') {
      input = (
        <Form.Item name={dataIndex} initialValue={(current as number | undefined) ?? undefined} style={{ margin: 0 }}>
          <InputNumber autoFocus style={{ width: '100%' }} min={0} onPressEnter={save} onBlur={save} />
        </Form.Item>
      );
    } else {
      input = (
        <Form.Item name={dataIndex} initialValue={(current as string | undefined) ?? ''} style={{ margin: 0 }}>
          <Input autoFocus onPressEnter={save} onBlur={save} />
        </Form.Item>
      );
    }
    return <td className={className} style={style}>{input}</td>;
  }

  const childNode = editable ? (
    <div onClick={() => setEditing(true)} title="点击行内编辑" style={{ cursor: 'pointer', minHeight: 24, padding: '2px 0' }}>
      {children}
    </div>
  ) : children;

  return <td className={className} style={style} {...(restProps as React.TdHTMLAttributes<HTMLTableCellElement>)}>{childNode}</td>;
};

const EntityDetail: React.FC = () => {
  const { id, appId, moduleId } = useParams<{ id: string; appId?: string; moduleId?: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form] = Form.useForm();

  // 顶/内 Tab 状态（与 URL ?tab= 同步，刷新/深链不丢上下文）
  const outerTab = searchParams.get('tab') || 'structure';
  const setOuterTab = (t: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', t);
    setSearchParams(next, { replace: true });
  };
  const [innerTab, setInnerTab] = useState('fields');

  const [entity, setEntity] = useState<MetaEntity | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingField, setEditingField] = useState<MetaField | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);

  // 关系
  const [relations, setRelations] = useState<MetaRelation[]>([]);
  const [relationLoading, setRelationLoading] = useState(false);
  const [relationModalOpen, setRelationModalOpen] = useState(false);
  const [editingRelation, setEditingRelation] = useState<MetaRelation | null>(null);
  const [relationForm] = Form.useForm();

  // 校验
  const [issues, setIssues] = useState<EntityValidationIssue[] | null>(null);
  const [validationLoading, setValidationLoading] = useState(false);

  // 关系目标实体候选
  const [allEntities, setAllEntities] = useState<MetaEntity[]>([]);

  useEffect(() => {
    if (!id) return;
    metadataEntityApi.detail(id!).then((res) => {
      if (res.code === 200) {
        setEntity(res.data);
        touchRecent({ id: res.data.id, code: res.data.code, displayName: res.data.displayName, appId, moduleId });
      } else message.error(res.message);
    });
  }, [id, appId, moduleId]);

  const loadFields = useCallback(async () => {
    if (!id) return;
    setFieldLoading(true);
    try {
      const res = await metadataFieldApi.page(id!, { page: 1, size: 200 });
      if (res.code === 200) {
        const sorted = (res.data.records || []).sort((a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999));
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
    metadataEntityApi.page({ page: 1, size: 200 }).then((res) => {
      if (res.code === 200) setAllEntities(res.data.records);
    });
  }, []);

  const loadRelations = useCallback(async () => {
    if (!id) return;
    setRelationLoading(true);
    try {
      const [asSource, asTarget] = await Promise.all([
        metadataRelationApi.page({ page: 1, size: 200, sourceEntityId: id! }),
        metadataRelationApi.page({ page: 1, size: 200, targetEntityId: id! }),
      ]);
      const merged = new Map<string, MetaRelation>();
      [...(asSource.code === 200 ? asSource.data.records : []),
        ...(asTarget.code === 200 ? asTarget.data.records : [])].forEach((r) => merged.set(r.id, r));
      setRelations([...merged.values()]);
    } catch {
      message.error('加载关系失败');
    } finally {
      setRelationLoading(false);
    }
  }, [id]);

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

  const saveFieldInline = useCallback(async (field: MetaField, patch: Partial<MetaField>) => {
    if (!id) return false;
    const body: UpdateMetaFieldReq = {
      displayName: patch.displayName ?? field.displayName,
      type: field.type,
      length: patch.length !== undefined ? patch.length : field.length,
      required: patch.required !== undefined ? patch.required : field.required,
      sortOrder: patch.sortOrder !== undefined ? patch.sortOrder : field.sortOrder,
      comment: field.comment,
      dataClassification: field.dataClassification,
      pii: field.pii ?? false,
      sensitivityLevel: field.sensitivityLevel,
      dataSteward: field.dataSteward,
      businessTerm: field.businessTerm,
      sourceSystem: field.sourceSystem,
      enumValues: field.enumValues,
      validationRules: field.validationRules,
    };
    const res = await metadataFieldApi.update(id, field.id, body);
    if (res.code === 200) {
      setFields((prev) => prev.map((f) => (f.id === field.id ? { ...f, ...patch } : f)));
      message.success('已保存');
      return true;
    }
    message.error(errorMessage(res));
    return false;
  }, [id]);

  const jumpToField = (issue: EntityValidationIssue) => {
    if (issue.fieldId == null) return;
    const target = fields.find((f) => f.id === issue.fieldId);
    if (target) {
      setOuterTab('structure');
      setInnerTab('fields');
      openEdit(target);
    } else {
      message.warning(`字段 ${issue.fieldName ?? issue.fieldId} 可能已被删除，请刷新字段列表`);
    }
  };

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
      const body: UpdateMetaRelationReq = { name: values.name, type: values.type, foreignKeyField: values.foreignKeyField, required: values.required, cascadeType: values.cascadeType };
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

  const handleRelationDelete = async (relId: string) => {
    const res = await metadataRelationApi.delete(relId);
    if (res.code === 200) { message.success('已删除'); loadRelations(); }
    else message.error(res.message);
  };

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

  const handleFieldModalCancel = () => {
    if (form.isFieldsTouched()) {
      Modal.confirm({
        title: '有未保存的修改',
        content: '关闭将丢失尚未保存的字段修改，确认放弃？',
        okText: '放弃修改', okButtonProps: { danger: true }, cancelText: '继续编辑',
        onOk: () => setModalOpen(false),
      });
    } else {
      setModalOpen(false);
    }
  };

  const handleRelationModalCancel = () => {
    if (relationForm.isFieldsTouched()) {
      Modal.confirm({
        title: '有未保存的修改',
        content: '关闭将丢失尚未保存的关系修改，确认放弃？',
        okText: '放弃修改', okButtonProps: { danger: true }, cancelText: '继续编辑',
        onOk: () => setRelationModalOpen(false),
      });
    } else {
      setRelationModalOpen(false);
    }
  };

  const openEdit = (field: MetaField) => {
    setEditingField(field);
    form.setFieldsValue({
      displayName: field.displayName, description: field.comment, length: field.length,
      required: field.required, sortOrder: field.sortOrder ?? 9999,
      dataClassification: field.dataClassification, pii: field.pii ?? false,
      sensitivityLevel: field.sensitivityLevel, dataSteward: field.dataSteward,
      businessTerm: field.businessTerm, sourceSystem: field.sourceSystem,
      enumValues: field.enumValues, validationRules: field.validationRules,
    });
    setModalOpen(true);
  };

  const openDetail = (field: MetaField) => {
    setEditingField(field);
    setDetailModalOpen(true);
  };

  const handleSubmit = async () => {
    if (!id) return;
    const values = await form.validateFields();
    try {
      if (editingField) {
        const body: UpdateMetaFieldReq = {
          displayName: values.displayName, type: editingField.type, comment: values.description,
          length: values.length, required: values.required, sortOrder: values.sortOrder,
          dataClassification: values.dataClassification ?? editingField.dataClassification ?? null,
          pii: values.pii ?? editingField.pii ?? false,
          sensitivityLevel: values.sensitivityLevel ?? editingField.sensitivityLevel ?? null,
          dataSteward: values.dataSteward ?? editingField.dataSteward ?? null,
          businessTerm: values.businessTerm ?? editingField.businessTerm ?? null,
          sourceSystem: values.sourceSystem ?? editingField.sourceSystem ?? null,
          enumValues: values.enumValues ?? editingField.enumValues ?? null,
          validationRules: values.validationRules ?? editingField.validationRules ?? null,
        };
        const res = await metadataFieldApi.update(id!, editingField.id, body);
        if (res.code === 200) { message.success('更新成功'); setModalOpen(false); loadFields(); }
        else message.error(errorMessage(res));
      } else {
        const body: CreateMetaFieldReq = {
          name: values.name, code: values.code, displayName: values.displayName, type: values.type,
          length: values.length, required: values.required, comment: values.description,
          sortOrder: values.sortOrder ?? 9999,
          dataClassification: values.dataClassification ?? null, pii: values.pii ?? false,
          sensitivityLevel: values.sensitivityLevel ?? null, dataSteward: values.dataSteward ?? null,
          businessTerm: values.businessTerm ?? null, sourceSystem: values.sourceSystem ?? null,
          enumValues: values.enumValues ?? null, validationRules: values.validationRules ?? null,
        };
        const res = await metadataFieldApi.create(id!, body);
        if (res.code === 200) { message.success('创建成功'); setModalOpen(false); loadFields(); }
        else message.error(errorMessage(res));
      }
    } catch {
      message.error('保存失败');
    }
  };

  const handleDeleteField = async (fieldId: string) => {
    if (!id) return;
    const res = await metadataFieldApi.delete(id!, fieldId);
    if (res.code === 200) { message.success('已删除'); loadFields(); }
    else message.error(res.message);
  };

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

  const fieldColumns: ColumnsType<MetaField> = [
    { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 60, onCell: (record) => ({ record, dataIndex: 'sortOrder', cellType: 'number', onSave: saveFieldInline, editable: true } as unknown as React.TdHTMLAttributes<HTMLTableCellElement>), render: (v: number) => <Text type="secondary">{v ?? '—'}</Text> },
    { title: '字段名', dataIndex: 'displayName', key: 'displayName', width: 160, ellipsis: true, onCell: (record) => ({ record, dataIndex: 'displayName', cellType: 'text', onSave: saveFieldInline, editable: true } as unknown as React.TdHTMLAttributes<HTMLTableCellElement>), render: (name: string, record) => (<Space>{fieldTypeIcon(record.type)}<Typography.Text ellipsis={{ tooltip: name }} style={{ maxWidth: 120, marginBottom: 0 }}>{name}</Typography.Text></Space>) },
    { title: '编码', dataIndex: 'code', key: 'code', width: 120, ellipsis: true },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => { const colorMap: Record<string, string> = { STRING: 'green', INTEGER: 'blue', LONG: 'cyan', DECIMAL: 'purple', BOOLEAN: 'orange', TEXT: 'geekblue', DATE: 'magenta', DATETIME: 'volcano', ENUM: 'gold' }; return <Tag color={colorMap[t] ?? 'default'}>{t}</Tag>; } },
    { title: '长度', dataIndex: 'length', key: 'length', width: 60, onCell: (record) => ({ record, dataIndex: 'length', cellType: 'number', onSave: saveFieldInline, editable: true } as unknown as React.TdHTMLAttributes<HTMLTableCellElement>), render: (v: number | null) => v ?? '—' },
    { title: '必填', dataIndex: 'required', key: 'required', width: 50, onCell: (record) => ({ record, dataIndex: 'required', cellType: 'boolean', onSave: saveFieldInline, editable: true } as unknown as React.TdHTMLAttributes<HTMLTableCellElement>), render: (v: boolean) => v ? <Tag color="red">是</Tag> : <Tag>否</Tag> },
    { title: '数据治理', key: 'governance', width: 200, render: (_: unknown, record: MetaField) => (<Space size={4} wrap>{record.pii && <Tag color="red">PII</Tag>}{record.dataClassification && <Tag color={DATA_CLASSIFICATION_MAP[record.dataClassification]?.color ?? 'default'}>{DATA_CLASSIFICATION_MAP[record.dataClassification]?.label ?? record.dataClassification}</Tag>}{record.sensitivityLevel && <Tag color={SENSITIVITY_LEVEL_MAP[record.sensitivityLevel]?.color ?? 'default'}>{record.sensitivityLevel}</Tag>}{record.dataSteward && <Tag color="blue">管家:{record.dataSteward}</Tag>}</Space>) },
    { title: '操作', key: 'action', width: 170, render: (_, record) => (<Space><Tooltip title="查看字段详情"><Button type="link" size="small" onClick={() => openDetail(record)}>查看</Button></Tooltip><Tooltip title="编辑字段属性"><Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} /></Tooltip>{entity?.status === 1 ? (<Tooltip title="已发布实体不可删除字段；结构性变更须先归档或回退草稿"><Button type="link" size="small" danger icon={<DeleteOutlined />} disabled /></Tooltip>) : (<Popconfirm title="确认删除？" description="删除字段可能导致运行时数据异常，请谨慎操作。" onConfirm={() => handleDeleteField(record.id)}><Tooltip title="删除字段"><Button type="link" size="small" danger icon={<DeleteOutlined />} /></Tooltip></Popconfirm>)}</Space>) },
  ];

  if (!entity) return <div className="page"><Text>加载中...</Text></div>;

  const dataPath = appId && moduleId
    ? `/apps/${appId}/modules/${moduleId}/entities/${id}/data`
    : `/entities/${id}/data`;

  // 返回实体列表：从 scoped 路由（appId/moduleId）进入时回跳到对应模块实体列表，
  // 避免掉上下文；从兼容路由 /entities/:id 进入时回跳 /entities（W1b 菜单收敛同步项）。
  const backPath = appId && moduleId
    ? `/apps/${appId}/modules/${moduleId}/entities`
    : '/entities';

  const isRuntimePublished = entity.deliveryMode === META_DELIVERY_RUNTIME && entity.status === META_ENTITY_PUBLISHED;

  // 脏态（MVP 近似）：已发布实体，结构 updatedAt 晚于最近一次本地记录的发布时刻 ⇒ 待重新发布
  const lastPublishAt = getLastPublish(id ?? '');
  const dirtySincePublish = entity.status === 1 && isDirtySincePublish(entity.updatedAt, lastPublishAt);
  /**
   * 脏态项数（设计 §3.2「未发布 · N 项」）：发布之后新增/修改过的字段数。
   * 计数为 0 时（后端未回传字段 updatedAt 等）退化为定性 Tag，绝不显示误导性的「0 项」。
   */
  const dirtyFieldCount = fields.filter((f) => isDirtySincePublish(f.updatedAt, lastPublishAt)).length;

  // 发布成功统一步骤：记录发布时刻（驱动脏态徽标由「有更新待重新发布」转绿）+ 回拉最新实体与字段。
  // 发布 Tab 与顶栏 Modal 两个入口必须共用，否则从 Modal 发布后脏态不刷新（同一事实两个写口 ⇒ 漂移）。
  const handlePublished = () => {
    setLastPublish(id ?? '');
    metadataEntityApi.detail(id!).then((res) => {
      if (res.code === 200) setEntity(res.data);
    });
    loadFields();
  };

  const goInterface = () => setOuterTab('ui');
  const goData = isRuntimePublished ? () => navigate(dataPath) : undefined;
  const goAddField = () => {
    setOuterTab('structure');
    setWizardOpen(true);
  };

  return (
    <div className="page">
      <Space style={{ marginBottom: 16 }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ padding: 0 }} onClick={() => navigate(backPath)}>返回实体列表</Button>
        <Text type="secondary">/</Text>
        <Text strong>{entity.displayName}</Text>
      </Space>

      {/* 顶栏：基本信息 + 常驻发布 + 打开数据（B1：已发布实体加字段后仍需可再发布） */}
      <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: 16 } }}>
        <Row gutter={[16, 12]} align="middle">
          <Col flex="auto">
            <Row gutter={[16, 12]}>
              <Col span={6}><Text type="secondary">编码</Text><div><Text code>{entity.code}</Text></div></Col>
              <Col span={6}><Text type="secondary">数据库表</Text><div><Text code>{entity.tableName}</Text></div></Col>
              <Col span={4}><Text type="secondary">交付模式</Text><div><Tag color={entity.deliveryMode === 1 ? 'purple' : 'default'}>{DELIVERY_MODE[entity.deliveryMode ?? 0]}</Tag></div></Col>
              <Col span={4}><Text type="secondary">状态</Text><div><Tag color={entity.status === 1 ? 'green' : 'blue'}>{ENTITY_STATUS[entity.status]}</Tag></div></Col>
              <Col span={4}><Text type="secondary">字段数</Text><div><Badge count={fields.length} showZero style={{ backgroundColor: '#1890ff' }} /></div></Col>
              <Col span={4}><Text type="secondary">发布态</Text><div>
                {entity.status === 0 ? (
                  <Tag color="orange">草稿</Tag>
                ) : dirtySincePublish && dirtyFieldCount > 0 ? (
                  <Tag color="gold">未发布 · {dirtyFieldCount} 项</Tag>
                ) : dirtySincePublish ? (
                  <Tag color="gold">有更新待重新发布</Tag>
                ) : (
                  <Tag color="green">已发布且最新</Tag>
                )}
              </div></Col>
            </Row>
            {entity.description && (<Row style={{ marginTop: 8 }}><Col span={24}><Text type="secondary">描述：</Text><Text>{entity.description}</Text></Col></Row>)}
            {/* 脏态条（设计 §3.2）：显式告知「线上仍跑上一版快照」，并就近给出重新发布入口 */}
            {dirtySincePublish && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 12 }}
                message={
                  dirtyFieldCount > 0
                    ? `有 ${dirtyFieldCount} 项结构变更尚未发布，线上仍跑上一版快照`
                    : '结构有更新尚未发布，线上仍跑上一版快照'
                }
                action={
                  <Button size="small" type="primary" icon={<ThunderboltOutlined />} onClick={() => setPreviewOpen(true)}>
                    重新发布
                  </Button>
                }
              />
            )}
          </Col>
          <Col>
            <Space>
              {/* W0-8：常驻「打开数据」——未发布/非运行时为 disabled + tooltip，而非隐藏按钮，
                  让用户知道入口存在以及为何点不了（禁「按钮凭空消失」型静默） */}
              <Tooltip title={isRuntimePublished ? '打开本实体的运行时数据' : '发布后（运行时交付模式）可录入数据'}>
                <Button
                  icon={<TableOutlined />}
                  disabled={!isRuntimePublished}
                  onClick={() => navigate(dataPath)}
                >
                  打开数据
                </Button>
              </Tooltip>
              <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => setPreviewOpen(true)}>
                {entity.status === 1 ? '重新发布' : '发布'}
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {entity.status === 1 && (
        <Alert type="info" showIcon style={{ marginBottom: 12 }} message="已发布实体"
          description="当前实体已发布：仅允许新增字段或修改非破坏属性；删除字段与结构性变更须先归档或回退草稿。新增字段将在下次发布后对运行时生效。" />
      )}

      <Card size="small" styles={{ body: { paddingTop: 4 } }}>
        <Tabs
          activeKey={outerTab}
          onChange={setOuterTab}
          tabBarExtraContent={
            <Space style={{ paddingRight: 8 }}>
              {outerTab === 'structure' && innerTab === 'fields' && (
                <>
                  <Button icon={<ReloadOutlined />} size="small" onClick={loadFields}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} size="small" onClick={() => setWizardOpen(true)}>新建字段</Button>
                </>
              )}
              {outerTab === 'structure' && innerTab === 'relations' && (
                <>
                  <Button icon={<ReloadOutlined />} size="small" onClick={loadRelations}>刷新</Button>
                  <Button type="primary" icon={<PlusOutlined />} size="small" onClick={openRelationCreate}>新建关系</Button>
                </>
              )}
              {outerTab === 'structure' && innerTab === 'validation' && (
                <Button icon={<ReloadOutlined />} size="small" onClick={loadValidation}>重新校验</Button>
              )}
            </Space>
          }
          items={[
            {
              key: 'structure',
              label: (<Space size={4}><ApartmentOutlined /><span>结构</span><Badge count={fields.length + relations.length} style={{ backgroundColor: '#1890ff' }} /></Space>),
              children: (
                <Tabs
                  activeKey={innerTab}
                  onChange={setInnerTab}
                  items={[
                    {
                      key: 'fields',
                      label: (<Space size={4}><FieldStringOutlined /><span>字段</span><Badge count={fields.length} style={{ backgroundColor: '#1890ff' }} /></Space>),
                      children: (
                        <Table rowKey="id" loading={fieldLoading} columns={fieldColumns} dataSource={fields} pagination={false} size="small" scroll={{ x: 'max-content' }}
                          components={{ body: { row: EditableRow as unknown as React.ComponentType<React.HTMLAttributes<HTMLTableRowElement>>, cell: EditableCell as unknown as React.ComponentType<React.TdHTMLAttributes<HTMLTableCellElement>> } }}
                          onRow={(record) => ({ record, onSave: saveFieldInline } as unknown as React.HTMLAttributes<HTMLTableRowElement>)} />
                      ),
                    },
                    {
                      key: 'relations',
                      label: (<Space size={4}><ApartmentOutlined /><span>关系</span><Badge count={relations.length} style={{ backgroundColor: '#722ed1' }} /></Space>),
                      children: (
                        <Table rowKey="id" loading={relationLoading} pagination={false} size="small" scroll={{ x: 'max-content' }} dataSource={relations} columns={[
                          { title: '关系名称', dataIndex: 'name', key: 'name', width: 160, ellipsis: true },
                          { title: '方向', key: 'direction', width: 180, render: (_, r) => { const asSource = r.sourceEntityId === entity.id; const other = asSource ? r.targetEntityId : r.sourceEntityId; const otherName = allEntities.find((e) => e.id === other)?.displayName ?? String(other); return (<Space size={4}><Tag color={asSource ? 'blue' : 'purple'}>{asSource ? '作为源' : '作为目标'}</Tag><span>{asSource ? `→ ${otherName}` : `${otherName} →`}</span></Space>); } },
                          { title: '类型', dataIndex: 'type', key: 'type', width: 120, render: (t: string) => <Tag>{t}</Tag> },
                          { title: '外键字段', dataIndex: 'foreignKeyField', key: 'foreignKeyField', width: 140, render: (v: string) => v || '—' },
                          { title: '操作', key: 'action', width: 110, render: (_, r) => (<Space><Button type="link" size="small" onClick={() => openRelationEdit(r)}>编辑</Button><Popconfirm title="确认删除该关系？" onConfirm={() => handleRelationDelete(r.id)}><Button type="link" size="small" danger>删除</Button></Popconfirm></Space>) },
                        ]} />
                      ),
                    },
                    {
                      key: 'validation',
                      label: (<Space size={4}><CheckCircleOutlined /><span>校验</span>{issues && issues.some((i) => i.level === 'ERROR') && <Badge count={issues.filter((i) => i.level === 'ERROR').length} size="small" />}</Space>),
                      children: (
                        <div style={{ paddingBottom: 12 }}>
                          {issues === null ? (
                            <Empty description="尚未校验"><Button type="primary" size="small" loading={validationLoading} onClick={loadValidation}>开始校验</Button></Empty>
                          ) : (
                            <List size="small" loading={validationLoading} dataSource={issues}
                              locale={{ emptyText: (<Space><CheckCircleOutlined style={{ color: '#52c41a' }} /><span>未发现问题，模型符合规范</span></Space>) }}
                              renderItem={(issue) => (
                                <List.Item actions={issue.fieldId != null ? [<Button key="jump" type="link" size="small" icon={<AimOutlined />} onClick={() => jumpToField(issue)}>定位字段</Button>] : undefined}>
                                  <Space size={8} align="start">
                                    {issue.level === 'ERROR' && <WarningOutlined style={{ color: '#ff4d4f', marginTop: 3 }} />}
                                    {issue.level === 'WARNING' && <WarningOutlined style={{ color: '#faad14', marginTop: 3 }} />}
                                    {issue.level === 'INFO' && <InfoCircleOutlined style={{ color: '#1890ff', marginTop: 3 }} />}
                                    <div>
                                      <Space size={6}>
                                        <Tag color={issue.level === 'ERROR' ? 'red' : issue.level === 'WARNING' ? 'orange' : 'blue'}>{issue.level}</Tag>
                                        <Text code style={{ fontSize: 12 }}>{issue.code}</Text>
                                        {issue.fieldName && <Text strong style={{ fontSize: 12 }}>{issue.fieldName}</Text>}
                                      </Space>
                                      <div><Text style={{ fontSize: 13 }}>{issue.message}</Text></div>
                                    </div>
                                  </Space>
                                </List.Item>
                              )} />
                          )}
                        </div>
                      ),
                    },
                  ]}
                />
              ),
            },
            {
              key: 'ui',
              label: (<Space size={4}><TableOutlined /><span>界面</span></Space>),
              children: (
                <InterfaceTab
                  entityCode={entity.code}
                  fields={fields}
                  onAddField={() => {
                    setOuterTab('structure');
                    setInnerTab('fields');
                    setWizardOpen(true);
                  }}
                />
              ),
            },
            {
              key: 'data',
              label: (<Space size={4}><TableOutlined /><span>数据</span></Space>),
              children: <RuntimeDataManagement embeddedEntityId={id} />,
            },
            {
              key: 'publish',
              label: (<Space size={4}><ThunderboltOutlined /><span>发布</span></Space>),
              children: <PublishPreviewPanel entityId={id ?? ''} entityName={entity.displayName} onPublished={handlePublished} onViewInterface={goInterface} onOpenData={goData} onAddField={goAddField} />,
            },
          ]}
        />
      </Card>

      {/* 编辑字段 Modal */}
      <Modal title="编辑字段" open={modalOpen} onOk={handleSubmit} onCancel={handleFieldModalCancel} destroyOnHidden forceRender width={520}>
        <Form form={form} layout="vertical">
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}><Input placeholder="如：姓名、邮箱" /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={2} placeholder="字段的业务含义" /></Form.Item>
          <Form.Item name="length" label="长度"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="sortOrder" label="排序号"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="required" label="必填"><Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
          <Title level={5} style={{ marginTop: 8 }}>数据治理属性</Title>
          <Form.Item name="dataClassification" label="数据分级"><Select allowClear placeholder="请选择数据分级" options={Object.entries(DATA_CLASSIFICATION_MAP).map(([k, v]) => ({ value: k, label: `${v.label}（${k}）` }))} /></Form.Item>
          <Form.Item name="pii" label="个人敏感信息(PII)"><Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} /></Form.Item>
          <Form.Item name="sensitivityLevel" label="敏感级别"><Select allowClear placeholder="请选择敏感级别" options={Object.entries(SENSITIVITY_LEVEL_MAP).map(([k, v]) => ({ value: k, label: v.label }))} /></Form.Item>
          <Form.Item name="dataSteward" label="数据管家/责任人"><Input placeholder="如：数据治理组-张三" /></Form.Item>
          <Form.Item name="businessTerm" label="业务术语/数据标准"><Input placeholder="如：客户唯一标识" /></Form.Item>
          <Form.Item name="sourceSystem" label="来源系统（血缘）"><Input placeholder="如：交易系统" /></Form.Item>
          <Form.Item name="enumValues" label="枚举值/标准码表（JSON）"><Input.TextArea rows={2} placeholder='可选，如 [{"code":"M","label":"男"}]' /></Form.Item>
          <Form.Item name="validationRules" label="校验规则/质量规则（JSON）"><Input.TextArea rows={2} placeholder='可选，如 {"min":0,"max":120}' /></Form.Item>
        </Form>
      </Modal>

      {/* 字段详情 Modal */}
      <Modal title="字段详情" open={detailModalOpen} onCancel={() => setDetailModalOpen(false)} footer={<Button onClick={() => setDetailModalOpen(false)}>关闭</Button>} width={480}>
        {editingField && (
          <Descriptions column={2} size="small" bordered>
            <Descriptions.Item label="名称">{editingField.name}</Descriptions.Item>
            <Descriptions.Item label="编码">{editingField.code}</Descriptions.Item>
            <Descriptions.Item label="显示名">{editingField.displayName}</Descriptions.Item>
            <Descriptions.Item label="类型"><Tag color={FIELD_TYPE_MAP[editingField.type]?.color}>{editingField.type}</Tag></Descriptions.Item>
            <Descriptions.Item label="长度">{editingField.length ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="必填">{editingField.required ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="数据分级">{editingField.dataClassification ? <Tag color={DATA_CLASSIFICATION_MAP[editingField.dataClassification]?.color ?? 'default'}>{DATA_CLASSIFICATION_MAP[editingField.dataClassification]?.label ?? editingField.dataClassification}</Tag> : '—'}</Descriptions.Item>
            <Descriptions.Item label="PII">{editingField.pii ? <Tag color="red">是</Tag> : '否'}</Descriptions.Item>
            <Descriptions.Item label="敏感级别">{editingField.sensitivityLevel ? <Tag color={SENSITIVITY_LEVEL_MAP[editingField.sensitivityLevel]?.color ?? 'default'}>{editingField.sensitivityLevel}</Tag> : '—'}</Descriptions.Item>
            <Descriptions.Item label="数据管家">{editingField.dataSteward || '—'}</Descriptions.Item>
            <Descriptions.Item label="业务术语">{editingField.businessTerm || '—'}</Descriptions.Item>
            <Descriptions.Item label="来源系统">{editingField.sourceSystem || '—'}</Descriptions.Item>
            <Descriptions.Item label="排序号">{editingField.sortOrder ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{editingField.createdAt ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="枚举值/码表" span={2}>{editingField.enumValues || '—'}</Descriptions.Item>
            <Descriptions.Item label="校验/质量规则" span={2}>{editingField.validationRules || '—'}</Descriptions.Item>
            <Descriptions.Item label="描述" span={2}>{editingField.comment || '—'}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* 字段向导 */}
      <FieldWizardModal open={wizardOpen} onCancel={() => setWizardOpen(false)} onSubmit={handleWizardSubmit} />

      {/* 新建/编辑关系 Modal */}
      <Modal title={editingRelation ? '编辑关系' : '新建关系'} open={relationModalOpen} onOk={handleRelationSubmit} onCancel={handleRelationModalCancel} destroyOnHidden forceRender width={520}>
        <Form form={relationForm} layout="vertical">
          <Form.Item name="name" label="关系名称" rules={[{ required: true }]}><Input placeholder="如：客户拥有订单" /></Form.Item>
          {!editingRelation && (<><Form.Item label="源实体"><Input value={entity.displayName} disabled /></Form.Item><Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true, message: '请选择目标实体' }]}><Select showSearch optionFilterProp="label" options={allEntities.filter((e) => e.id !== entity.id).map((e) => ({ value: e.id, label: `${e.displayName} (${e.code})` }))} /></Form.Item></>)}
          <Form.Item name="type" label="关系类型" rules={[{ required: true }]}><Select options={RELATION_TYPES.map((t) => ({ value: t, label: t }))} /></Form.Item>
          <Form.Item name="foreignKeyField" label="外键字段"><Input placeholder="如：customer_id" /></Form.Item>
        </Form>
      </Modal>

      {/* 发布预览 Modal（顶栏发布入口） */}
      <PublishPreviewModal open={previewOpen} entityId={entity?.id ?? null} entityName={entity?.displayName} onClose={() => setPreviewOpen(false)} onPublished={handlePublished} onViewInterface={goInterface} onOpenData={goData} onAddField={goAddField} />
    </div>
  );
};

export default EntityDetail;
