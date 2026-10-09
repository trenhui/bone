import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, message, Typography, Space, Card, Tag, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import { pageRecords, templateApi } from '../services/api';
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

interface TemplateItem {
  id: string;
  name: string;
  code: string;
  type: string;
  language: string;
  engine: string;
  version: string;
  status: 'ACTIVE' | 'INACTIVE' | string;
  content?: string;
  description?: string;
}

const TemplateManagement: React.FC = () => {
  const [templates, setTemplates] = useState<TemplateItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [previewModalVisible, setPreviewModalVisible] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<TemplateItem | null>(null);
  const [form] = Form.useForm();
  const [previewTemplate, setPreviewTemplate] = useState<TemplateItem | null>(null);
  const [keyword, setKeyword] = useState('');

  const loadTemplates = useCallback(async () => {
    try {
      setLoading(true);
      const response = await templateApi.getList({ page: 1, size: 100 });
      setTemplates(pageRecords(response.data) as unknown as TemplateItem[]);
    } catch (error) {
      message.error('加载模板失败');
      console.error('加载模板失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTemplates();
  }, [loadTemplates]);

  const handleAddTemplate = (): void => {
    form.resetFields();
    setEditingTemplate(null);
    setModalVisible(true);
  };

  const handleEditTemplate = (template: TemplateItem): void => {
    form.setFieldsValue(template);
    setEditingTemplate(template);
    setModalVisible(true);
  };

  const handleDeleteTemplate = async (id: string): Promise<void> => {
    try {
      await templateApi.delete(id);
      message.success('模板删除成功');
      void loadTemplates();
    } catch (error) {
      message.error('模板删除失败');
      console.error('模板删除失败:', error);
    }
  };

  const handlePreviewTemplate = (template: TemplateItem): void => {
    setPreviewTemplate(template);
    setPreviewModalVisible(true);
  };

  const handleSaveTemplate = async (): Promise<void> => {
    try {
      const values = await form.validateFields();

      if (editingTemplate) {
        await templateApi.update(editingTemplate.id, values);
        message.success('模板更新成功');
      } else {
        await templateApi.create(values);
        message.success('模板创建成功');
      }

      setModalVisible(false);
      void loadTemplates();
    } catch (error) {
      message.error('保存模板失败');
      console.error('保存模板失败:', error);
    }
  };

  const handlePublishTemplate = async (id: string): Promise<void> => {
    try {
      await templateApi.publish(id);
      message.success('模板发布成功');
      void loadTemplates();
    } catch (error) {
      message.error('模板发布失败');
      console.error('模板发布失败:', error);
    }
  };

  // 表格列定义
  const filteredTemplates = templates.filter((t) => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return true;
    return [t.name, t.code, t.type].some((v) => (v ?? '').toLowerCase().includes(kw));
  });

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
      dataIndex: 'templateVersion',
      key: 'templateVersion',
      render: (v: string, record: TemplateItem) => v ?? record.version ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>
          {status === 'ACTIVE' ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: TemplateItem) => (
        <Space size="middle">
          <Button
            icon={<EyeOutlined />}
            onClick={() => handlePreviewTemplate(record)}
          >
            预览
          </Button>
          <AuthButton
            code={BonePermissionCodes.GENERATOR_TEMPLATES_WRITE}
            icon={<EditOutlined />}
            onClick={() => handleEditTemplate(record)}
          >
            编辑
          </AuthButton>
          <AuthButton
            code={BonePermissionCodes.GENERATOR_TEMPLATES_WRITE}
            type="primary"
            disabled={record.status === 'ACTIVE'}
            onClick={() => handlePublishTemplate(record.id)}
          >
            发布
          </AuthButton>
          {/* 删除被 Popconfirm 包住，只能用 <Auth> 包外层：AuthButton 无权限时返回 null，
              会成为 Popconfirm 的 children，antd 对其 cloneElement 会抛异常。 */}
          <Auth code={BonePermissionCodes.GENERATOR_TEMPLATES_WRITE}>
            <Popconfirm
              title="确认删除该模板？"
              description="删除后不可恢复"
              okText="删除"
              okButtonProps={{ danger: true }}
              cancelText="取消"
              onConfirm={() => handleDeleteTemplate(record.id)}
            >
              <Button danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Auth>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card>
        <Title level={4}>模板管理</Title>
        
        <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Input
            placeholder="搜索模板名称 / 编码 / 类型"
            prefix={<SearchOutlined />}
            allowClear
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <AuthButton
            code={BonePermissionCodes.GENERATOR_TEMPLATES_WRITE}
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddTemplate}
          >
            新建模板
          </AuthButton>
        </div>
        
        <Table
          columns={columns}
          dataSource={filteredTemplates}
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
        width={800}
        // 自定义 footer 才能给「确定」加门禁（antd 默认确定/取消按钮由 ModalContext 内部
        // 渲染，页面拿不到元素）。传数组时 antd 原样渲染、不做 cloneElement。
        footer={[
          <AuthButton
            key="ok"
            code={BonePermissionCodes.GENERATOR_TEMPLATES_WRITE}
            type="primary"
            onClick={() => void handleSaveTemplate()}
          >
            确定
          </AuthButton>,
          <Button key="cancel" onClick={() => setModalVisible(false)}>
            取消
          </Button>,
        ]}
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
              {/* type 必须与后端 FileGenerator#supports 的模板码一致，否则存进去不会被任何生成器认领 */}
              <Option value="entity">聚合根实体</Option>
              <Option value="repository">域仓储接口</Option>
              <Option value="createCommand">创建命令</Option>
              <Option value="updateCommand">更新命令</Option>
              <Option value="queryDto">应用层读模型</Option>
              <Option value="applicationService">应用服务</Option>
              <Option value="createRequest">创建请求体</Option>
              <Option value="updateRequest">更新请求体</Option>
              <Option value="pageQuery">分页查询入参</Option>
              <Option value="response">响应契约</Option>
              <Option value="assembler">Web 装配器</Option>
              <Option value="controller">Web 控制器</Option>
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
              {/* 值必须全大写：与 gen_code_template 种子数据及 DDL 默认值一致。
                  小写会导致模板按引擎名匹配时查不到（engine 当前尚无调度消费点，
                  但这是数据卫生，见 bone-init.sql:1347）。 */}
              <Option value="FREEMARKER">Freemarker</Option>
              <Option value="VELOCITY">Velocity</Option>
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