import React, { useState } from 'react';
import { Card, Form, Input, Select, Button, message, Space } from 'antd';
import { SaveOutlined, CancelOutlined } from '@ant-design/icons';

const { Option } = Select;

interface ConfigForm {
  name: string;
  code: string;
  description: string;
  status: number;
  type: string;
}

const BizIdentityConfigCreate: React.FC = () => {
  const [form] = Form.useForm<ConfigForm>();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: ConfigForm) => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        message.success('创建主体专属配置成功');
        form.resetFields();
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('创建主体专属配置失败');
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
  };

  return (
    <div style={{ padding: '20px' }}>
      <Card title="创建主体专属配置">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            status: 1,
            type: '企业',
          }}
        >
          <Form.Item
            name="name"
            label="配置名称"
            rules={[{ required: true, message: '请输入配置名称' }]}
          >
            <Input placeholder="请输入配置名称" />
          </Form.Item>

          <Form.Item
            name="code"
            label="配置编码"
            rules={[{ required: true, message: '请输入配置编码' }]}
          >
            <Input placeholder="请输入配置编码" />
          </Form.Item>

          <Form.Item
            name="type"
            label="主体类型"
            rules={[{ required: true, message: '请选择主体类型' }]}
          >
            <Select placeholder="请选择主体类型">
              <Option value="企业">企业</Option>
              <Option value="机构">机构</Option>
              <Option value="团体">团体</Option>
              <Option value="个人">个人</Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入描述' }]}
          >
            <Input.TextArea placeholder="请输入描述" rows={4} />
          </Form.Item>

          <Form.Item
            name="status"
            label="状态"
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select placeholder="请选择状态">
              <Option value={1}>启用</Option>
              <Option value={0}>禁用</Option>
            </Select>
          </Form.Item>

          <Form.Item style={{ textAlign: 'right' }}>
            <Space>
              <Button type="primary" icon={<SaveOutlined />} loading={loading} htmlType="submit">
                保存
              </Button>
              <Button icon={<CancelOutlined />} onClick={handleCancel}>
                取消
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default BizIdentityConfigCreate;