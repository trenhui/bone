import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button, Card, Descriptions, Form, Input, InputNumber, Modal,
  Popconfirm, Row, Col, Select, Space, Table, Tag, Typography, message, Badge, Tooltip,
} from 'antd';
import {
  PlusOutlined, ArrowLeftOutlined, EditOutlined, DeleteOutlined, FieldStringOutlined,
  ReloadOutlined, FieldNumberOutlined, CalendarOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { metadataEntityApi, metadataFieldApi } from '../services/metadataApi';
import type { MetaEntity, MetaField, CreateMetaFieldReq, UpdateMetaFieldReq } from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, FIELD_TYPES, FIELD_TYPE_MAP } from '../types';

const { Text } = Typography;

const EntityDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [entity, setEntity] = useState<MetaEntity | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [fieldLoading, setFieldLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingField, setEditingField] = useState<MetaField | null>(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  // 加载实体详情
  useEffect(() => {
    if (!id) return;
    metadataEntityApi.detail(Number(id)).then((res) => {
      if (res.code === 200) setEntity(res.data);
      else message.error(res.message);
    });
  }, [id]);

  // 加载字段列表
  const loadFields = useCallback(async () => {
    if (!id) return;
    setFieldLoading(true);
    try {
      const res = await metadataFieldApi.page(Number(id), { pageNum: 1, pageSize: 200 });
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

  // 打开新建字段
  const openCreate = () => {
    setEditingField(null);
    form.resetFields();
    form.setFieldsValue({ required: false, type: 'STRING' });
    setModalOpen(true);
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
        const res = await metadataFieldApi.update(Number(id), editingField.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          loadFields();
        } else {
          message.error(res.message);
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
        const res = await metadataFieldApi.create(Number(id), body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          loadFields();
        } else {
          message.error(res.message);
        }
      }
    } catch {
      message.error('保存失败');
    }
  };

  // 删除字段
  const handleDeleteField = async (fieldId: number) => {
    if (!id) return;
    const res = await metadataFieldApi.delete(Number(id), fieldId);
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
      render: (name: string, record) => (
        <Space>
          {fieldTypeIcon(record.type)}
          <Button type="link" style={{ padding: 0 }} onClick={() => openDetail(record)}>
            {name}
          </Button>
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
          <Popconfirm
            title="确认删除？"
            description="删除字段可能导致运行时数据异常，请谨慎操作。"
            onConfirm={() => handleDeleteField(record.id)}
          >
            <Tooltip title="删除字段">
              <Button type="link" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
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

      {/* Tab 布局：内联字段管理为主 */}
      <Card
        size="small"
        title={
          <Space>
            <FieldStringOutlined />
            <span>字段管理</span>
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} size="small" onClick={loadFields}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} size="small" onClick={openCreate}>新建字段</Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={fieldLoading}
          columns={fieldColumns}
          dataSource={fields}
          pagination={false}
          size="small"
        />
      </Card>

      {/* 新建/编辑字段 Modal */}
      <Modal
        title={editingField ? '编辑字段' : '新建字段'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        width={520}
      >
        <Form form={form} layout="vertical">
          {!editingField && (
            <>
              <Form.Item name="name" label="名称" rules={[{ required: true }]} tooltip="英文名称，如 firstName">
                <Input placeholder="如：firstName, email" />
              </Form.Item>
              <Form.Item name="code" label="编码" rules={[{ required: true }]} tooltip="全局唯一，一旦创建不可修改">
                <Input placeholder="如：first_name, email" />
              </Form.Item>
              <Form.Item name="type" label="类型" rules={[{ required: true }]}>
                <Select options={FIELD_TYPES.map((t) => ({
                  value: t,
                  label: `${FIELD_TYPE_MAP[t]?.icon ?? ''} ${t} - ${FIELD_TYPE_MAP[t]?.desc ?? ''}`,
                }))} />
              </Form.Item>
            </>
          )}
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
    </div>
  );
};

export default EntityDetail;
