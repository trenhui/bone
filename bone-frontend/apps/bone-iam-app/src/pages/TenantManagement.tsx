import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, message, Popconfirm, Space, Tag, Switch } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import type { Tenant, CreateTenantRequest, UpdateTenantRequest, UpdateTenantQuotaRequest } from '../types';

const TenantManagement: React.FC = () => {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isQuotaModalVisible, setIsQuotaModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentTenant, setCurrentTenant] = useState<Tenant | null>(null);
  const [form] = Form.useForm();
  const [quotaForm] = Form.useForm();

  const fetchTenants = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getTenants(page, pageSize, keyword);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        setTenants(records);
        setTotal(newTotal);
      }
    } catch {
      message.error('获取租户列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, keyword]);

  useEffect(() => {
    void fetchTenants();
  }, [fetchTenants]);

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
        fetchTenants();
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
        fetchTenants();
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
          fetchTenants();
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
          message.success('创建租户成功');
          setIsModalVisible(false);
          fetchTenants();
        }
      }
    } catch {
      message.error('操作失败');
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
        fetchTenants();
      }
    } catch {
      message.error('更新配额失败');
    }
  };

  const columns = [
    { title: '租户名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: '编码', dataIndex: 'code', key: 'code', width: 140 },
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
    { title: '管理员邮箱', dataIndex: 'adminEmail', key: 'adminEmail', width: 200 },
    {
      title: '账号配额',
      key: 'maxAccounts',
      width: 120,
      render: (_: unknown, record: Tenant) =>
        record.maxAccounts != null ? `${record.maxAccounts}` : '不限',
    },
    {
      title: '角色配额',
      key: 'maxRoles',
      width: 120,
      render: (_: unknown, record: Tenant) =>
        record.maxRoles != null ? `${record.maxRoles}` : '不限',
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_: unknown, record: Tenant) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button size="small" onClick={() => handleQuotaEdit(record)}>
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
            okText="确定"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>租户管理</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            placeholder="搜索租户名称或编码"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(1);
            }}
            style={{ width: 300 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增租户
          </Button>
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={tenants}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          },
        }}
        scroll={{ x: 1200 }}
      />
      <Modal
        title={isEditMode ? '编辑租户' : '新增租户'}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
        width={520}
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
          >
            <Input placeholder="请输入租户编码" disabled={isEditMode} />
          </Form.Item>
          <Form.Item name="level" label="租户等级">
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
      </Modal>
      <Modal
        title="编辑配额"
        open={isQuotaModalVisible}
        onOk={handleQuotaSubmit}
        onCancel={() => setIsQuotaModalVisible(false)}
        width={420}
      >
        <Form form={quotaForm} layout="vertical">
          <Form.Item name="maxAccounts" label="账号数量上限（留空不限）">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空表示不限制" />
          </Form.Item>
          <Form.Item name="maxRoles" label="角色数量上限（留空不限）">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="留空表示不限制" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TenantManagement;
