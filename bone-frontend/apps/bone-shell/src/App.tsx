import React, { useEffect, useState, createContext, useContext } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, Navigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, Space, message, Form, Input, Card, Select, Switch, Popover, Tooltip } from 'antd';
const { Password } = Input;
import {
  UserOutlined, LogoutOutlined, DashboardOutlined, UserAddOutlined, TeamOutlined, 
  LockOutlined, AuditOutlined, DatabaseOutlined, LinkOutlined, SettingOutlined, 
  SunOutlined, MoonOutlined, MenuOutlined, AppstoreOutlined, 
  LayoutOutlined, SettingOutlined as SettingIcon, PlusOutlined, MinusOutlined
} from '@ant-design/icons';
import { registerMicroApps, start } from 'qiankun';
import './App.css';

const { Header, Sider, Content } = Layout;
const { Option } = Select;

// 主题上下文
const ThemeContext = createContext({
  theme: 'light',
  toggleTheme: () => {}
});

// 布局上下文
const LayoutContext = createContext({
  layoutMode: 'side', // side, top, mix
  toggleLayoutMode: () => {}
});

// 菜单配置上下文
const MenuConfigContext = createContext({
  menuConfig: [],
  updateMenuConfig: () => {}
});

function App() {
  const [collapsed, setCollapsed] = useState(false);
  const [user, setUser] = useState(() => {
    // 从localStorage中读取用户信息
    const savedUser = localStorage.getItem('bone-user');
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [theme, setTheme] = useState(() => {
    // 从localStorage中读取主题设置
    const savedTheme = localStorage.getItem('bone-theme');
    return savedTheme || 'light';
  });
  const [layoutMode, setLayoutMode] = useState('side');
  const [menuConfig, setMenuConfig] = useState([
    { key: 'dashboard', label: '首页', icon: <DashboardOutlined />, path: '/', enabled: true },
    { key: 'iam', label: 'IAM管理', icon: <UserAddOutlined />, path: '/iam', enabled: true },
    { key: 'metadata', label: '元数据管理', icon: <DatabaseOutlined />, path: '/metadata', enabled: true },
    { key: 'masterdata', label: '主数据管理', icon: <DatabaseOutlined />, path: '/masterdata', enabled: true },
    { key: 'integration', label: '集成管理', icon: <LinkOutlined />, path: '/integration', enabled: true },
    { key: 'system', label: '系统管理', icon: <SettingOutlined />, path: '/system', enabled: true },
    { key: 'extension', label: '扩展管理', icon: <AppstoreOutlined />, path: '/extension', enabled: true },
  ]);

  useEffect(() => {
    // 启动 qiankun
    start({
      sandbox: {
        strictStyleIsolation: true,
        experimentalStyleIsolation: true
      },
      onError: (err) => {
        console.log('qiankun error:', err);
      },
      importEntry: {
        getTemplate: (tpl: string) => {
          // 移除所有包含React Refresh的脚本标签
          let processedTpl = tpl.replace(/<script[^>]*react-refresh[^>]*>.*?<\/script>/gis, '');
          // 移除所有 type="module" 的脚本标签
          processedTpl = processedTpl.replace(/<script[^>]*type="module"[^>]*>.*?<\/script>/gis, '');
          // 移除所有 vite 相关的脚本标签
          processedTpl = processedTpl.replace(/<script[^>]*vite[^>]*>.*?<\/script>/gis, '');
          // 确保所有外部脚本都有正确的类型
          return processedTpl;
        },
        getScriptValue: (scriptText: string) => {
          // 跳过所有包含React Refresh的脚本
          if (scriptText.includes('react-refresh') || 
              scriptText.includes('injectIntoGlobalHook') ||
              scriptText.includes('vite/client')) {
            return '';
          }
          return scriptText;
        }
      }
    });
  }, []);

  // 注册微应用
  useEffect(() => {
    // 只注册微应用，但不立即加载
    registerMicroApps([
      {
        name: 'bone-iam-app',
        entry: 'http://localhost:3003',
        container: '#micro-app-container',
        activeRule: '/iam',
        props: {
          name: 'bone-iam-app',
          user: user
        }
      },
      {
        name: 'bone-metadata-app',
        entry: 'http://localhost:3004',
        container: '#micro-app-container',
        activeRule: '/metadata',
        props: {
          name: 'bone-metadata-app',
          user: user
        }
      },
      {
        name: 'bone-masterdata-app',
        entry: 'http://localhost:3005',
        container: '#micro-app-container',
        activeRule: '/masterdata',
        props: {
          name: 'bone-masterdata-app',
          user: user
        }
      },
      {
        name: 'bone-integration-app',
        entry: 'http://localhost:3006',
        container: '#micro-app-container',
        activeRule: '/integration',
        props: {
          name: 'bone-integration-app',
          user: user
        }
      },
      {
        name: 'bone-system-app',
        entry: 'http://localhost:3007',
        container: '#micro-app-container',
        activeRule: '/system',
        props: {
          name: 'bone-system-app',
          user: user
        }
      },
      {
        name: 'bone-extension-app',
        entry: 'http://localhost:3008',
        container: '#micro-app-container',
        activeRule: '/extension',
        props: {
          name: 'bone-extension-app',
          user: user
        }
      }
    ]);
  }, [user]);

  // 切换主题
  const toggleTheme = () => {
    const newTheme = theme === 'light' ? 'dark' : 'light';
    setTheme(newTheme);
    // 保存主题设置到localStorage
    localStorage.setItem('bone-theme', newTheme);
  };

  // 切换布局模式
  const toggleLayoutMode = () => {
    const modes = ['side', 'top', 'mix'];
    const currentIndex = modes.indexOf(layoutMode);
    const nextIndex = (currentIndex + 1) % modes.length;
    setLayoutMode(modes[nextIndex]);
  };

  // 更新菜单配置
  const updateMenuConfig = (key: any, enabled: any) => {
    setMenuConfig(menuConfig.map(item => 
      item.key === key ? { ...item, enabled } : item
    ));
  };

  // 登录处理
  const [requirePasswordChange, setRequirePasswordChange] = useState(false);

  const handleLogin = (values: any) => {
    // 模拟登录（实际应调用后端API）
    // 安全要求：首次使用默认密码登录必须强制修改
    if (values.username === 'admin' && values.password === 'admin123') {
      message.warning('首次登录，请修改默认密码');
      setRequirePasswordChange(true);
      return;
    }
    if (values.username === 'admin') {
      message.success('登录成功');
      const userInfo = { name: values.username, forceChangePassword: false };
      setUser(userInfo);
      localStorage.setItem('bone-user', JSON.stringify(userInfo));
    } else {
      message.error('用户名或密码错误');
    }
  };

  // 退出登录
  const handleLogout = () => {
    message.success('退出登录成功');
    setUser(null);
    // 从localStorage中删除用户信息
    localStorage.removeItem('bone-user');
  };

  // 过滤启用的菜单
  const enabledMenus = menuConfig.filter(item => item.enabled);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <LayoutContext.Provider value={{ layoutMode, toggleLayoutMode }}>
        <MenuConfigContext.Provider value={{ menuConfig, updateMenuConfig }}>
          <div className={`app-container ${theme}`}>
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
                      theme={theme === 'dark' ? 'dark' : 'light'}
                    >
                      <div className="logo" />
                      <Menu 
            theme={theme === 'dark' ? 'dark' : 'light'}
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
                      className={`site-layout-background ${theme === 'dark' ? 'dark-header' : ''}`} 
                      style={{ padding: 0 }}
                    >
                      <div className="header-left">
                        {layoutMode === 'top' && (
                          <Menu 
                            theme={theme === 'dark' ? 'dark' : 'light'}
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
                          <Tooltip title="切换主题">
                            <Button 
                              type="text" 
                              icon={theme === 'light' ? <MoonOutlined /> : <SunOutlined />}
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
                      className={`site-layout-background ${theme === 'dark' ? 'dark-content' : ''}`}
                      style={{
                        margin: '24px 16px',
                        padding: 24,
                        minHeight: 280,
                      }}
                    >
                      <Routes>
                        <Route path="/" element={<Dashboard />} />
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
  );
}

// 登录页面
function LoginPage({ onLogin, requirePasswordChange, onPasswordChange }: any) {
  const [form] = Form.useForm();
  const { theme } = useContext(ThemeContext);

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
      <PasswordChangePage theme={theme} onPasswordChange={onPasswordChange} />
    );
  }

  return (
    <div className={`login-container ${theme}`}>
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
  const { theme } = useContext(ThemeContext);

  return (
    <Popover
      content={
        <div className={`menu-config ${theme}`}>
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

// 仪表盘
function Dashboard() {
  const { theme } = useContext(ThemeContext);
  const { layoutMode } = useContext(LayoutContext);

  // 模拟数据
  const stats = [
    { title: '用户数量', value: 1, icon: <UserOutlined />, color: '#1890ff' },
    { title: '实体数量', value: 0, icon: <DatabaseOutlined />, color: '#52c41a' },
    { title: '集成流程', value: 0, icon: <LinkOutlined />, color: '#faad14' },
    { title: '扩展插件', value: 0, icon: <AppstoreOutlined />, color: '#f5222d' },
  ];

  const recentActivities = [
    { time: '刚刚', action: '系统登录', user: 'admin', status: 'success' },
    { time: '10分钟前', action: '用户创建', user: 'admin', status: 'success' },
    { time: '30分钟前', action: '角色更新', user: 'admin', status: 'success' },
  ];

  const systemStatus = [
    { service: 'IAM服务', status: 'running', color: '#52c41a' },
    { service: '元数据服务', status: 'running', color: '#52c41a' },
    { service: '主数据服务', status: 'running', color: '#52c41a' },
    { service: '集成服务', status: 'running', color: '#52c41a' },
    { service: '系统服务', status: 'running', color: '#52c41a' },
    { service: '扩展服务', status: 'running', color: '#52c41a' },
  ];

  return (
    <div className={`dashboard ${theme}`}>
      {/* 页面标题 */}
      <div className="dashboard-header">
        <h1>BONE 平台控制台</h1>
        <p>欢迎回来，admin！</p>
        <div className="dashboard-info">
          <div className="info-item">
            <span>当前主题: </span>
            <span>{theme === 'light' ? '亮色' : '暗色'}</span>
          </div>
          <div className="info-item">
            <span>布局模式: </span>
            <span>
              {layoutMode === 'side' ? '侧边菜单' : 
               layoutMode === 'top' ? '顶部菜单' : '混合模式'}
            </span>
          </div>
        </div>
      </div>

      {/* 数据统计卡片 */}
      <div className="stats-section">
        <h2 className="section-title">系统概览</h2>
        <div className="stats-cards">
          {stats.map((stat, index) => (
            <div key={index} className="stat-card">
              <div className="stat-icon" style={{ backgroundColor: `${stat.color}20`, color: stat.color }}>
                {stat.icon}
              </div>
              <div className="stat-content">
                <div className="stat-value">{stat.value}</div>
                <div className="stat-title">{stat.title}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 系统状态和最近活动 */}
      <div className="dashboard-grid">
        {/* 系统状态 */}
        <div className="dashboard-section">
          <h2 className="section-title">服务状态</h2>
          <div className="status-list">
            {systemStatus.map((item, index) => (
              <div key={index} className="status-item">
                <span className="status-service">{item.service}</span>
                <span className="status-indicator" style={{ backgroundColor: item.color }}></span>
                <span className="status-text">{item.status === 'running' ? '运行中' : item.status}</span>
              </div>
            ))}
          </div>
        </div>

        {/* 最近活动 */}
        <div className="dashboard-section">
          <h2 className="section-title">最近活动</h2>
          <div className="activity-list">
            {recentActivities.map((activity, index) => (
              <div key={index} className="activity-item">
                <div className="activity-time">{activity.time}</div>
                <div className="activity-content">
                  <span className="activity-action">{activity.action}</span>
                  <span className="activity-user">by {activity.user}</span>
                </div>
                <div className={`activity-status ${activity.status}`}>
                  {activity.status === 'success' ? '成功' : '失败'}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 快捷操作 */}
      <div className="dashboard-section">
        <h2 className="section-title">快捷操作</h2>
        <div className="quick-actions">
          <Link to="/iam" className="quick-action-item">
            <div className="quick-action-icon">
              <UserAddOutlined />
            </div>
            <span>用户管理</span>
          </Link>
          <Link to="/metadata" className="quick-action-item">
            <div className="quick-action-icon">
              <DatabaseOutlined />
            </div>
            <span>实体管理</span>
          </Link>
          <Link to="/integration" className="quick-action-item">
            <div className="quick-action-icon">
              <LinkOutlined />
            </div>
            <span>流程编排</span>
          </Link>
          <Link to="/system" className="quick-action-item">
            <div className="quick-action-icon">
              <SettingOutlined />
            </div>
            <span>系统配置</span>
          </Link>
          <Link to="/extension" className="quick-action-item">
            <div className="quick-action-icon">
              <AppstoreOutlined />
            </div>
            <span>扩展管理</span>
          </Link>
          <Link to="/masterdata" className="quick-action-item">
            <div className="quick-action-icon">
              <DatabaseOutlined />
            </div>
            <span>主数据管理</span>
          </Link>
        </div>
      </div>
    </div>
  );
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