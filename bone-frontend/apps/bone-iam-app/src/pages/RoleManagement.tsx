import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Popconfirm, Space } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { apiService } from '../services/api';
import { Role, CreateRoleRequest, UpdateRoleRequest } from '../types';

const { Option } = Select;

const RoleManagement: React.FC = () => {
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentRole, setCurrentRole] = useState<Role | null>(null);
  const [form] = Form.useForm();

  const fetchRoles = async () => {
    setLoading(true);
    try {
      const response = await apiService.getRoles(page, pageSize, keyword);
      if (response.code === 200) {
        setRoles(response.data.data);
        setTotal(response.data.total);
      }
    } catch (error) {
      message.error('获取角色列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchPermissions = async () => {
    try {
      const response = await apiService.getPermissions(1, 200);
      if (response.code === 200) {
        setPermissions(response.data.data);
      }
    } catch (error) {
      message.error('获取权限列表失败');
    }
  };

  useEffect(() => {
    fetchRoles();
    fetchPermissions();
  }, [page, pageSize, keyword]);

  const handleAddRole = () => {
    setIsEditMode(false);
    setCurrentRole(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEditRole = (role: Role) => {
    setIsEditMode(true);
    setCurrentRole(role);
    form.setFieldsValue({
      name: role.name,
      description: role.description,
      permissionIds: role.permissions.map(permission => permission.id)
    });
    setIsModalVisible(true);
  };

  const handleDeleteRole = async (id: string) => {
    try {
      const response = await apiService.deleteRole(id);
      if (response.code === 200) {
        message.success('删除角色成功');
        fetchRoles();
      }
    } catch (error) {
      message.error('删除角色失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (isEditMode && currentRole) {
        const updateData: UpdateRoleRequest = {
          name: values.name,
          description: values.description,
          permissionIds: values.permissionIds
        };
        const response = await apiService.updateRole(currentRole.id, updateData);
        if (response.code === 200) {
          message.success('更新角色成功');
          setIsModalVisible(false);
          fetchRoles();
        }
      } else {
        const createData: CreateRoleRequest = {
          name: values.name,
          description: values.description,
          permissionIds: values.permissionIds
        };
        const response = await apiService.createRole(createData);
        if (response.code === 200) {
          message.success('创建角色成功');
          setIsModalVisible(false);
          fetchRoles();
        }
      }
    } catch (error) {
      message.error('操作失败');
    }
  };

  const columns = [
    {
      title: '角色名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '权限数量',
      dataIndex: 'permissions',
      key: 'permissions',
      render: (permissions: any[]) => permissions.length
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Role) => (
        <Space size="middle">
          <Button icon={<EditOutlined />} onClick={() => handleEditRole(record)} />
          <Popconfirm
            title="确定要删除这个角色吗？"
            onConfirm={() => handleDeleteRole(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2>角色管理</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            placeholder="搜索角色名称"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAddRole}>
            新增角色
          </Button>
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={roles}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          onChange: (page, pageSize) => {
            setPage(page);
            setPageSize(pageSize);
          }
        }}
      />
      <Modal
        title={isEditMode ? '编辑角色' : '新增角色'}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
        width={600}
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
            <Input.TextArea placeholder="请输入角色描述" rows={4} />
          </Form.Item>
          <Form.Item
            name="permissionIds"
            label="权限"
            rules={[{ required: true, message: '请选择权限!' }]}
          >
            <Select mode="multiple" placeholder="请选择权限" style={{ width: '100%' }} maxTagCount="responsive">
              {permissions.map(permission => (
                <Option key={permission.id} value={permission.id}>{permission.name} ({permission.code})</Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default RoleManagement;