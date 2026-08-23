import { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button, Breadcrumb, Card, Form, Input, InputNumber, Modal, Popconfirm, Select,
  Space, Table, Tag, Tree, message, Badge, Typography, Row, Col,
} from 'antd';
import {
  PlusOutlined, AppstoreOutlined, DatabaseOutlined, FolderOutlined,
  SearchOutlined, ApartmentOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import { metadataEntityApi } from '../services/metadataApi';
import type { CreateMetaEntityReq, MetaEntity, UpdateMetaEntityReq } from '../types';
import { DELIVERY_MODE, ENTITY_STATUS, META_DELIVERY_RUNTIME, META_ENTITY_PUBLISHED } from '../types';

const { Text } = Typography;

/** 模拟的模块树数据 */
const MODULE_TREE: DataNode[] = [
  { title: '全部实体', key: 'all', icon: <AppstoreOutlined /> },
  {
    title: '系统模块', key: 'system', icon: <FolderOutlined />,
    children: [
      { title: '用户管理', key: 'user', icon: <DatabaseOutlined />, isLeaf: true },
      { title: '权限管理', key: 'permission', icon: <DatabaseOutlined />, isLeaf: true },
    ],
  },
  {
    title: '业务模块', key: 'biz', icon: <FolderOutlined />,
    children: [
      { title: '订单管理', key: 'order', icon: <DatabaseOutlined />, isLeaf: true },
      { title: '商品管理', key: 'product', icon: <DatabaseOutlined />, isLeaf: true },
      { title: '客户管理', key: 'customer', icon: <DatabaseOutlined />, isLeaf: true },
    ],
  },
  {
    title: '主数据', key: 'masterdata', icon: <ApartmentOutlined />,
    children: [
      { title: '组织架构', key: 'org', icon: <DatabaseOutlined />, isLeaf: true },
      { title: '字典管理', key: 'dict', icon: <DatabaseOutlined />, isLeaf: true },
    ],
  },
];

// ----- types -----

interface EntityGroup {
  key: string;
  label: string;
  entities: MetaEntity[];
}

// ----- Component -----

const EntityManagement: React.FC = () => {
  const navigate = useNavigate();
  const { appId, moduleId } = useParams<{ appId: string; moduleId: string }>();
  const isScoped = !!appId && !!moduleId;

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<MetaEntity[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<number | null>(null);
  const [deliveryFilter, setDeliveryFilter] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<number | null>(null);
  const [selectedModule, setSelectedModule] = useState<string>(isScoped ? moduleId! : 'all');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MetaEntity | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { pageNum: page, pageSize };
      if (keyword) params.keyword = keyword;
      if (typeFilter !== null) params.type = typeFilter;
      if (deliveryFilter !== null) params.deliveryMode = deliveryFilter;
      if (statusFilter !== null) params.status = statusFilter;
      if (isScoped) {
        params.module = moduleId;
      } else if (selectedModule !== 'all') {
        params.module = selectedModule;
      }

      const res = await metadataEntityApi.page(params);
      if (res.code === 200) {
        setData(res.data.list);
        setTotal(res.data.total);
      } else {
        message.error(res.message || '加载失败');
      }
    } catch {
      message.error('无法连接元数据服务，请确认 bone-metadata-server :9001 已启动');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword, typeFilter, deliveryFilter, statusFilter, selectedModule]);

  useEffect(() => { load(); }, [load]);

  // ----- 模块分组（模拟分组；正式上线由后端返回）-----
  const grouped = useMemo<EntityGroup[]>(() => {
    const groups: Record<string, EntityGroup> = {
      uncategorized: { key: 'uncategorized', label: '未分组', entities: [] },
    };
    MODULE_TREE.forEach((node) => {
      if (node.children) {
        node.children.forEach((child) => {
          if (child.key && typeof child.key === 'string') {
            groups[child.key] = { key: child.key, label: child.title as string, entities: [] };
          }
        });
      }
    });
    data.forEach((entity) => {
      const moduleKey = (entity as Record<string, string>).moduleKey || 'uncategorized';
      if (groups[moduleKey]) {
        groups[moduleKey].entities.push(entity);
      } else {
        groups.uncategorized.entities.push(entity);
      }
    });
    return Object.values(groups).filter((g) => g.entities.length > 0);
  }, [data]);

  // ----- Modal handlers -----
  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ type: 0, deliveryMode: 0 });
    setModalOpen(true);
  };

  const openEdit = (record: MetaEntity) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        const body: UpdateMetaEntityReq = {
          name: values.name,
          displayName: values.displayName,
          description: values.description,
          tableName: values.tableName,
          sortOrder: values.sortOrder,
          deliveryMode: values.deliveryMode,
          icon: values.icon,
        };
        const res = await metadataEntityApi.update(editing.id, body);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      } else {
        const body: CreateMetaEntityReq = values;
        const res = await metadataEntityApi.create(body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        } else {
          message.error(res.message);
        }
      }
    } catch {
      message.error('保存失败');
    }
  };

  const handlePublish = async (id: number) => {
    const res = await metadataEntityApi.publish(id);
    if (res.code === 200) {
      message.success('已发布');
      load();
    } else {
      message.error(res.message);
    }
  };

  const handleDelete = async (id: number) => {
    const res = await metadataEntityApi.delete(id);
    if (res.code === 200) {
      message.success('已删除');
      load();
    } else {
      message.error(res.message);
    }
  };

  // ----- 表格列定义 -----
  const columns: ColumnsType<MetaEntity> = [
    {
      title: '名称',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (name: string, record) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 500 }}
          onClick={() => navigate(`/entities/${record.id}`)}
        >
          {name}
        </Button>
      ),
    },
    { title: '编码', dataIndex: 'code', key: 'code', width: 140, ellipsis: true },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 140, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 100,
      render: (t: number) => (
        <Tag color={t === 1 ? 'geekblue' : 'default'}>{t === 1 ? '主数据类' : '普通业务'}</Tag>
      ),
    },
    {
      title: '交付模式',
      dataIndex: 'deliveryMode',
      key: 'deliveryMode',
      width: 100,
      render: (m: number, row) => (
        <Tag color={m === 1 ? 'purple' : 'default'}>{row.deliveryModeLabel ?? DELIVERY_MODE[m] ?? m}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (s: number) => (
        <Tag color={s === 1 ? 'green' : s === 2 ? 'default' : 'blue'}>
          {ENTITY_STATUS[s] ?? s}
        </Tag>
      ),
    },
    {
      title: '字段数',
      key: 'fieldCount',
      width: 70,
      render: (_, record) => (
        <Badge
          count={(record as Record<string, number>).fieldCount ?? 0}
          style={{ backgroundColor: '#1890ff' }}
          showZero
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(record)} disabled={record.status === 1}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}`)}>
            字段
          </Button>
          {record.deliveryMode === META_DELIVERY_RUNTIME && record.status === META_ENTITY_PUBLISHED && (
            <Button type="link" size="small" onClick={() => navigate(`/entities/${record.id}/data`)}>
              数据
            </Button>
          )}
          {record.status === 0 && (
            <Popconfirm title="发布后实体将不可编辑，确认发布？" onConfirm={() => handlePublish(record.id)}>
              <Button type="link" size="small">发布</Button>
            </Popconfirm>
          )}
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger disabled={record.status === 1}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // ----- Render -----
  return (
    <div className="page" style={{ display: 'flex', gap: 16 }}>
      {/* 面包屑（模块上下文时） */}
      {isScoped && (
        <div style={{ position: 'absolute', top: 0, left: 0, right: 0 }}>
          <Breadcrumb
            items={[
              { title: <a onClick={() => navigate('/apps')}>应用管理</a> },
              { title: <a onClick={() => navigate(`/apps/${appId}/modules`)}>模块</a> },
              { title: '领域建模' },
            ]}
          />
        </div>
      )}

      {/* 左侧模块树（仅非 scoped 模式） */}
      {!isScoped && (
        <Card size="small" style={{ width: 220, flexShrink: 0 }} bodyStyle={{ padding: 8 }}>
          <Tree
            showIcon
            defaultExpandAll
            selectedKeys={[selectedModule]}
            onSelect={(keys) => {
              if (keys.length > 0) setSelectedModule(String(keys[0]));
            }}
            treeData={MODULE_TREE}
          />
        </Card>
      )}

      {/* 右侧列表 */}
      <div style={{ flex: 1 }}>
        {isScoped && <div style={{ height: 36 }} /> /* 面包屑占位 */}
        {/* 过滤与操作栏 */}
        <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col>
            <Input.Search
              placeholder="搜索实体名称/编码"
              allowClear
              style={{ width: 220 }}
              onSearch={(v) => { setKeyword(v); setPage(1); }}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 130 }}
              placeholder="类型"
              allowClear
              value={typeFilter}
              onChange={(v) => { setTypeFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '普通业务' },
                { value: 1, label: '主数据类' },
              ]}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 120 }}
              placeholder="交付模式"
              allowClear
              value={deliveryFilter}
              onChange={(v) => { setDeliveryFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '生成式(A)' },
                { value: 1, label: '运行时(B)' },
              ]}
            />
          </Col>
          <Col>
            <Select
              style={{ width: 100 }}
              placeholder="状态"
              allowClear
              value={statusFilter}
              onChange={(v) => { setStatusFilter(v ?? null); setPage(1); }}
              options={[
                { value: 0, label: '草稿' },
                { value: 1, label: '已发布' },
                { value: 2, label: '已下线' },
              ]}
            />
          </Col>
          <Col flex="auto" style={{ textAlign: 'right' }}>
            <Space>
              <Button icon={<SearchOutlined />} onClick={load}>刷新</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建实体</Button>
            </Space>
          </Col>
        </Row>

        {/* 批量操作栏 */}
        {selectedRowKeys.length > 0 && (
          <Space style={{ marginBottom: 12 }}>
            <Text type="secondary">已选 {selectedRowKeys.length} 项</Text>
            <Popconfirm title={`确认发布选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={() => {
              // TODO: 后端 batchPublish API 未实现
              message.info('批量发布功能待后端实现');
              setSelectedRowKeys([]);
            }}>
              <Button size="small">批量发布</Button>
            </Popconfirm>
            <Popconfirm title={`确认删除选中的 ${selectedRowKeys.length} 个实体？`} onConfirm={() => {
              // TODO: 后端 batchDelete API 未实现
              message.info('批量删除功能待后端实现');
              setSelectedRowKeys([]);
            }}>
              <Button size="small" danger>批量删除</Button>
            </Popconfirm>
          </Space>
        )}

        {/* 模块分组展示 */}
        {selectedModule === 'all' && grouped.length > 0 ? (
          grouped.map((group) => (
            <Card
              key={group.key}
              size="small"
              title={
                <Space>
                  <FolderOutlined />
                  <span>{group.label}</span>
                  <Tag>{group.entities.length}</Tag>
                </Space>
              }
              style={{ marginBottom: 12 }}
              bodyStyle={{ padding: 0 }}
            >
              <Table
                rowKey="id"
                loading={loading}
                columns={columns}
                dataSource={group.entities}
                pagination={false}
                size="small"
                rowSelection={{
                  selectedRowKeys,
                  onChange: setSelectedRowKeys,
                }}
              />
            </Card>
          ))
        ) : (
          <Table
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={data}
            rowSelection={{
              selectedRowKeys,
              onChange: setSelectedRowKeys,
            }}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              onChange: (p, ps) => { setPage(p); setPageSize(ps); },
            }}
          />
        )}
      </div>

      {/* 新建/编辑 Modal */}
      <Modal
        title={editing ? '编辑实体' : '新建实体'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        width={600}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              {!editing ? (
                <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                  <Input disabled={!!editing} />
                </Form.Item>
              ) : (
                <Form.Item name="name" label="名称">
                  <Input disabled />
                </Form.Item>
              )}
            </Col>
          </Row>
          {!editing && (
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="code" label="编码" rules={[{ required: true }]} tooltip="全局唯一，一旦创建不可修改">
                  <Input placeholder="如：user, order" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="tableName" label="数据库表名" rules={[{ required: true }]} tooltip="物理表名，建议前缀如 meta_">
                  <Input placeholder="如：sys_user, biz_order" />
                </Form.Item>
              </Col>
            </Row>
          )}
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="实体的业务含义说明" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="deliveryMode" label="交付模式" tooltip="生成式走代码生成；运行时由 metadata-engine 提供动态 API">
                <Select
                  disabled={!!editing && editing.status === 1}
                  options={[
                    { value: 0, label: '生成式 (A)' },
                    { value: 1, label: '运行时 (B)' },
                  ]}
                />
              </Form.Item>
            </Col>
            {!editing && (
              <Col span={8}>
                <Form.Item name="type" label="类型">
                  <Select options={[
                    { value: 0, label: '普通业务实体' },
                    { value: 1, label: '主数据类实体' },
                  ]} />
                </Form.Item>
              </Col>
            )}
            <Col span={8}>
              <Form.Item name="sortOrder" label="排序">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          {/* 模块选择 */}
          <Form.Item name="moduleKey" label="所属模块">
            <Select
              disabled={isScoped}
              placeholder="选择模块（可选，由后端 API 驱动）"
              options={[
                { value: 'user', label: '用户管理' },
                { value: 'permission', label: '权限管理' },
                { value: 'order', label: '订单管理' },
                { value: 'product', label: '商品管理' },
                { value: 'customer', label: '客户管理' },
                { value: 'org', label: '组织架构' },
                { value: 'dict', label: '字典管理' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default EntityManagement;
