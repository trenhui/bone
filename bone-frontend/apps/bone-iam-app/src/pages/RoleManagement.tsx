import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntApp,
  Avatar,
  Button,
  Drawer,
  Form,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { StatisticCard } from '@ant-design/pro-components';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import ModulePage from '../components/ModulePage';
import type { Permission, Role, CreateRoleRequest, UpdateRoleRequest } from '../types';

const RoleManagement: React.FC = () => {
  const { message } = AntApp.useApp();
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentRole, setCurrentRole] = useState<Role | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [domainCount, setDomainCount] = useState(0);
  const [form] = Form.useForm();

  const fetchRoles = useCallback(async () => {
    setLoading(true);
    try {
      const response = await api.getRoles(page, pageSize, debouncedKeyword || undefined);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        // 删除后当前页可能越界：回退到最后一页
        if (records.length === 0 && newTotal > 0 && page > 1) {
          setPage(Math.max(1, Math.ceil(newTotal / pageSize)));
          return;
        }
        setRoles(records);
        setTotal(newTotal);
      }
    } catch {
      message.error('获取角色列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedKeyword, message]);

  // 统计数据：权限点覆盖业务域（code 首段）；注意列表接口不返回角色已绑权限，
  // 权限分配情况仅在编辑抽屉内通过 detail + assign 接口闭环（见 handleEdit / handleSubmit）
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

  const fetchPermissions = useCallback(async () => {
    try {
      const response = await api.getPermissions(1, 200);
      if (response.code === 200) {
        setPermissions(unwrapPage(response.data).records);
      }
    } catch {
      message.error('获取权限列表失败');
    }
  }, [message]);

  // 搜索防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword);
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  const reload = useCallback(() => {
    void fetchRoles();
    void fetchStats();
  }, [fetchRoles, fetchStats]);

  useEffect(() => {
    void fetchRoles();
  }, [fetchRoles]);

  useEffect(() => {
    void fetchPermissions();
    void fetchStats();
  }, [fetchPermissions, fetchStats]);

  const handleAdd = () => {
    setIsEditMode(false);
    setCurrentRole(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEdit = async (role: Role) => {
    setIsEditMode(true);
    setCurrentRole(role);
    setIsModalVisible(true);
    // 列表接口不返回已绑定权限，从详情接口预填（RoleDetailResp.permissionIds）
    try {
      const detail = await api.getRole(role.id);
      const detailIds =
        (detail.data as unknown as { permissionIds?: Array<number | string> } | undefined)
          ?.permissionIds ?? [];
      form.setFieldsValue({
        name: role.name,
        description: role.description,
        permissionIds: detailIds as number[],
      });
    } catch {
      form.setFieldsValue({ name: role.name, description: role.description, permissionIds: [] });
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const response = await api.deleteRole(id);
      if (response.code === 200) {
        message.success('删除角色成功');
        reload();
      }
    } catch {
      message.error('删除角色失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      let roleId: number;
      if (isEditMode && currentRole) {
        const updateData: UpdateRoleRequest = {
          name: values.name,
          description: values.description,
        };
        const response = await api.updateRole(currentRole.id, updateData);
        if (response.code !== 200) {
          message.error(response.message || '更新角色失败');
          return;
        }
        roleId = currentRole.id;
      } else {
        const createData: CreateRoleRequest = {
          name: values.name,
          description: values.description,
        };
        const response = await api.createRole(createData);
        if (response.code !== 200) {
          message.error(response.message || '创建角色失败');
          return;
        }
        // 后端返回新建角色 ID（雪花 ID 以字符串承载，直接透传）
        roleId = response.data;
      }
      // 权限绑定走独立的 replace 语义接口；编辑时即使清空也要提交以解除全部绑定
      const permissionIds = (values.permissionIds ?? []) as number[];
      if (permissionIds.length > 0 || isEditMode) {
        try {
          await api.assignPermissions(roleId, permissionIds);
        } catch {
          message.warning('角色已保存，但权限分配失败，请重试');
        }
      }
      message.success(isEditMode ? '更新角色成功' : '创建角色成功');
      setIsModalVisible(false);
      reload();
    } catch {
      message.error('操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: '角色名称',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (name: string) => (
        <Space>
          <Avatar style={{ backgroundColor: '#f0f5ff', color: '#2f54eb', flexShrink: 0 }}>
            {name.charAt(0)}
          </Avatar>
          <Typography.Text strong>{name}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (v: string) => v || '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_: unknown, record: Role) => (
        <Space size={0}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定要删除这个角色吗？"
            description="删除后关联该角色的账号将失去对应权限"
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
        placeholder="搜索角色名称"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 240 }}
      />
      <Button icon={<ReloadOutlined />} onClick={reload} title="刷新" />
      <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
        新增角色
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '角色总数', value: total, prefix: <TeamOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '权限点总数', value: permissions.length, prefix: <SafetyCertificateOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '权限覆盖业务域', value: domainCount, valueStyle: { color: '#1677ff' } }}
      />
    </StatisticCard.Group>
  );

  return (
    <ModulePage
      title="角色管理"
      description="维护角色及其权限分配，支持为一个角色绑定多个权限点。"
      extra={toolbar}
      statistics={statistics}
    >
      <Table
        columns={columns}
        dataSource={roles}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个角色`,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          }
        }}
      />
      <Drawer
        title={isEditMode ? `编辑角色 · ${currentRole?.name ?? ''}` : '新增角色'}
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
            label="角色名称"
            rules={[{ required: true, message: '请输入角色名称!' }]}
          >
            <Input placeholder="请输入角色名称" />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入角色描述!' }]}
          >
            <Input.TextArea placeholder="请输入角色描述，说明该角色的职责范围" rows={4} />
          </Form.Item>
          <Form.Item
            name="permissionIds"
            label="权限"
            tooltip="角色拥有的权限点集合，账号权限 = 其所有角色的权限并集"
          >
            <Select mode="multiple" placeholder="请选择权限" style={{ width: '100%' }} maxTagCount="responsive">
              {permissions.map(permission => (
                <Select.Option key={permission.id} value={permission.id}>{permission.name} ({permission.code})</Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Drawer>
    </ModulePage>
  );
};

export default RoleManagement;
