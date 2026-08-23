import { useState } from 'react';
import { Card, Form, Input, Button, Avatar, Descriptions, App as AntdApp } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { authService } from '@bone/shared-services';

interface ProfilePageProps {
  user: { name: string } | null;
}

export default function ProfilePage({ user }: ProfilePageProps): JSX.Element {
  const { message: messageApi } = AntdApp.useApp();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const handleChangePassword = async (values: {
    oldPassword: string;
    newPassword: string;
  }): Promise<void> => {
    setSubmitting(true);
    try {
      await authService.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      messageApi.success('密码修改成功');
      form.resetFields();
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      messageApi.error(err?.response?.data?.message || '密码修改失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '8px 0' }}>
      <Card title="个人中心" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Avatar size={64} icon={<UserOutlined />} />
          <div>
            <div style={{ fontSize: 18, fontWeight: 600 }}>{user?.name}</div>
            <div style={{ color: '#888' }}>已登录用户</div>
          </div>
        </div>
        <Descriptions style={{ marginTop: 16 }} column={1} title="账户信息">
          <Descriptions.Item label="用户名">{user?.name ?? '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="修改密码">
        <Form
          form={form}
          layout="vertical"
          onFinish={handleChangePassword}
          style={{ maxWidth: 420 }}
        >
          <Form.Item
            name="oldPassword"
            label="当前密码"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入当前密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, min: 6, message: '密码长度至少6位' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请确认新密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting}>
              保存修改
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
