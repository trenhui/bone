import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { appApi, type BoneApplication, type CreateAppReq, type UpdateAppReq } from '../services/appApi';
import { unwrapPage } from '../utils/pageResult';
import ModulePage from '../components/ModulePage';

const { Text } = Typography;

const PERM_COLORS: Record<string, string> = { admin: 'red', developer: 'blue', viewer: 'green' };
const PERM_LABELS: Record<string, string> = { admin: '管理员', developer: '开发者', viewer: '只读' };

/**
 * 应用管理（IAM 限界上下文）。
 *
 * 应用(App)聚合的管理入口：服务端分页列表 + 关键词搜索 + 新建/编辑/删除。
 * 应用同时是元数据建模的归属维度，建模链路（应用 → 模块 → 实体 → 字段）
 * 见 bone-metadata-app「建模工作台」；应用与模块的后端真源在 bone-iam
 * `AppController`（写操作需 `iam:apps:write`）。
 */
const ApplicationManagement: React.FC = () => {
  const { message } = AntApp.useApp();
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editApp, setEditApp] = useState<BoneApplication | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchApps = useCallback(async () => {
    setLoading(true);
    try {
      const res = await appApi.listMine({ page, size: pageSize, keyword: debouncedKeyword || undefined });
      if (res.code === 200) {
        const { records, total: newTotal } = unwrapPage(res.data);
        // 删除后当前页可能越界：回退到最后一页
        if (records.length === 0 && newTotal > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(newTotal / pageSize)));
          return;
        }
        setApps(records);
        setTotal(newTotal);
      }
    } catch {
      message.error('获取应用列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, message]);

  useEffect(() => {
    fetchApps();
  }, [fetchApps]);

  // 搜索防抖：输入停止 300ms 后才触发请求
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const openCreate = () => {
    setEditApp(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (app: BoneApplication) => {
    setEditApp(app);
    form.setFieldsValue({ name: app.name, description: app.description, icon: app.icon });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editApp) {
        const res = await appApi.update(editApp.id, values as UpdateAppReq);
        if (res.code === 200) {
          message.success('应用更新成功');
        }
      } else {
        const res = await appApi.create(values as CreateAppReq);
        if (res.code === 200) {
          message.success('应用创建成功');
        }
      }
      setModalOpen(false);
      fetchApps();
    } catch {
      message.error(editApp ? '更新失败' : '创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      const res = await appApi.delete(id);
      if (res.code === 200) {
        message.success('已删除');
        fetchApps();
      }
    } catch {
      message.error('删除失败');
    }
  };

  const columns: ColumnsType<BoneApplication> = [
    {
      title: '应用',
      key: 'app',
      width: 240,
      render: (_, app) => (
        <Space>
          <span style={{ fontSize: 24, lineHeight: 1 }}>{app.icon || '📱'}</span>
          <Space direction="vertical" size={0} style={{ minWidth: 0 }}>
            <Text strong ellipsis style={{ maxWidth: 160 }}>
              {app.name}
            </Text>
            <Text code style={{ fontSize: 12 }}>
              {app.code}
            </Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '描述',
      key: 'description',
      ellipsis: true,
      render: (_, app) => app.description || <Text type="secondary">暂无描述</Text>,
    },
    {
      title: '我的角色',
      key: 'myRole',
      width: 100,
      render: (_, app) =>
        app.myRole ? <Tag color={PERM_COLORS[app.myRole]}>{PERM_LABELS[app.myRole] ?? app.myRole}</Tag> : '-',
    },
    {
      title: '模块 / 实体',
      key: 'counts',
      width: 130,
      render: (_, app) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {app.moduleCount} 模块 · {app.entityCount} 实体
        </Text>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (v: string) => v || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, app) => (
        <Space size={2}>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(app)} />
          </Tooltip>
          <Popconfirm title="确认删除该应用？" onConfirm={() => handleDelete(app.id)}>
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <ModulePage
      title="应用管理"
      description="应用的创建与维护；应用作为元数据建模与授权的归属维度，建模入口见「元数据管理 → 建模工作台」"
      extra={
        <Space>
          <Input
            allowClear
            placeholder="搜索应用名称或编码"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 240 }}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchApps} title="刷新" />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建应用
          </Button>
        </Space>
      }
      card={false}
    >
      <Table
        columns={columns}
        dataSource={apps}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个应用`,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          },
        }}
      />

      <Modal
        title={editApp ? '编辑应用' : '新建应用'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="应用名称"
            rules={[{ required: true, message: '请输入应用名称' }]}
          >
            <Input placeholder="如：订单管理系统" />
          </Form.Item>
          <Form.Item
            name="code"
            label="应用编码"
            rules={[{ required: true, message: '请输入应用编码' }]}
            tooltip="全局唯一标识，创建后不可修改"
            hidden={!!editApp}
          >
            <Input placeholder="如：OMS" disabled={!!editApp} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="应用的用途说明" />
          </Form.Item>
        </Form>
      </Modal>
    </ModulePage>
  );
};

export default ApplicationManagement;
