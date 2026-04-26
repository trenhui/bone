import React, { useState, useEffect } from 'react';
import { Card, Form, Input, Button, Switch, Tag, message } from 'antd';
import { UserOutlined, LockOutlined, MoonOutlined, SunOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSettingsStore, useUserStore } from '@/store';
import { ThemeEnum } from '@/enums/ThemeEnum';
import defaultSettings from '@/settings';
import './Login.css';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [isDark, setIsDark] = useState(defaultSettings.theme === ThemeEnum.DARK);
  const [isCapslock, setIsCapslock] = useState(false);
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  
  const settingsStore = useSettingsStore();
  const userStore = useUserStore();

  // 检查url中的redirect参数
  useEffect(() => {
    const redirect = searchParams.get('redirect') || '/';
    if (redirect) {
      // 这里可以处理redirect逻辑
    }
  }, [searchParams]);

  const onFinish = async (values: any) => {
    try {
      setLoading(true);
      await userStore.login(values);
      message.success('登录成功');
      
      // 解析redirect
      const redirect = searchParams.get('redirect');
      if (redirect) {
        try {
          const url = new URL(redirect, window.location.origin);
          navigate(url.pathname + url.search);
        } catch {
          navigate(redirect);
        }
      } else {
        navigate('/');
      }
    } catch (error) {
      message.error('登录失败');
    } finally {
      setLoading(false);
    }
  };

  const toggleTheme = () => {
    const newTheme = settingsStore.theme === ThemeEnum.DARK ? ThemeEnum.LIGHT : ThemeEnum.DARK;
    settingsStore.changeTheme(newTheme);
    setIsDark(newTheme === ThemeEnum.DARK);
  };

  const checkCapslock = (event: React.KeyboardEvent<HTMLInputElement>) => {
    setIsCapslock(event.getModifierState && event.getModifierState('CapsLock'));
  };

  return (
    <div className="login-container">
      {/* 顶部工具栏 */}
      <div className="top-bar">
        <Switch
          checked={isDark}
          onChange={toggleTheme}
          checkedChildren={<MoonOutlined />}
          unCheckedChildren={<SunOutlined />}
        />
      </div>

      {/* 登录表单 */}
      <Card className="login-card">
        <div className="text-center relative">
          <h2>{defaultSettings.title}</h2>
          <Tag className="ml-2 absolute-rt">{defaultSettings.version}</Tag>
        </div>

        <Form
          form={form}
          onFinish={onFinish}
          className="login-form"
          layout="vertical"
        >
          {/* 用户名 */}
          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
            ]}
          >
            <Input
              prefix={<UserOutlined className="mx-2" />}
              placeholder="请输入用户名"
              size="large"
              style={{ height: 48 }}
            />
          </Form.Item>

          {/* 密码 */}
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码长度不能少于6位' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined className="mx-2" />}
              placeholder="请输入密码"
              size="large"
              style={{ height: 48 }}
              onKeyUp={checkCapslock}
              onPressEnter={() => form.submit()}
            />
            {isCapslock && (
              <div style={{ marginTop: 8, color: '#faad14' }}>
                大写锁定已开启
              </div>
            )}
          </Form.Item>

          {/* 登录按钮 */}
          <Button
            type="primary"
            size="large"
            loading={loading}
            onClick={() => form.submit()}
            style={{ width: '100%' }}
          >
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
};

export default Login;
