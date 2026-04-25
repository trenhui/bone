import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Popconfirm, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined, LockOutlined, UnlockOutlined, KeyOutlined } from '@ant-design/icons';
import * as api from '../services/api';
import type { Account, CreateAccountRequest, UpdateAccountRequest } from '../types';

const { Option } = Select;

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '禁用', color: 'red' },
  1: { text: '启用', color: 'green' },
  2: { text: '锁定', color: 'orange' },
};

const AccountManagement: React.FC = () => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [roles, setRoles] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentAccount, setCurrentAccount] = useState<Account | null>(null);
  const [isResetPwdModalVisible, setIsResetPwdModalVisible] = useState(false);
  const [form] = Form.useForm();
  const [resetPwdForm] = Form.useForm();

  const fetchAccounts = async () => {
    setLoading(true);
    try {
      const response = await api.getAccounts(page, pageSize, keyword);
      if (response.code === 200) {
        setAccounts(response.data.data);
        setTotal(response.data.total);
      }
    } catch {
      message.error('获取账号列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      const response = await api.getRoles(1, 100);
      if (response.code === 200) {
        setRoles(response.data.data);
      }
    } catch {
      message.error('获取角色列表失败');
    }
  };

  useEffect(() => {
    fetchAccounts();
    fetchRoles();
  }, [page, pageSize, keyword]);

  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentAccount(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEdit = (account: Account) => {
    setIsEditMode(true);
    setCurrentAccount(account);
    form.setFieldsValue({
      email: account.email,
      phone: account.phone,
      realName: account.realName,
      status: account.status,
      roleIds: account.roles?.map((role) => role.id) || [],
    });
    setIsModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await api.deleteAccount(id);
      if (response.code === 200) {
        message.success('删除账号成功');
        fetchAccounts();
      }
    } catch {
      message.error('删除账号失败');
    }
  };

  const handleToggleStatus = async (account: Account) => {
    try {
      if (account.status === 1) {
        await api.disableAccount(account.id);
        message.success('账号已禁用');
      } else {
        await api.enableAccount(account.id);
        message.success('账号已启用');
      }
      fetchAccounts();
    } catch {
      message.error('操作失败');
    }
  };

  const handleResetPassword = (account: Account) => {
    setCurrentAccount(account);
    resetPwdForm.resetFields();
    setIsResetPwdModalVisible(true);
  };

  const handleResetPasswordSubmit = async () => {
    try {
      const values = await resetPwdForm.validateFields();
      if (currentAccount) {
        await api.resetAccountPassword(currentAccount.id, { password: values.password });
        message.success('密码重置成功');
        setIsResetPwdModalVisible(false);
      }
    } catch {
      message.error('密码重置失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (isEditMode && currentAccount) {
        const updateData: UpdateAccountRequest = {
          email: values.email,
          phone: values.phone,
          realName: values.realName,
          status: values.status,
          roleIds: values.roleIds,
        };
        const response = await api.updateAccount(currentAccount.id, updateData);
        if (response.code === 200) {
          message.success('更新账号成功');
          setIsModalVisible(false);
          fetchAccounts();
        }
      } else {
        const createData: CreateAccountRequest = {
          username: values.username,
          password: values.password,
          email: values.email,
          phone: values.phone,
          realName: values.realName,
          tenantId: values.tenantId,
          roleIds: values.roleIds,
        };
        const response = await api.createAccount(createData);
        if (response.code === 200) {
          message.success('创建账号成功');
          setIsModalVisible(false);
          fetchAccounts();
        }
      }
    } catch {
      message.error('操作失败');
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    { title: '手机号', dataIndex: 'phone', key: 'phone' },
    { title: '真实姓名', dataIndex: 'realName', key: 'realName' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: number) => {
        const info = statusMap[status] || { text: '未知', color: 'default' };
        return <Tag color={info.color}>{info.text}</Tag>;
      },
    },
    {
      title: '管理员',
      dataIndex: 'isAdmin',
      key: 'isAdmin',
      render: (isAdmin: boolean) => (isAdmin ? <Tag color="blue">是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: (lastLoginAt: string) => lastLoginAt || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Account) => (
        <Space size="middle">
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          <Button
            icon={record.status === 1 ? <LockOutlined /> : <UnlockOutlined />}
            onClick={() => handleToggleStatus(record)}
          />
          <Button icon={<KeyOutlined />} onClick={() => handleResetPassword(record)} />
          <Popconfirm title="确定要删除这个账号吗？" onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>账号管理</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            placeholder="搜索用户名、邮箱或姓名"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新增账号
          </Button>
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={accounts}
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
      />
      <Modal title={isEditMode ? '编辑账号' : '新增账号'} open={isModalVisible} onOk={handleSubmit} onCancel={() => setIsModalVisible(false)} width={600}>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: !isEditMode, message: '请输入用户名!' }]} hidden={isEditMode}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          {!isEditMode && (
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码!' }]}>
              <Input.Password placeholder="请输入密码" />
            </Form.Item>
          )}
          <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱!' }, { type: 'email', message: '请输入正确的邮箱格式!' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号!' }]}>
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item name="realName" label="真实姓名">
            <Input placeholder="请输入真实姓名" />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态!' }]}>
            <Select placeholder="请选择状态">
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
              <Option value={2}>锁定</Option>
            </Select>
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" placeholder="请选择角色" style={{ width: '100%' }}>
              {roles.map((role) => (
                <Option key={role.id} value={role.id}>
                  {role.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
      <Modal title="重置密码" open={isResetPwdModalVisible} onOk={handleResetPasswordSubmit} onCancel={() => setIsResetPwdModalVisible(false)}>
        <Form form={resetPwdForm} layout="vertical">
          <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码!' }, { min: 6, message: '密码至少6位!' }]}>
            <Input.Password placeholder="请输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AccountManagement;
