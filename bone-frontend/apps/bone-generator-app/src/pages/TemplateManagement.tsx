import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Typography, Space, InputNumber, Card } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { codeTemplateApi } from '../services/api';

const { Title, Text } = Typography;
const { TextArea } = Input;

const TemplateManagement: React.FC = () => {
  const [templates, setTemplates] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<any>(null);
  const [form] = Form.useForm();
  const [previewTemplate, setPreviewTemplate] = useState<any>(null);

  // 加载模板列表
  const loadTemplates = async () => {
    try {
      setLoading(true);
      const response = await codeTemplateApi.getList({ page: 1, size: 100 });
      setTemplates(response.data.data.list || []);
    } catch (error) {
      message.error('加载模板失败');
      console.error('加载模板失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 组件挂载时加载模板列表
  useEffect(() => {
    loadTemplates();
  }, []);

  // 处理新增模板
  const handleAddTemplate = () => {
    form.resetFields();
    setEditingTemplate(null);
    setModalVisible(true);
  };

  // 处理编辑模板
  const handleEditTemplate = (template: any) => {
    form.setFieldsValue(template);
    setEditingTemplate(template);
    setModalVisible(true);
  };

  // 处理删除模板
  const handleDeleteTemplate = async (id: number) => {
    try {
      await codeTemplateApi.delete(id);
      message.success('模板删除成功');
      loadTemplates();
    } catch (error) {
      message.error('模板删除失败');
      console.error('模板删除失败:', error);
    }
  };

  // 处理预览模板
  const handlePreviewTemplate = (template: any) => {
    setPreviewTemplate(template);
    setPreviewModalVisible(true);
  };

  // 处理保存模板
  const handleSaveTemplate = async () => {
    try {
      const values = await form.validateFields();
      
      if (editingTemplate) {
        // 更新模板
        await codeTemplateApi.update(editingTemplate.id, values);
        message.success('模板更新成功');
      } else {
        // 创建模板
        await codeTemplateApi.create(values);
        message.success('模板创建成功');
      }
      
      setModalVisible(false);
      loadTemplates();
    } catch (error) {
      message.error('保存模板失败');
      console.error('保存模板失败:', error);
    }
  };

  // 处理发布模板
  const handlePublishTemplate = async (id: number) => {
    try {
      await codeTemplateApi.publish(id);
      message.success('模板发布成功');
      loadTemplates();
    } catch (error) {
      message.error('模板发布失败');
      console.error('模板发布失败:', error);
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '模板名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '模板编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
    },
    {
      title: '语言',
      dataIndex: 'language',
      key: 'language',
    },
    {
      title: '模板引擎',
      dataIndex: 'engine',
      key: 'engine',
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Text style={{ color: status === 'ACTIVE' ? 'green' : 'gray' }}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
        </Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Space size="middle">
          <Button
            icon={<EyeOutlined />}
            onClick={() => handlePreviewTemplate(record)}
          >
            预览
          </Button>
          <Button
            icon={<EditOutlined />}
            onClick={() => handleEditTemplate(record)}
          >
            编辑
          </Button>
          <Button
            type="primary"
            onClick={() => handlePublishTemplate(record.id)}
          >
            发布
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteTemplate(record.id)}
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
        <Title level={4}>模板管理</Title>
        
        <div style={{ marginBottom: '16px', textAlign: 'right' }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddTemplate}
          >
            新建模板
          </Button>
        </div>
        
        <Table
          columns={columns}
          dataSource={templates}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
          }}
        />
      </Card>

      {/* 新增/编辑模板弹窗 */}
      <Modal
        title={editingTemplate ? '编辑模板' : '新建模板'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={handleSaveTemplate}
        width={800}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input placeholder="请输入模板名称" />
          </Form.Item>
          
          <Form.Item
            name="code"
            label="模板编码"
            rules={[{ required: true, message: '请输入模板编码' }]}
          >
            <Input placeholder="请输入模板编码" />
          </Form.Item>
          
          <Form.Item
            name="type"
            label="类型"
            rules={[{ required: true, message: '请选择模板类型' }]}
          >
            <Select placeholder="请选择模板类型">
              <Option value="entity">实体</Option>
              <Option value="repository">仓储</Option>
              <Option value="controller">控制器</Option>
              <Option value="service">服务</Option>
              <Option value="dto">数据传输对象</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="language"
            label="语言"
            rules={[{ required: true, message: '请选择语言' }]}
          >
            <Select placeholder="请选择语言">
              <Option value="java">Java</Option>
              <Option value="kotlin">Kotlin</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="engine"
            label="模板引擎"
            rules={[{ required: true, message: '请选择模板引擎' }]}
          >
            <Select placeholder="请选择模板引擎">
              <Option value="freemarker">Freemarker</Option>
              <Option value="velocity">Velocity</Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="version"
            label="版本"
            rules={[{ required: true, message: '请输入版本' }]}
          >
            <Input placeholder="请输入版本" />
          </Form.Item>
          
          <Form.Item
            name="content"
            label="模板内容"
            rules={[{ required: true, message: '请输入模板内容' }]}
          >
            <TextArea
              rows={10}
              placeholder="请输入模板内容"
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          
          <Form.Item
            name="description"
            label="描述"
          >
            <TextArea
              rows={3}
              placeholder="请输入模板描述"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 预览模板弹窗 */}
      <Modal
        title="模板预览"
        open={previewModalVisible}
        onCancel={() => setPreviewModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setPreviewModalVisible(false)}>
            关闭
          </Button>,
        ]}
        width={800}
      >
        {previewTemplate && (
          <div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>模板名称：</Text>
              <Text>{previewTemplate.name}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>模板编码：</Text>
              <Text>{previewTemplate.code}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>类型：</Text>
              <Text>{previewTemplate.type}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>语言：</Text>
              <Text>{previewTemplate.language}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>模板引擎：</Text>
              <Text>{previewTemplate.engine}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>版本：</Text>
              <Text>{previewTemplate.version}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>描述：</Text>
              <Text>{previewTemplate.description}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>模板内容：</Text>
              <pre style={{ backgroundColor: '#f5f5f5', padding: '16px', borderRadius: '4px', overflow: 'auto' }}>
                {previewTemplate.content}
              </pre>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default TemplateManagement;