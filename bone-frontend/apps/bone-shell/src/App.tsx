import { useEffect, useState, useContext } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, Space, message, Form, Input, Card, Switch, Popover, Tooltip } from 'antd';
const { Password } = Input;
import axios from 'axios';
import {
  UserOutlined, LogoutOutlined, DashboardOutlined, UserAddOutlined,
  LockOutlined, DatabaseOutlined, LinkOutlined, SettingOutlined,
  SunOutlined, MoonOutlined, AppstoreOutlined, CodeOutlined,
  LayoutOutlined, SettingOutlined as SettingIcon,
} from '@ant-design/icons';
import {
  applyTheme,
  BoneAppProvider,
  publishThemeChange,
  readStoredTheme,
  resolveThemeMode,
  themePreferenceLabel,
  type Theme,
} from '@bone/ui';
import { initGlobalState, registerMicroApps, start } from 'qiankun';
import './App.css';

const boneGlobalActions = initGlobalState({
  themeMode: readStoredTheme(),
  user: null,
});

const { Header, Sider, Content } = Layout;

import DashboardPage from './pages/DashboardPage';
import {
  LayoutContext,
  MenuConfigContext,
  ThemeContext,
  type ShellMenuItem,
} from './shellContext';

function App() {
  const [collapsed, setCollapsed] = useState(false);
  const [user, setUser] = useState(() => {
    // 从localStorage中读取用户信息
    const savedUser = localStorage.getItem('bone-user');
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme());
  const resolvedTheme = resolveThemeMode(theme);
  const [layoutMode, setLayoutMode] = useState<'side' | 'top' | 'mix'>('side');
  const [menuConfig, setMenuConfig] = useState<ShellMenuItem[]>([
    { key: 'dashboard', label: '首页', icon: <DashboardOutlined />, path: '/', enabled: true },
    { key: 'iam', label: 'IAM管理', icon: <UserAddOutlined />, path: '/iam', enabled: true },
    { key: 'metadata', label: '元数据管理', icon: <DatabaseOutlined />, path: '/metadata', enabled: true },
    { key: 'masterdata', label: '主数据管理', icon: <DatabaseOutlined />, path: '/masterdata', enabled: true },
    { key: 'integration', label: '集成管理', icon: <LinkOutlined />, path: '/integration', enabled: true },
    { key: 'system', label: '系统管理', icon: <SettingOutlined />, path: '/system', enabled: true },
    { key: 'extension', label: '扩展管理', icon: <AppstoreOutlined />, path: '/extension', enabled: true },
    { key: 'generator', label: '代码生成', icon: <CodeOutlined />, path: '/generator', enabled: true },
  ]);

  useEffect(() => {
    applyTheme(theme);
  }, []);

  useEffect(() => {
    applyTheme(theme);
    publishThemeChange(theme);
    boneGlobalActions.setGlobalState({ themeMode: theme, user });
  }, [theme, user]);

  useEffect(() => {
    // 启动 qiankun
    // qiankun 类型定义未覆盖 Vite importEntry 钩子，运行时仍生效
    start({
      sandbox: {
        strictStyleIsolation: true,
        experimentalStyleIsolation: true,
      },
      importEntry: {
        getTemplate: (tpl: string) => {
          let processedTpl = tpl.replace(/<script[^>]*react-refresh[^>]*>.*?<\/script>/gis, '');
          processedTpl = processedTpl.replace(/<script[^>]*type="module"[^>]*>.*?<\/script>/gis, '');
          processedTpl = processedTpl.replace(/<script[^>]*vite[^>]*>.*?<\/script>/gis, '');
          return processedTpl;
        },
        getScriptValue: (scriptText: string) => {
          if (
            scriptText.includes('react-refresh') ||
            scriptText.includes('injectIntoGlobalHook') ||
            scriptText.includes('vite/client')
          ) {
            return '';
          }
          return scriptText;
        },
      },
    } as Parameters<typeof start>[0]);
  }, []);

  const microAppProps = (name: string) => ({
    name,
    user,
    themeMode: theme,
  });

  useEffect(() => {
    registerMicroApps([
      {
        name: 'bone-iam-app',
        entry: 'http://localhost:3003',
        container: '#micro-app-container',
        activeRule: '/iam',
        props: microAppProps('bone-iam-app'),
      },
      {
        name: 'bone-metadata-app',
        entry: 'http://localhost:3004',
        container: '#micro-app-container',
        activeRule: '/metadata',
        props: microAppProps('bone-metadata-app'),
      },
      {
        name: 'bone-masterdata-app',
        entry: 'http://localhost:3005',
        container: '#micro-app-container',
        activeRule: '/masterdata',
        props: microAppProps('bone-masterdata-app'),
      },
      {
        name: 'bone-integration-app',
        entry: 'http://localhost:3006',
        container: '#micro-app-container',
        activeRule: '/integration',
        props: microAppProps('bone-integration-app'),
      },
      {
        name: 'bone-system-app',
        entry: 'http://localhost:3007',
        container: '#micro-app-container',
        activeRule: '/system',
        props: microAppProps('bone-system-app'),
      },
      {
        name: 'bone-extension-app',
        entry: 'http://localhost:3008',
        container: '#micro-app-container',
        activeRule: '/extension',
        props: microAppProps('bone-extension-app'),
      },
      {
        name: 'bone-generator-app',
        entry: 'http://localhost:3009',
        container: '#micro-app-container',
        activeRule: '/generator',
        props: microAppProps('bone-generator-app'),
      },
    ]);
  }, [user, theme]);

  const toggleTheme = () => {
    const cycle: Theme[] = ['system', 'light', 'dark'];
    const next = cycle[(cycle.indexOf(theme) + 1) % cycle.length];
    setTheme(next);
  };

  // 切换布局模式
  const toggleLayoutMode = () => {
    const modes = ['side', 'top', 'mix'] as const;
    const currentIndex = modes.indexOf(layoutMode);
    const nextIndex = (currentIndex + 1) % modes.length;
    setLayoutMode(modes[nextIndex]);
  };

  // 更新菜单配置
  const updateMenuConfig = (key: string, enabled: boolean) => {
    setMenuConfig((prev) =>
      prev.map((item) => (item.key === key ? { ...item, enabled } : item)),
    );
  };

  // 登录处理
  const [requirePasswordChange, setRequirePasswordChange] = useState(false);

  const handleLogin = async (values: any) => {
    // 说明：这里先打通最小闭环（主应用登录 -> token 落地 -> 进入 IAM 微应用）
    setRequirePasswordChange(false);
    try {
      const resp = await axios.post('/api/v1/iam/login', {
        username: values.username,
        password: values.password,
      });
      if (resp?.data?.code !== 200) {
        message.error(resp?.data?.message || '登录失败');
        return;
      }
      const token = resp.data.data?.token;
      const username = resp.data.data?.account?.username || values.username;
      if (token) {
        localStorage.setItem('token', token);
      }
      localStorage.setItem('username', username);

      const userInfo = { name: username, forceChangePassword: false };
      setUser(userInfo);
      localStorage.setItem('bone-user', JSON.stringify(userInfo));
      message.success('登录成功');
    } catch (e: any) {
      message.error(e?.response?.data?.message || '登录失败，请检查用户名或密码');
    }
  };

  // 退出登录
  const handleLogout = async () => {
    try {
      await axios.post('/api/v1/iam/logout');
    } catch {
      // JWT 模式下后端可无状态，失败也不影响前端清理
    } finally {
      setUser(null);
      localStorage.removeItem('bone-user');
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      message.success('退出登录成功');
    }
  };

  // 过滤启用的菜单
  const enabledMenus = menuConfig.filter(item => item.enabled);

  return (
    <BoneAppProvider themeMode={theme}>
    <ThemeContext.Provider value={{ theme, resolvedTheme, toggleTheme }}>
      <LayoutContext.Provider value={{ layoutMode, toggleLayoutMode }}>
        <MenuConfigContext.Provider value={{ menuConfig, updateMenuConfig }}>
          <div className={`app-container ${resolvedTheme}`}>
            <Router>
              {!user ? (
                <LoginPage 
                  onLogin={handleLogin} 
                  requirePasswordChange={requirePasswordChange}
                  onPasswordChange={(_newPassword: string) => {
                    message.success('密码修改成功，请重新登录');
                    setRequirePasswordChange(false);
                  }}
                />
              ) : (
                <Layout style={{ minHeight: '100vh' }}>
                  {layoutMode !== 'top' && (
                    <Sider 
                      collapsible 
                      collapsed={collapsed} 
                      onCollapse={(value) => setCollapsed(value)}
                      theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
                    >
                      <div className="logo" />
                      <Menu 
            theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
            mode="inline" 
            defaultSelectedKeys={['dashboard']}
            items={enabledMenus.map(item => ({
              key: item.key,
              icon: item.icon,
              label: <Link to={item.path}>{item.label}</Link>
            }))}
          />
                    </Sider>
                  )}
                  <Layout className="site-layout">
                    <Header 
                      className={`site-layout-background ${resolvedTheme === 'dark' ? 'dark-header' : ''}`} 
                      style={{ padding: 0 }}
                    >
                      <div className="header-left">
                        {layoutMode === 'top' && (
                          <Menu 
                            theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
                            mode="horizontal" 
                            defaultSelectedKeys={['dashboard']}
                            style={{ lineHeight: '64px' }}
                            items={enabledMenus.map(item => ({
                              key: item.key,
                              icon: item.icon,
                              label: <Link to={item.path}>{item.label}</Link>
                            }))}
                          />
                        )}
                      </div>
                      <div className="header-right">
                        <Space size="middle">
                          <Tooltip title={`切换主题（当前：${themePreferenceLabel(theme)}）`}>
                            <Button 
                              type="text" 
                              icon={resolvedTheme === 'light' ? <MoonOutlined /> : <SunOutlined />}
                              onClick={toggleTheme}
                              className="header-button"
                            />
                          </Tooltip>
                          <Tooltip title="切换布局模式">
                            <Button 
                              type="text" 
                              icon={<LayoutOutlined />}
                              onClick={toggleLayoutMode}
                              className="header-button"
                            />
                          </Tooltip>
                          <MenuConfig />
                          <Dropdown menu={{ items: userMenu(handleLogout) }} placement="bottomRight">
            <Button type="text" className="user-button">
              <Avatar size="small" icon={<UserOutlined />} />
              <span className="user-name">{user?.name}</span>
            </Button>
          </Dropdown>
                        </Space>
                      </div>
                    </Header>
                    <Content
                      className={`site-layout-background ${resolvedTheme === 'dark' ? 'dark-content' : ''}`}
                      style={{
                        margin: '24px 16px',
                        padding: 24,
                        minHeight: 280,
                      }}
                    >
                      <Routes>
                        <Route path="/" element={<DashboardPage />} />
                        <Route path="/iam" element={<MicroAppContainer />} />
                        <Route path="/iam/*" element={<MicroAppContainer />} />
                        <Route path="/metadata" element={<MicroAppContainer />} />
                        <Route path="/metadata/*" element={<MicroAppContainer />} />
                        <Route path="/masterdata" element={<MicroAppContainer />} />
                        <Route path="/masterdata/*" element={<MicroAppContainer />} />
                        <Route path="/integration" element={<MicroAppContainer />} />
                        <Route path="/integration/*" element={<MicroAppContainer />} />
                        <Route path="/system" element={<MicroAppContainer />} />
                        <Route path="/system/*" element={<MicroAppContainer />} />
                        <Route path="/extension" element={<MicroAppContainer />} />
                        <Route path="/extension/*" element={<MicroAppContainer />} />
                        <Route path="/generator" element={<MicroAppContainer />} />
                        <Route path="/generator/*" element={<MicroAppContainer />} />
                        <Route path="*" element={<Navigate to="/" replace />} />
                      </Routes>
                    </Content>
                  </Layout>
                </Layout>
              )}
            </Router>
          </div>
        </MenuConfigContext.Provider>
      </LayoutContext.Provider>
    </ThemeContext.Provider>
    </BoneAppProvider>
  );
}

// 登录页面
function LoginPage({ onLogin, requirePasswordChange, onPasswordChange }: any) {
  const [form] = Form.useForm();
  const { resolvedTheme } = useContext(ThemeContext);

  // 设置默认值
  useEffect(() => {
    form.setFieldsValue({
      username: 'admin',
      password: ''
    });
  }, [form]);

  const handleSubmit = (values: any) => {
    onLogin(values);
  };

  if (requirePasswordChange) {
    return (
      <PasswordChangePage theme={resolvedTheme} onPasswordChange={onPasswordChange} />
    );
  }

  return (
    <div className={`login-container ${resolvedTheme}`}>
      <Card className="login-card" title="BONE 平台登录">
        <Form
          form={form}
          onFinish={handleSubmit}
          layout="vertical"
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Password prefix={<LockOutlined />} placeholder="请输入密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" className="login-button">
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

// 强制修改密码页面
function PasswordChangePage({ theme, onPasswordChange }: any) {
  const [form] = Form.useForm();

  const handleSubmit = (values: any) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('两次输入的密码不一致');
      return;
    }
    if (values.newPassword.length < 6) {
      message.error('密码长度至少6位');
      return;
    }
    onPasswordChange(values.newPassword);
  };

  return (
    <div className={`login-container ${theme}`}>
      <Card className="login-card" title="首次登录 - 修改默认密码">
        <p style={{ marginBottom: 16, color: '#ff4d4f' }}>
          为了账号安全，首次登录请修改默认密码
        </p>
        <Form
          form={form}
          onFinish={handleSubmit}
          layout="vertical"
        >
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, message: '请输入新密码' }]}
          >
            <Password prefix={<LockOutlined />} placeholder="请输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            rules={[{ required: true, message: '请确认新密码' }]}
          >
            <Password prefix={<LockOutlined />} placeholder="请确认新密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" className="login-button">
              确认修改
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

