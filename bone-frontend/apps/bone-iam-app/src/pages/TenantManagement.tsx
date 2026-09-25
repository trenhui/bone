import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntApp,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  BankOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { StatisticCard } from '@ant-design/pro-components';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import ModulePage from '../components/ModulePage';
import type { Tenant, CreateTenantRequest, UpdateTenantRequest, UpdateTenantQuotaRequest } from '../types';

const TenantManagement: React.FC = () => {
  const { message } = AntApp.useApp();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isQuotaModalVisible, setIsQuotaModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentTenant, setCurrentTenant] = useState<Tenant | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [stats, setStats] = useState({ enabled: 0, disabled: 0 });
  const [form] = Form.useForm();
  const [quotaForm] = Form.useForm();

  const fetchTenants = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getTenants(page, pageSize, debouncedKeyword || undefined);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        // 删除后当前页可能越界：回退到最后一页
        if (records.length === 0 && newTotal > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(newTotal / pageSize)));
          return;
        }
        setTenants(records);
        setTotal(newTotal);
      }
    } catch {
      message.error('获取租户列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, message]);

  // 统计数据：全量拉取一次
  const fetchStats = useCallback(async () => {
    try {
      const response = await api.getTenants(1, 500);
      if (response.code === 200) {
        const { records } = unwrapPage(response.data);
        setStats({
          enabled: records.filter((t) => t.status === 1).length,
          disabled: records.filter((t) => t.status !== 1).length,
        });
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
    void fetchTenants();
    void fetchStats();
  }, [fetchTenants, fetchStats]);

  useEffect(() => {
    void fetchTenants();
  }, [fetchTenants]);

  useEffect(() => {
    void fetchStats();
  }, [fetchStats]);

  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentTenant(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEdit = (tenant: Tenant) => {
    setIsEditMode(true);
    setCurrentTenant(tenant);
    form.setFieldsValue({
      name: tenant.name,
      code: tenant.code,
      level: tenant.level,
      adminEmail: tenant.adminEmail,
    });
    setIsModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await api.deleteTenant(id);
      if (response.code === 200) {
        message.success('删除租户成功');
        reload();
      }
    } catch {
      message.error('删除租户失败');
    }
  };

  const handleToggleStatus = async (tenant: Tenant) => {
    try {
      const response = tenant.status === 1
        ? await api.disableTenant(tenant.id)
        : await api.enableTenant(tenant.id);
      if (response.code === 200) {
        message.success(tenant.status === 1 ? '已禁用租户' : '已启用租户');
        reload();
      }
    } catch {
      message.error('操作失败');
    }
  };

  const handleQuotaEdit = (tenant: Tenant) => {
    setCurrentTenant(tenant);
    quotaForm.setFieldsValue({
      maxAccounts: tenant.maxAccounts,
      maxRoles: tenant.maxRoles,
    });
    setIsQuotaModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (isEditMode && currentTenant) {
        const updateData: UpdateTenantRequest = {
          name: values.name,
          level: values.level,
          adminEmail: values.adminEmail,
        };
        const response = await api.updateTenant(currentTenant.id, updateData);
        if (response.code === 200) {
          message.success('更新租户成功');
          setIsModalVisible(false);
          reload();
        }
      } else {
        const createData: CreateTenantRequest = {
          name: values.name,
          code: values.code,
          level: values.level,
          adminEmail: values.adminEmail,
        };
        const response = await api.createTenant(createData);
        if (response.code === 200) {
          const d = response.data as unknown as {
            adminUsername?: string;
            initialPassword?: string;
          };
          Modal.success({
            title: '租户创建成功，已初始化租户管理员',
            width: 480,
            content: (
              <div style={{ marginTop: 8 }}>
                <p>
                  管理员用户名：<Typography.Text copyable code>{d?.adminUsername}</Typography.Text>
                </p>
                <p>
                  初始密码（<b>仅此一次展示</b>，请立即转交租户管理员）：
                  <Typography.Text copyable code>{d?.initialPassword}</Typography.Text>
                </p>
                <p style={{ color: '#999' }}>
                  该管理员可登录后管理本租户的账号、角色、组织机构与审计日志。
                </p>
              </div>
            ),
          });
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

  const handleQuotaSubmit = async () => {
    if (!currentTenant) return;
    try {
      const values = await quotaForm.validateFields();
      const quotaData: UpdateTenantQuotaRequest = {
        maxAccounts: values.maxAccounts != null ? Number(values.maxAccounts) : undefined,
        maxRoles: values.maxRoles != null ? Number(values.maxRoles) : undefined,
      };
      const response = await api.updateTenantQuota(currentTenant.id, quotaData);
      if (response.code === 200) {
        message.success('更新配额成功');
        setIsQuotaModalVisible(false);
        reload();
      }
    } catch {
      message.error('更新配额失败');
    }
  };

  const columns = [
    {
      title: '租户名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (name: string, record: Tenant) => (
        <Space>
          <BankOutlined style={{ color: '#1677ff' }} />
          <Typography.Text strong>{name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{record.code}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number) =>
        status === 1
          ? <Tag icon={<CheckCircleOutlined />} color="success">启用</Tag>
          : <Tag icon={<StopOutlined />} color="error">禁用</Tag>,
    },
    {
      title: '管理员邮箱',
      dataIndex: 'adminEmail',
      key: 'adminEmail',
      width: 200,
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    {
      title: '账号配额',
      key: 'maxAccounts',
      width: 110,
      render: (_: unknown, record: Tenant) =>
        record.maxAccounts != null ? <Tag color="blue">{record.maxAccounts} 个</Tag> : <Tag>不限</Tag>,
    },
    {
      title: '角色配额',
      key: 'maxRoles',
      width: 110,
      render: (_: unknown, record: Tenant) =>
        record.maxRoles != null ? <Tag color="blue">{record.maxRoles} 个</Tag> : <Tag>不限</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      render: (_: unknown, record: Tenant) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => handleQuotaEdit(record)}>
            配额
          </Button>
          <Switch
            checked={record.status === 1}
            checkedChildren="启用"
            unCheckedChildren="禁用"
            size="small"
            onChange={() => handleToggleStatus(record)}
          />
          <Popconfirm
            title="确定要删除这个租户吗？"
            description="删除后该租户关联的账号、角色等数据将被清理"
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
        placeholder="搜索租户名称或编码"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 260 }}
      />
      <Button icon={<ReloadOutlined />} onClick={reload} title="刷新" />
      <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
        新增租户
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '租户总数', value: total, prefix: <BankOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '启用中', value: stats.enabled, valueStyle: { color: '#52c41a' } }}
      />
      <StatisticCard
        statistic={{ title: '已禁用', value: stats.disabled, valueStyle: { color: '#ff4d4f' } }}
      />
    </StatisticCard.Group>
  );

  return (
    <ModulePage
      title="租户管理"
      description="管理多租户的生命周期、启停与资源配额。"
      extra={toolbar}
      statistics={statistics}
    >
      <Table
        columns={columns}
        dataSource={tenants}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个租户`,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          },
        }}
        scroll={{ x: 1200 }}
      />
      <Drawer
        title={isEditMode ? `编辑租户 · ${currentTenant?.name ?? ''}` : '新增租户'}
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
            label="租户名称"
            rules={[{ required: true, message: '请输入租户名称!' }]}
          >
            <Input placeholder="请输入租户名称" />
          </Form.Item>
          <Form.Item
            name="code"
            label="租户编码"
            rules={[
              { required: true, message: '请输入租户编码!' },
              { pattern: /^[a-zA-Z][a-zA-Z0-9_-]*$/, message: '编码必须以字母开头，仅允许字母、数字、下划线、连字符' },
            ]}
            extra={isEditMode ? '租户编码创建后不可修改' : '创建后不可修改，请谨慎填写'}
          >
            <Input placeholder="请输入租户编码" disabled={isEditMode} />
          </Form.Item>
          <Form.Item name="level" label="租户等级" tooltip="数字越大等级越高，可用于差异化配额与功能开关">
            <InputNumber min={0} max={9} style={{ width: '100%' }} placeholder="默认 0" />
          </Form.Item>
          <Form.Item
            name="adminEmail"
            label="管理员邮箱"
            rules={[{ type: 'email', message: '请输入有效的邮箱地址' }]}
          >
            <Input placeholder="请输入管理员邮箱" />
          </Form.Item>
        </Form>
      </Drawer>
      <Modal
        title={currentTenant ? `编辑配额 · ${currentTenant.name}` : '编辑配额'}
        open={isQuotaModalVisible}
        onOk={handleQuotaSubmit}
        onCancel={() => setIsQuotaModalVisible(false)}
        width={420}
        okText="保存"
      >
        <Form form={quotaForm} layout="vertical">
          <Form.Item name="maxAccounts" label="账号数量上限" extra="留空表示不限制">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空表示不限制" />
          </Form.Item>
          <Form.Item name="maxRoles" label="角色数量上限" extra="留空表示不限制">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空表示不限制" />
          </Form.Item>
        </Form>
      </Modal>
    </ModulePage>
  );
};

export default TenantManagement;
