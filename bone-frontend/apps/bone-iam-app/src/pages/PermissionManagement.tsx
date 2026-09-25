import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Drawer,
  Form,
  Input,
  AutoComplete,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TagsOutlined,
} from '@ant-design/icons';
import { StatisticCard } from '@ant-design/pro-components';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import ModulePage from '../components/ModulePage';
import type { Permission, CreatePermissionRequest, UpdatePermissionRequest } from '../types';
import { BONE_PERMISSION_CODE_CATALOG } from '../constants/bonePermissionCodes';

const PermissionManagement: React.FC = () => {
  const { message } = AntApp.useApp();
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentPermission, setCurrentPermission] = useState<Permission | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [domainCount, setDomainCount] = useState(0);
  const [form] = Form.useForm();

  const fetchPermissions = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getPermissions(page, pageSize, debouncedKeyword || undefined);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        // 删除后当前页可能越界：回退到最后一页
        if (records.length === 0 && newTotal > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(newTotal / pageSize)));
          return;
        }
        setPermissions(records);
        setTotal(newTotal);
      }
    } catch {
      message.error('获取权限列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, message]);

  // 统计数据：全量拉取一次，派生覆盖域数（code 首段）
  const fetchStats = useCallback(async () => {
    try {
      const response = await api.getPermissions(1, 500);
      if (response.code === 200) {
        const { records } = unwrapPage(response.data);
        setDomainCount(new Set(records.map((p) => p.code?.split(':')[0] ?? '')).size);
      }
    } catch {
      /* 统计失败不影响列表 */
    }
  }, []);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const reload = useCallback(() => {
    void fetchPermissions();
    void fetchStats();
  }, [fetchPermissions, fetchStats]);

  useEffect(() => {
    void fetchPermissions();
  }, [fetchPermissions]);

  useEffect(() => {
    void fetchStats();
  }, [fetchStats]);

  const handleCatalogCodeSelect = (code: string) => {
    const entry = BONE_PERMISSION_CODE_CATALOG.find((item) => item.code === code);
    if (!entry) {
      return;
    }
    const segments = code.split(':');
    const action = segments.length > 1 ? segments[segments.length - 1] : '';
    const resourceType = segments.length > 2 ? segments.slice(1, -1).join(':') : segments[0] ?? entry.domain;
    form.setFieldsValue({
      code: entry.code,
      name: entry.name,
      resourceType,
      action,
      description: `[${entry.maturity}] ${entry.domain} 域平台权限`,
    });
  };

  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentPermission(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEdit = (permission: Permission) => {
    setIsEditMode(true);
    setCurrentPermission(permission);
    form.setFieldsValue({
      name: permission.name,
      code: permission.code,
      description: permission.description,
      resourceType: permission.resourceType,
      action: permission.action
    });
    setIsModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await api.deletePermission(id);
      if (response.code === 200) {
        message.success('删除权限成功');
        reload();
      }
    } catch {
      message.error('删除权限失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (isEditMode && currentPermission) {
        const updateData: UpdatePermissionRequest = {
          name: values.name,
          description: values.description,
          resourceType: values.resourceType,
          action: values.action
        };
        const response = await api.updatePermission(currentPermission.id, updateData);
        if (response.code === 200) {
          message.success('更新权限成功');
          setIsModalVisible(false);
          reload();
        }
      } else {
        const createData: CreatePermissionRequest = {
          name: values.name,
          code: values.code,
          description: values.description,
          resourceType: values.resourceType,
          action: values.action
        };
        const response = await api.createPermission(createData);
        if (response.code === 200) {
          message.success('创建权限成功');
          setIsModalVisible(false);
          reload();
        }
      }
    } catch {
      message.error('操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: '权限名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (name: string) => (
        <Space>
          <SafetyCertificateOutlined style={{ color: '#1677ff' }} />
          <Typography.Text strong>{name}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '权限编码',
      dataIndex: 'code',
      key: 'code',
      width: 240,
      render: (code: string) => <Typography.Text code copyable={{ text: code }}>{code}</Typography.Text>,
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
      width: 110,
      render: (v: string) => (v ? <Tag color="cyan">{v}</Tag> : '-'),
    },
    {
      title: '操作类型',
      dataIndex: 'action',
      key: 'actionType',
      width: 100,
      render: (v: string) => (v ? <Tag color="geekblue">{v}</Tag> : '-'),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_: unknown, record: Permission) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个权限吗？"
            description="删除后引用该权限点的角色将同步失效"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const toolbar = (
    <Space>
      <Input
        allowClear
        placeholder="搜索权限名称或编码"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 260 }}
      />
      <Button icon={<ReloadOutlined />} onClick={reload} title="刷新" />
      <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
        新增权限
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '权限点总数', value: total, prefix: <SafetyCertificateOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '覆盖业务域', value: domainCount, prefix: <TagsOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '平台目录编码', value: BONE_PERMISSION_CODE_CATALOG.length, prefix: <ApiOutlined /> }}
      />
    </StatisticCard.Group>
  );

  return (
    <ModulePage
      title="权限管理"
      description="维护平台权限点，支持按名称或编码检索；编码遵循 domain:resource:action 规范。"
      extra={toolbar}
      statistics={statistics}
    >
      <Table
        columns={columns}
        dataSource={permissions}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个权限点`,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          }
        }}
      />
      <Drawer
        title={isEditMode ? `编辑权限 · ${currentPermission?.name ?? ''}` : '新增权限'}
        open={isModalVisible}
        onClose={() => setIsModalVisible(false)}
        width={480}
        destroyOnClose
        footer={
          <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button onClick={() => setIsModalVisible(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleSubmit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="权限名称"
            rules={[{ required: true, message: '请输入权限名称!' }]}
          >
            <Input placeholder="请输入权限名称" />
          </Form.Item>
          <Form.Item
            name="code"
            label="权限编码"
            rules={[{ required: true, message: '请选择或输入权限编码!' }]}
            extra="与 IAM 详设 §3.7 / JWT authorities 一致；可从平台目录选择或自定义"
          >
            {isEditMode ? (
              <Input disabled />
            ) : (
              <AutoComplete
                placeholder="选择推荐编码或输入自定义（domain:resource:action）"
                options={BONE_PERMISSION_CODE_CATALOG.map((item) => ({
                  value: item.code,
                  label: `${item.code} — ${item.name} [${item.maturity}]`,
                }))}
                onSelect={handleCatalogCodeSelect}
                filterOption={(input, option) =>
                  String(option?.value ?? '').toLowerCase().includes(input.toLowerCase())
                  || String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                }
              />
            )}
          </Form.Item>
          <Form.Item
            name="resourceType"
            label="资源类型"
            rules={[{ required: true, message: '请输入资源类型!' }]}
          >
            <Input placeholder="如：menu, button, api, data" />
          </Form.Item>
          <Form.Item
            name="action"
            label="操作类型"
            rules={[{ required: true, message: '请输入操作!' }]}
          >
            <Input placeholder="如：create, read, update, delete" />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入权限描述!' }]}
          >
            <Input.TextArea placeholder="请输入权限描述" rows={3} />
          </Form.Item>
        </Form>
      </Drawer>
    </ModulePage>
  );
};

export default PermissionManagement;