// 菜单配置组件
function MenuConfig() {
  const { menuConfig, updateMenuConfig } = useContext(MenuConfigContext);
  const { resolvedTheme } = useContext(ThemeContext);

  return (
    <Popover
      content={
        <div className={`menu-config ${resolvedTheme}`}>
          <h3>菜单配置</h3>
          {menuConfig.map(item => (
            <div key={item.key} className="menu-config-item">
              <span>{item.label}</span>
              <Switch
                checked={item.enabled}
                onChange={(checked) => updateMenuConfig(item.key, checked)}
              />
            </div>
          ))}
        </div>
      }
      title="菜单配置"
      trigger="click"
    >
      <Button type="text" icon={<SettingIcon />} className="header-button" />
    </Popover>
  );
}

// 用户菜单
function userMenu(onLogout: any) {
  return [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: onLogout,
    },
  ];
}


// 微应用容器
function MicroAppContainer() {
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    // 模拟微应用加载
    const timer = setTimeout(() => {
      setIsLoading(false);
      // 检查微应用是否加载成功
      const container = document.getElementById('micro-app-container');
      if (container && container.children.length === 0) {
        setError(true);
      }
    }, 2000);

    return () => clearTimeout(timer);
  }, []);

  return (
    <div style={{ position: 'relative', width: '100%', minHeight: '400px' }}>
      {/* 微应用容器 */}
      <div id="micro-app-container" style={{ width: '100%', height: '100%', minHeight: '400px' }} />
      
      {/* 加载状态 */}
      {isLoading && (
        <div style={{ 
          position: 'absolute', 
          top: 0, 
          left: 0, 
          right: 0, 
          bottom: 0, 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          backgroundColor: 'rgba(255, 255, 255, 0.8)',
          zIndex: 1000
        }}>
          <div style={{ textAlign: 'center' }}>
            <p>正在加载应用...</p>
          </div>
        </div>
      )}
      
      {/* 错误状态 */}
      {!isLoading && error && (
        <div style={{ 
          position: 'absolute', 
          top: 0, 
          left: 0, 
          right: 0, 
          bottom: 0, 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          backgroundColor: 'rgba(255, 255, 255, 0.9)',
          zIndex: 1000
        }}>
          <div style={{ textAlign: 'center' }}>
            <p style={{ fontSize: '18px', marginBottom: '16px' }}>应用未启动</p>
            <p style={{ color: '#666', marginBottom: '24px' }}>请启动对应的微应用后再访问此页面</p>
            <Button type="primary" onClick={() => window.location.reload()}>刷新页面</Button>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;