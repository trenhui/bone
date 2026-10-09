import React, { useCallback, useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined, CheckCircleOutlined, CloseCircleOutlined, SearchOutlined } from '@ant-design/icons';
import { connectorApi } from '../services/api';
import { normalizeTotal } from '@bone/shared-utils';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
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
  const [keyword, setKeyword] = useState('');

  const connectorTypes = [
    { value: 'REST', label: 'REST API' },
    { value: 'SOAP', label: 'SOAP WebService' },
    { value: 'JDBC', label: '数据库' },
    { value: 'FTP', label: 'FTP' },
    { value: 'MQ', label: '消息队列' },
    { value: 'HTTP', label: 'HTTP' },
    { value: 'HTTPS', label: 'HTTPS' },
    { value: 'REDIS', label: 'Redis' },
    { value: 'ELASTICSEARCH', label: 'Elasticsearch' },
    { value: 'MONGO_DB', label: 'MongoDB' },
    { value: 'S3', label: 'S3 / MinIO' },
    { value: 'SFTP', label: 'SFTP' },
    { value: 'FILE', label: '文件' },
    { value: 'SMTP', label: 'SMTP' },
    { value: 'POP3', label: 'POP3' },
    { value: 'IMAP', label: 'IMAP' },
    { value: 'KAFKA', label: 'Kafka' },
    { value: 'RABBITMQ', label: 'RabbitMQ' },
    { value: 'AZURE_BLOB', label: 'Azure Blob' },
    { value: 'GOOGLE_CLOUD_STORAGE', label: 'GCS' },
  ];

  const fetchConnectors = useCallback(async () => {
    setLoading(true);
    try {
      const response = await connectorApi.getConnectors({ page: page, size: pageSize });
      setConnectors(response.data.records);
      setTotal(normalizeTotal(response.data.total));
    } catch {
      message.error('获取连接器列表失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    void fetchConnectors();
  }, [fetchConnectors]);

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

  const handleDelete = async (id: string) => {
    try {
      await connectorApi.deleteConnector(id);
      message.success('删除成功');
      fetchConnectors();
    } catch (error) {
      message.error('删除失败');
    }
  };

  const handleTest = async (id: string) => {
    try {
      const response = await connectorApi.testConnector(id);
      if (response.data.success) {
        message.success('测试成功');
      } else {
        message.error(`测试失败: ${response.data.message}`);
      }
    } catch (error) {
      message.error('测试失败');
    }
  };

  const handleEnable = async (id: string) => {
    try {
      await connectorApi.enableConnector(id);
      message.success('启用成功');
      fetchConnectors();
    } catch (error) {
      message.error('启用失败');
    }
  };

  const handleDisable = async (id: string) => {
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

  const filteredConnectors = connectors.filter((c) => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return true;
    const typeLabel = connectorTypes.find((t) => t.value === c.type)?.label ?? c.type ?? '';
    return [c.name, c.type, typeLabel].some((v) => (v ?? '').toLowerCase().includes(kw));
  });

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
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: Connector) => (
        <div>
          <AuthButton
            code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            style={{ marginRight: 8 }}
          >
            编辑
          </AuthButton>
          {/* 测试会真实调用下游连接器（发起一次外部请求），按写操作门禁 */}
          <AuthButton
            code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
            type="link"
            icon={<ReloadOutlined />}
            onClick={() => handleTest(record.id)}
            style={{ marginRight: 8 }}
          >
            测试
          </AuthButton>
          {record.status === 'ENABLED' ? (
            <AuthButton
              code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
              type="link"
              danger
              onClick={() => handleDisable(record.id)}
              style={{ marginRight: 8 }}
            >
              禁用
            </AuthButton>
          ) : (
            <AuthButton
              code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
              type="link"
              onClick={() => handleEnable(record.id)}
              style={{ marginRight: 8 }}
            >
              启用
            </AuthButton>
          )}
          {/* 删除被 Popconfirm 包住，只能用 <Auth> 包外层：AuthButton 无权限时返回 null，
              会成为 Popconfirm 的 children，antd 对其 cloneElement 会抛异常。 */}
          <Auth code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}>
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
          </Auth>
        </div>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="连接器管理"
        extra={
          <AuthButton
            code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
          >
            新建连接器
          </AuthButton>
        }
      >
        <Input
          placeholder="搜索连接器名称 / 类型"
          prefix={<SearchOutlined />}
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          style={{ width: 300, marginBottom: 16 }}
        />
        <Table
          columns={columns}
          dataSource={filteredConnectors}
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
        onCancel={() => setModalVisible(false)}
        width={600}
        // 自定义 footer 才能给「确定」加门禁（antd 默认确定/取消按钮由 ModalContext 内部
        // 渲染，页面拿不到元素）。传数组时 antd 原样渲染、不做 cloneElement。
        footer={[
          <AuthButton
            key="ok"
            code={BonePermissionCodes.INTEGRATION_CONNECTORS_WRITE}
            type="primary"
            onClick={() => void handleSubmit()}
          >
            确定
          </AuthButton>,
          <Button key="cancel" onClick={() => setModalVisible(false)}>
            取消
          </Button>,
        ]}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            // JSON 模板必须放Form 的 initialValues：
            // config 是受控字段（name="config"），写在 TextArea 的 defaultValue 上
            // 不会被 antd 注入 ⇒ 用户看到的永远是空 textarea（antd 会告警
            // "`defaultValue` will not work on controlled Field"）。
            config: `{
  "url": "",
  "username": "",
  "password": ""
}`,
          }}
        >
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
            <TextArea rows={6} placeholder="请输入 JSON 格式的配置" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};