import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Space, Card, Typography, Divider } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { dataSourceApi, pageRecords } from '../services/api';
import { DataSource } from '../services/types';
import { useGeneratorStore } from '../store';

const { Title, Text } = Typography;
const { Option } = Select;
const { Password } = Input;

const DataSourceManagement: React.FC = () => {
  const [form] = Form.useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingDataSource, setEditingDataSource] = useState<DataSource | null>(null);
  const [loading, setLoading] = useState(false);
  const [testLoading, setTestLoading] = useState(false);
  
  const {
    dataSources,
    setDataSources,
    setLoadingDataSources,
  } = useGeneratorStore();

  // 数据库类型选项
  const databaseTypes = [
    { value: 'mysql', label: 'MySQL' },
    { value: 'postgresql', label: 'PostgreSQL' },
  ];

  // 加载数据源列表
  const loadDataSources = async () => {
    try {
      setLoadingDataSources(true);
      const response = await dataSourceApi.getList({ page: 1, size: 100 });
      setDataSources(pageRecords(response.data.data));
    } catch (error) {
      message.error('加载数据源失败');
      console.error('加载数据源失败:', error);
    } finally {
      setLoadingDataSources(false);
    }
  };

  // 监听数据库类型变化，自动设置默认端口
  const typeValue = Form.useWatch('type', form);
  useEffect(() => {
    if (typeValue) {
      let defaultPort = '';
      switch (typeValue) {
        case 'mysql':
          defaultPort = '3306';
          break;
        case 'postgresql':
          defaultPort = '5432';
          break;
        default:
          defaultPort = '3306';
      }
      form.setFieldsValue({ port: defaultPort });
    }
  }, [typeValue, form]);

  // 组件挂载时加载数据源列表
  useEffect(() => {
    loadDataSources();
  }, []);

  // 打开新增模态框
  const showAddModal = () => {
    form.resetFields();
    // 设置默认值
    form.setFieldsValue({
      type: 'mysql',
      host: 'localhost',
      port: '3306',
    });
    setEditingDataSource(null);
    setIsModalVisible(true);
  };

  // 打开编辑模态框
  const showEditModal = (dataSource: DataSource) => {
    setEditingDataSource(dataSource);
    form.setFieldsValue({
      name: dataSource.name,
      type: dataSource.type,
      host: dataSource.host,
      port: dataSource.port,
      database: dataSource.database,
      username: dataSource.username,
      password: dataSource.password,
    });
    setIsModalVisible(true);
  };

  // 测试连接
  const testConnection = async () => {
    try {
      setTestLoading(true);
      const values = form.getFieldsValue();
      // 先创建临时数据源以获取ID
      let dataSourceId = '';
      if (editingDataSource) {
        dataSourceId = editingDataSource.id;
      } else {
        // 创建临时数据源
        const createResponse = await dataSourceApi.create(values);
        dataSourceId = createResponse.data.data;
      }
      
      // 测试连接
      const response = await dataSourceApi.testConnection(dataSourceId);
      if (response.data.code === 200 && response.data.data) {
        message.success('连接成功');
      } else {
        message.error('连接失败: ' + response.data.message);
      }
      
      // 如果是新增的临时数据源，测试完成后删除
      if (!editingDataSource) {
        try {
          await dataSourceApi.delete(dataSourceId);
        } catch (error) {
          // 忽略删除错误，不影响测试结果
          console.error('删除临时数据源失败:', error);
        }
      }
    } catch (error) {
      message.error('连接失败');
      console.error('测试连接失败:', error);
    } finally {
      setTestLoading(false);
    }
  };

  // 保存数据源
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      
      if (editingDataSource) {
        // 更新数据源
        await dataSourceApi.update(editingDataSource.id, values);
        message.success('更新成功');
      } else {
        // 创建数据源
        await dataSourceApi.create(values);
        message.success('创建成功');
      }
      
      setIsModalVisible(false);
      loadDataSources();
    } catch (error) {
      message.error('保存失败');
      console.error('保存数据源失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 删除数据源
  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个数据源吗？',
      onOk: async () => {
        try {
          setLoading(true);
          await dataSourceApi.delete(id);
          message.success('删除成功');
          loadDataSources();
        } catch (error) {
          message.error('删除失败');
          console.error('删除数据源失败:', error);
        } finally {
          setLoading(false);
        }
      },
    });
  };

  // 表格列定义
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
        const typeMap: Record<string, string> = {
          mysql: 'MySQL',
          postgresql: 'PostgreSQL',
        };
        return typeMap[type] ?? type;
      },
    },
    {
      title: '连接信息',
      dataIndex: 'host',
      key: 'host',
      render: (host: string, record: DataSource) => (
        <Text>
          {host}:{record.port}/{record.database}
        </Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const statusMap: Record<string, string> = {
          ACTIVE: '激活',
          INACTIVE: '未激活',
        };
        return statusMap[status] ?? status;
      },
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: DataSource) => (
        <Space size="middle">
          <Button
            type="primary"
            icon={<EditOutlined />}
            size="small"
            onClick={() => showEditModal(record)}
          >
            编辑
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            size="small"
            onClick={() => handleDelete(record.id)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
          <Title level={4}>数据源管理</Title>
          <Space>
            <Button
              type="primary"
              icon={<ReloadOutlined />}
              onClick={loadDataSources}
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={showAddModal}
            >
              新增数据源
            </Button>
          </Space>
        </div>
        
        <Table
          columns={columns}
          dataSource={dataSources}
          rowKey="id"
          pagination={false}
        />
      </Card>

      {/* 新增/编辑模态框 */}
      <Modal
        title={editingDataSource ? '编辑数据源' : '新增数据源'}
        open={isModalVisible}
        onOk={handleSave}
        onCancel={() => setIsModalVisible(false)}
        confirmLoading={loading}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          requiredMark={false}
        >
          <Form.Item
            name="name"
            label="数据源名称"
            rules={[{ required: true, message: '请输入数据源名称' }]}
          >
            <Input placeholder="请输入数据源名称" />
          </Form.Item>
          
          <Form.Item
            name="type"
            label="数据库类型"
            rules={[{ required: true, message: '请选择数据库类型' }]}
          >
            <Select placeholder="请选择数据库类型">
              {databaseTypes.map((type) => (
                <Option key={type.value} value={type.value}>
                  {type.label}
                </Option>
              ))}
            </Select>
          </Form.Item>
          
          <Form.Item
            name="host"
            label="主机地址"
            rules={[{ required: true, message: '请输入主机地址' }]}
          >
            <Input placeholder="请输入主机地址" />
          </Form.Item>
          
          <Form.Item
            name="port"
            label="端口"
            rules={[{ required: true, message: '请输入端口' }]}
          >
            <Input placeholder="请输入端口" />
          </Form.Item>
          
          <Form.Item
            name="database"
            label="数据库名称"
            rules={[{ required: true, message: '请输入数据库名称' }]}
          >
            <Input placeholder="请输入数据库名称" />
          </Form.Item>
          
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input placeholder="请输入用户名" />
          </Form.Item>
          
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Password placeholder="请输入密码" />
          </Form.Item>
          
          <Divider />
          
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <Button
              type="primary"
              onClick={testConnection}
              loading={testLoading}
              style={{ marginRight: '12px' }}
            >
              测试连接
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default DataSourceManagement;