import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Popconfirm, Space } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { apiService } from '../services/api';
import { Permission, CreatePermissionRequest, UpdatePermissionRequest } from '../types';

const { Option } = Select;

const PermissionManagement: React.FC = () => {
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isEditMode, setIsEditMode] = useState(false);
  const [currentPermission, setCurrentPermission] = useState<Permission | null>(null);
  const [form] = Form.useForm();

  const fetchPermissions = async () => {
    setLoading(true);
    try {
      const response = await apiService.getPermissions(page, pageSize, keyword);
      if (response.code === 200) {
        setPermissions(response.data.data);
        setTotal(response.data.total);
      }
    } catch (error) {
      message.error('获取权限列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPermissions();
  }, [page, pageSize, keyword]);

  const handleAddPermission = () => {
    setIsEditMode(false);
    setCurrentPermission(null);
    form.resetFields();
    setIsModalVisible(true);
  };

  const handleEditPermission = (permission: Permission) => {
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

  const handleDeletePermission = async (id: string) => {
    try {
      const response = await apiService.deletePermission(id);
      if (response.code === 200) {
        message.success('删除权限成功');
        fetchPermissions();
      }
    } catch (error) {
      message.error('删除权限失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (isEditMode && currentPermission) {
        const updateData: UpdatePermissionRequest = {
          name: values.name,
          description: values.description,
          resourceType: values.resourceType,
          action: values.action
        };
        const response = await apiService.updatePermission(currentPermission.id, updateData);
        if (response.code === 200) {
          message.success('更新权限成功');
          setIsModalVisible(false);
          fetchPermissions();
        }
      } else {
        const createData: CreatePermissionRequest = {
          name: values.name,
          code: values.code,
          description: values.description,
          resourceType: values.resourceType,
          action: values.action
        };
        const response = await apiService.createPermission(createData);
        if (response.code === 200) {
          message.success('创建权限成功');
          setIsModalVisible(false);
          fetchPermissions();
        }
      }
    } catch (error) {
      message.error('操作失败');
    }
  };

  const columns = [
    {
      title: '权限名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '权限编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Permission) => (
        <Space size="middle">
          <Button icon={<EditOutlined />} onClick={() => handleEditPermission(record)} />
          <Popconfirm
            title="确定要删除这个权限吗？"
            onConfirm={() => handleDeletePermission(record.id)}
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
        <h2>权限管理</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <Input
            placeholder="搜索权限名称或编码"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAddPermission}>
            新增权限
          </Button>
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={permissions}
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
        title={isEditMode ? '编辑权限' : '新增权限'}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
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
            rules={[{ required: true, message: '请输入权限编码!' }]}
            disabled={isEditMode}
          >
            <Input placeholder="请输入权限编码" />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入权限描述!' }]}
          >
            <Input.TextArea placeholder="请输入权限描述" rows={3} />
          </Form.Item>
          <Form.Item
            name="resourceType"
            label="资源类型"
            rules={[{ required: true, message: '请输入资源类型!' }]}
          >
            <Input placeholder="请输入资源类型" />
          </Form.Item>
          <Form.Item
            name="action"
            label="操作"
            rules={[{ required: true, message: '请输入操作!' }]}
          >
            <Input placeholder="请输入操作" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default PermissionManagement;