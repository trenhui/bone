import React, { useCallback, useEffect, useState } from 'react';
import {
  Avatar,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Tree,
  TreeSelect,
  Typography,
  App as AntApp,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
  UnlockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { StatisticCard } from '@ant-design/pro-components';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import ModulePage from '../components/ModulePage';
import type {
  Account,
  CreateAccountRequest,
  Role,
  UpdateAccountRequest,
} from '../types';
import type { DeptNode } from '../services/api';

/** 部门树节点 → antd DataNode；value 用数字（DeptNode.id 虽序列化为字符串，但后端 deptId 为 Long，TreeSelect 表单字段亦为 number） */
const toTreeData = (nodes: DeptNode[]): DataNode[] =>
  nodes.map((n) => ({
    key: Number(n.id),
    value: Number(n.id),
    title: n.name,
    children: n.children && n.children.length > 0 ? toTreeData(n.children) : undefined,
  }));

const statusMap: Record<number, { text: string; color: string; icon: React.ReactNode }> = {
  0: { text: '禁用', color: 'error', icon: <LockOutlined /> },
  1: { text: '启用', color: 'success', icon: <UnlockOutlined /> },
  2: { text: '锁定', color: 'warning', icon: <LockOutlined /> },
};

const AVATAR_COLORS = ['#1677ff', '#722ed1', '#13c2c2', '#52c41a', '#fa8c16', '#eb2f96'];
const avatarColor = (seed: string) =>
  AVATAR_COLORS[[...seed].reduce((acc, ch) => acc + ch.charCodeAt(0), 0) % AVATAR_COLORS.length];

const AccountManagement: React.FC = () => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [deptTreeData, setDeptTreeData] = useState<DataNode[]>([]);
  const [selectedDeptId, setSelectedDeptId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentAccount, setCurrentAccount] = useState<Account | null>(null);
  const [isResetPwdModalVisible, setIsResetPwdModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [stats, setStats] = useState({ enabled: 0, disabledOrLocked: 0, admins: 0 });
  const [form] = Form.useForm();
  const [resetPwdForm] = Form.useForm();
  const { message: antMessage } = AntApp.useApp();

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getAccounts(page, pageSize, debouncedKeyword, undefined, selectedDeptId);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        // 删除后当前页可能越界：回退到最后一页
        if (records.length === 0 && newTotal > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(newTotal / pageSize)));
          return;
        }
        setAccounts(records);
        setTotal(newTotal);
      }
    } catch (err) {
      console.error('获取账号列表失败', err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, selectedDeptId]);

  // 统计数据：拉取一次全量（管理台账号规模有限），派生启用/禁用/管理员数
  const fetchStats = useCallback(async () => {
    try {
      const response = await api.getAccounts(1, 500);
      if (response.code === 200) {
        const { records } = unwrapPage(response.data);
        setStats({
          enabled: records.filter((a) => a.status === 1).length,
          disabledOrLocked: records.filter((a) => a.status === 0 || a.status === 2).length,
          admins: records.filter((a) => a.isAdmin).length,
        });
      }
    } catch {
      /* 统计失败不影响列表 */
    }
  }, []);

  // 搜索防抖：输入停止 300ms 后才触发请求
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const fetchRoles = useCallback(async () => {
    try {
      const response = await api.getRoles(1, 100);
      if (response.code === 200) {
        setRoles(unwrapPage(response.data).records);
      }
    } catch (err) {
      console.error('获取角色列表失败', err);
    }
  }, []);

  const fetchDeptTree = useCallback(async () => {
    try {
      const response = await api.getDeptTree();
      if (response.code === 200) {
        setDeptTreeData(toTreeData(response.data ?? []));
      }
    } catch (err) {
      console.error('获取部门树失败', err);
    }
  }, []);

  const reload = useCallback(() => {
    void fetchAccounts();
    void fetchStats();
  }, [fetchAccounts, fetchStats]);

  useEffect(() => {
    void fetchAccounts();
    void fetchRoles();
    void fetchDeptTree();
  }, [fetchAccounts, fetchRoles, fetchDeptTree]);

  useEffect(() => {
    void fetchStats();
  }, [fetchStats]);

  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentAccount(null);
    form.resetFields();
    form.setFieldsValue({ status: 1 });
    setIsModalVisible(true);
  };

  const handleEdit = async (account: Account) => {
    setIsEditMode(true);
    setCurrentAccount(account);
    setIsModalVisible(true);
    try {
      const detail = await api.getAccount(account.id);
      const roleIds =
        detail.code === 200 && detail.data?.roleIds?.length
          ? detail.data.roleIds
          : account.roleIds ?? account.roles?.map((role) => role.id) ?? [];
      form.setFieldsValue({
        email: account.email,
        phone: account.phone,
        realName: account.realName,
        status: account.status,
        deptId: account.deptId ?? undefined,
        roleIds,
      });
    } catch {
      form.setFieldsValue({
        email: account.email,
        phone: account.phone,
        realName: account.realName,
        status: account.status,
        deptId: account.deptId ?? undefined,
        roleIds: account.roleIds ?? account.roles?.map((role) => role.id) ?? [],
      });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await api.deleteAccount(id);
      if (response.code === 200) {
        antMessage.success('删除账号成功');
        reload();
      }
    } catch {
      antMessage.error('删除账号失败');
    }
  };

  const handleToggleStatus = async (account: Account) => {
    try {
      if (account.status === 1) {
        await api.disableAccount(account.id);
        antMessage.success('账号已禁用');
      } else {
        await api.enableAccount(account.id);
        antMessage.success('账号已启用');
      }
      reload();
    } catch {
      antMessage.error('操作失败');
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
        antMessage.success('密码重置成功');
        setIsResetPwdModalVisible(false);
      }
    } catch {
      antMessage.error('密码重置失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      if (isEditMode && currentAccount) {
        const updateData: UpdateAccountRequest = {
          email: values.email,
          phone: values.phone,
          realName: values.realName,
          status: values.status,
          deptId: values.deptId ?? null,
          roleIds: values.roleIds,
        };
        const response = await api.updateAccount(currentAccount.id, updateData);
        if (response.code === 200) {
          antMessage.success('更新账号成功');
          setIsModalVisible(false);
          reload();
        } else {
          antMessage.error(response.message || '更新账号失败');
        }
      } else {
        const createData: CreateAccountRequest = {
          username: values.username,
          password: values.password,
          email: values.email,
          phone: values.phone,
          realName: values.realName,
          tenantId: values.tenantId,
          deptId: values.deptId ?? null,
          roleIds: values.roleIds,
        };
        const response = await api.createAccount(createData);
        if (response.code === 200) {
          antMessage.success('创建账号成功');
          setIsModalVisible(false);
          reload();
        } else {
          antMessage.error(response.message || '创建账号失败');
        }
      }
    } catch (err: unknown) {
      const msg = (err as { displayMessage?: string })?.displayMessage
        || (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || '操作失败';
      antMessage.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: '用户',
      key: 'user',
      width: 190,
      render: (_: unknown, record: Account) => (
        <Space>
          <Avatar
            style={{ backgroundColor: avatarColor(record.username), flexShrink: 0 }}
            icon={<UserOutlined />}
          >
            {(record.realName || record.username).charAt(0)}
          </Avatar>
          <Space direction="vertical" size={0} style={{ minWidth: 0 }}>
            <Space size={4}>
              <Typography.Text strong ellipsis style={{ maxWidth: 120 }}>
                {record.username}
              </Typography.Text>
              {record.isAdmin && (
                <Tooltip title="平台管理员">
                  <Tag color="geekblue" style={{ marginInlineEnd: 0 }} icon={<TeamOutlined />} />
                </Tooltip>
              )}
            </Space>
            <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
              {record.realName || '-'}
            </Typography.Text>
          </Space>
        </Space>
      ),
    },
    {
      title: '所属部门',
      dataIndex: 'deptName',
      key: 'deptName',
      width: 112,
      render: (v: string | null) => (v ? <Tag color="blue" icon={<ApartmentOutlined />}>{v}</Tag> : <Typography.Text type="secondary">未归属</Typography.Text>),
    },
    {
      title: '联系方式',
      key: 'contact',
      ellipsis: true,
      render: (_: unknown, record: Account) => (
        <Space direction="vertical" size={0} style={{ minWidth: 0 }}>
          {record.email ? (
            <Typography.Text copyable={{ text: record.email }} style={{ fontSize: 13 }} ellipsis>
              {record.email}
            </Typography.Text>
          ) : (
            <Typography.Text type="secondary">-</Typography.Text>
          )}
          {record.phone && (
            <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
              {record.phone}
            </Typography.Text>
          )}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 84,
      render: (status: number) => {
        const info = statusMap[status] || { text: '未知', color: 'default', icon: null };
        return <Tag color={info.color} icon={info.icon}>{info.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 168,
      render: (_: unknown, record: Account) => (
        <Space size={2}>
          <Tooltip title="编辑">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          </Tooltip>
          <Tooltip title={record.status === 1 ? '禁用' : '启用'}>
            <Button
              type="text"
              size="small"
              icon={record.status === 1 ? <LockOutlined /> : <UnlockOutlined />}
              onClick={() => handleToggleStatus(record)}
            />
          </Tooltip>
          <Tooltip title="重置密码">
            <Button type="text" size="small" icon={<KeyOutlined />} onClick={() => handleResetPassword(record)} />
          </Tooltip>
          <Popconfirm
            title="确定要删除这个账号吗？"
            description="删除后该账号将无法登录平台"
            onConfirm={() => handleDelete(record.id)}
            okText="删除"
            okButtonProps={{ danger: true }}
            cancelText="取消"
          >
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const toolbar = (
    <Space>
      <Input
        allowClear
        placeholder="搜索用户名、邮箱或姓名"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 280 }}
      />
      <Button icon={<ReloadOutlined />} onClick={reload} title="刷新" />
      <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
        新增账号
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '账号总数', value: total, prefix: <TeamOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '启用中', value: stats.enabled, valueStyle: { color: '#52c41a' } }}
      />
      <StatisticCard
        statistic={{ title: '禁用 / 锁定', value: stats.disabledOrLocked, valueStyle: { color: '#faad14' } }}
      />
      <StatisticCard statistic={{ title: '管理员', value: stats.admins }} />
    </StatisticCard.Group>
  );

  const handleDeptSelect = (key: React.Key[] | React.Key | null) => {
    const id = Array.isArray(key) ? (key[0] ?? null) : key;
    setSelectedDeptId(id == null ? null : Number(id));
    setPage(1);
  };

  const deptFilterPanel = (
    <div
      style={{
        width: 248,
        flexShrink: 0,
        background: '#fff',
        borderRadius: 8,
        padding: '12px 8px',
        maxHeight: 560,
        overflow: 'auto',
      }}
    >
      <Typography.Text type="secondary" style={{ fontSize: 12, paddingLeft: 8 }}>
        按部门筛选（含子部门）
      </Typography.Text>
      <Tree
        treeData={deptTreeData}
        blockNode
        defaultExpandAll
        selectedKeys={selectedDeptId == null ? [] : [selectedDeptId]}
        onSelect={(keys) => handleDeptSelect(keys as React.Key[])}
        style={{ marginTop: 8 }}
      />
    </div>
  );

  return (
    <ModulePage
      title="账号管理"
      description="管理平台用户账号，支持按部门筛选、搜索、启用/禁用、重置密码与角色分配。"
      extra={toolbar}
      statistics={statistics}
    >
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {deptFilterPanel}
        <div style={{ flex: 1, minWidth: 0 }}>
          <Table
            columns={columns}
            dataSource={accounts}
            rowKey="id"
            loading={loading}
            scroll={{ x: 640 }}
            pagination={{
              current: page,
              pageSize: pageSize,
              total: total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 个账号`,
              onChange: (p, ps) => {
                setPage(p);
                if (ps) setPageSize(ps);
              },
            }}
          />
        </div>
      </div>
      <Drawer
        title={isEditMode ? `编辑账号 · ${currentAccount?.username ?? ''}` : '新增账号'}
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
          <Form.Item name="username" label="用户名" rules={[{ required: !isEditMode, message: '请输入用户名!' }]} hidden={isEditMode}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          {!isEditMode && (
            <Form.Item name="password" label="初始密码" rules={[{ required: true, message: '请输入密码!' }, { min: 8, message: '密码长度至少8位!' }]} extra="至少 8 位，创建后可由用户自行修改">
              <Input.Password placeholder="请输入密码（至少8位）" autoComplete="new-password" />
            </Form.Item>
          )}
          <Form.Item name="realName" label="真实姓名">
            <Input placeholder="请输入真实姓名" />
          </Form.Item>
          <Form.Item name="email" label="邮箱" rules={[{ required: true, message: '请输入邮箱!' }, { type: 'email', message: '请输入正确的邮箱格式!' }]}>
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="手机号" rules={[{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号!' }]}>
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item
            name="deptId"
            label="归属部门"
            tooltip="账号所属的主部门（必填）；组织机构树与账号列表联动展示"
            rules={[{ required: true, message: '请选择归属部门!' }]}
          >
            <TreeSelect
              treeData={deptTreeData}
              placeholder="请选择归属部门"
              treeDefaultExpandAll
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true, message: '请选择状态!' }]}>
            <Select
              placeholder="请选择状态"
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '禁用' },
                { value: 2, label: '锁定' },
              ]}
            />
          </Form.Item>
          <Form.Item name="roleIds" label="角色" tooltip="一个账号可绑定多个角色，权限取角色并集">
            <Select mode="multiple" placeholder="请选择角色" style={{ width: '100%' }} maxTagCount="responsive">
              {roles.map((role) => (
                <Select.Option key={role.id} value={role.id}>
                  {role.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Drawer>
      <Modal
        title={currentAccount ? `重置密码 · ${currentAccount.username}` : '重置密码'}
        open={isResetPwdModalVisible}
        onOk={handleResetPasswordSubmit}
        onCancel={() => setIsResetPwdModalVisible(false)}
        okText="重置"
      >
        <Form form={resetPwdForm} layout="vertical">
          <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码!' }, { min: 8, message: '密码长度至少8位!' }]} extra="重置后请通知用户及时修改">
            <Input.Password placeholder="请输入新密码（至少8位）" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </ModulePage>
  );
};

export default AccountManagement;
