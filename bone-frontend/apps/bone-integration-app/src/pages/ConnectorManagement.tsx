import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { connectorApi } from '../services/api';
import type { Connector, CreateConnectorReq, UpdateConnectorReq } from '../types';

const { Option } = Select;
const { TextArea } = Input;

export const ConnectorManagement: React.FC = () => {
  const [connectors, setConnectors] = useState<Connector[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [currentConnector, setCurrentConnector] = useState<Connector | null>(null);
  const [form] = Form.useForm();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const connectorTypes = [
    { value: 'REST', label: 'REST API' },
    { value: 'SOAP', label: 'SOAP WebService' },
    { value: 'JDBC', label: '数据库' },
    { value: 'FTP', label: 'FTP' },
    { value: 'MQ', label: '消息队列' },
  ];

  const fetchConnectors = async () => {
    setLoading(true);
    try {
      const response = await connectorApi.getConnectors({ pageNum: page, pageSize });
      setConnectors(response.data.data.list);
      setTotal(response.data.data.total);
    } catch (error) {
      message.error('获取连接器列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConnectors();
  }, [page, pageSize]);

  const handleAdd = () => {
    setIsEdit(false);
    setCurrentConnector(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (connector: Connector) => {
    setIsEdit(true);
    setCurrentConnector(connector);
    form.setFieldsValue({
      name: connector.name,
      type: connector.type,
      config: JSON.stringify(connector.config, null, 2),
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await connectorApi.deleteConnector(id);
      message.success('删除成功');
      fetchConnectors();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const response = await connectorApi.testConnector(id);
      if (response.data.data.success) {
        message.success('测试成功');
      } else {
        message.error(`测试失败: ${response.data.data.message}`);
      }
    } catch (error) {
      message.error('测试失败');
    }
  };

  const handleEnable = async (id: number) => {
    try {
      await connectorApi.enableConnector(id);
      message.success('启用成功');
      fetchConnectors();
    } catch (error) {
      message.error('启用失败');
    }
  };

  const handleDisable = async (id: number) => {
    try {
      await connectorApi.disableConnector(id);
      message.success('禁用成功');
      fetchConnectors();
    } catch (error) {
      message.error('禁用失败');
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const config = JSON.parse(values.config);
      
      if (isEdit && currentConnector) {
        const updateData: UpdateConnectorReq = {
          name: values.name,
          type: values.type,
          config,
        };
        await connectorApi.updateConnector(currentConnector.id, updateData);
        message.success('更新成功');
      } else {
        const createData: CreateConnectorReq = {
          name: values.name,
          type: values.type,
          config,
        };
        await connectorApi.createConnector(createData);
        message.success('创建成功');
      }
      
      setModalVisible(false);
      fetchConnectors();
    } catch (error) {
      message.error('操作失败');
    }
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const connectorType = connectorTypes.find(t => t.value === type);
        return connectorType ? connectorType.label : type;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <span>
          {status === 'ENABLED' ? (
            <span style={{ color: '#52c41a' }}><CheckCircleOutlined /> 启用</span>
          ) : (
            <span style={{ color: '#ff4d4f' }}><CloseCircleOutlined /> 禁用</span>
          )}
        </span>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Connector) => (
        <div>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            style={{ marginRight: 8 }}
          >
            编辑
          </Button>
          <Button
            type="link"
            icon={<ReloadOutlined />}
            onClick={() => handleTest(record.id)}
            style={{ marginRight: 8 }}
          >
            测试
          </Button>
          {record.status === 'ENABLED' ? (
            <Button
              type="link"
              danger
              onClick={() => handleDisable(record.id)}
              style={{ marginRight: 8 }}
            >
              禁用
            </Button>
          ) : (
            <Button
              type="link"
              onClick={() => handleEnable(record.id)}
              style={{ marginRight: 8 }}
            >
              启用
            </Button>
          )}
          <Popconfirm
            title="确定删除此连接器吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </div>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="连接器管理"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            新建连接器
          </Button>
        }
      >
        <Table
          columns={columns}
          dataSource={connectors}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: (page) => setPage(page),
            onShowSizeChange: (_, size) => setPageSize(size),
          }}
        />
      </Card>

      <Modal
        title={isEdit ? '编辑连接器' : '新建连接器'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入连接器名称' }]}
          >
            <Input placeholder="请输入连接器名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="类型"
            rules={[{ required: true, message: '请选择连接器类型' }]}
          >
            <Select placeholder="请选择连接器类型">
              {connectorTypes.map(type => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="config"
            label="配置"
            rules={[{ required: true, message: '请输入连接器配置' }]}
          >
            <TextArea
              rows={6}
              placeholder="请输入 JSON 格式的配置"
              defaultValue="{\n  \"url\": \"\",\n  \"username\": \"\",\n  \"password\": \"\"\n}"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};