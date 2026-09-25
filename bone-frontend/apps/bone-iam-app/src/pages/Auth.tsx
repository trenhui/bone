import React, { useState } from 'react';
import { Card, Form, Input, Button, message, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import * as api from '../services/api';
import type { LoginRequest } from '../types';

const { Text } = Typography;

const Auth: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (values: LoginRequest) => {
    setLoading(true);
    try {
      const response = await api.login(values);
      if (response.code === 200) {
        localStorage.setItem('token', response.data.token);
        if (response.data.refreshToken) {
          localStorage.setItem('refreshToken', response.data.refreshToken);
        }
        localStorage.setItem('username', response.data.account.username);
        // 身份分流（详设 §2.9）：tenantId=0（或旧后端缺省）为平台管理员视角；>0 为租户管理员视角。
        const tenantId = (response.data.account as unknown as { tenantId?: string | number }).tenantId;
        const tenantName = (response.data.account as unknown as { tenantName?: string }).tenantName;
        localStorage.setItem('tenantId', tenantId != null ? String(tenantId) : '0');
        localStorage.setItem('tenantName', tenantName || '平台');
        message.success('登录成功');
        navigate('/accounts');
      } else {
        message.error(response.message || '登录失败');
      }
    } catch {
      message.error('登录失败，请检查用户名和密码');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f0f2f5' }}>
      <Card title="IAM 系统登录" style={{ width: 400 }}>
        <div style={{ marginBottom: 16, padding: 12, backgroundColor: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 4 }}>
          <Text strong>演示账号（单登录入口，登录后按身份自动分流）：</Text>
          <div>平台管理员：admin / 123456（租户管理、权限目录、全模块）</div>
          <div>租户管理员：tenant_admin / 123456（仅本租户功能）</div>
        </div>
        <Form
          name="login"
          initialValues={{ remember: true }}
          onFinish={handleLogin}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名!' }]}
          >
            <Input prefix={<UserOutlined className="site-form-item-icon" />} placeholder="用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码!' }]}
          >
            <Input
              prefix={<LockOutlined className="site-form-item-icon" />}
              type="password"
              placeholder="密码"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" style={{ width: '100%' }} loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default Auth;
