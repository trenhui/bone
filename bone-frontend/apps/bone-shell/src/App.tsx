import { useEffect, useState, useContext, useMemo, Component, ReactNode } from 'react';
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, Space, message, Form, Input, Card, Switch, Popover, Tooltip, Badge, Result } from 'antd';
const { Password } = Input;
import axios from 'axios';
import { registerMicroApps, start as startQiankun, addGlobalUncaughtErrorHandler } from 'qiankun';
import {
  UserOutlined, LogoutOutlined, DashboardOutlined, UserAddOutlined,
  LockOutlined, DatabaseOutlined, LinkOutlined, SettingOutlined,
  SunOutlined, MoonOutlined, AppstoreOutlined, CodeOutlined,
  LayoutOutlined, SettingOutlined as SettingIcon,
  SafetyCertificateOutlined, AuditOutlined, TeamOutlined,
  FileTextOutlined, PartitionOutlined, ApiOutlined, ThunderboltOutlined,
  ClusterOutlined, OrderedListOutlined, ReconciliationOutlined,
  BranchesOutlined, ControlOutlined, HistoryOutlined,
  NodeIndexOutlined, NodeCollapseOutlined, UnorderedListOutlined,
  CoffeeOutlined, ProfileOutlined,
  LineChartOutlined, AlertOutlined, CloudOutlined, CloudServerOutlined,
  BellOutlined,
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
import './App.css';

const { Header, Sider, Content } = Layout;

import DashboardPage from './pages/DashboardPage';
import {
  LayoutContext,
  MenuConfigContext,
  ThemeContext,
  type ShellMenuItem,
} from './shellContext';
import Authorized from './auth/Authorized';
import { PermissionCodes, clearScopes, persistScopesFromToken } from './auth/jwt';

function App(): JSX.Element {
  const [collapsed, setCollapsed] = useState(false);
  const [user, setUser] = useState(() => {
    // 从localStorage中读取用户信息
    const savedUser = localStorage.getItem('bone-user');
    if (savedUser) {
      // 刷新页面后恢复 JWT scopes，供 <Authorized> 路由守卫使用。
      persistScopesFromToken(localStorage.getItem('token'));
    }
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme());
  const resolvedTheme = resolveThemeMode(theme);
  const [layoutMode, setLayoutMode] = useState<'side' | 'top' | 'mix'>('side');
  const [currentPageTitle, setCurrentPageTitle] = useState<string>('');

  const [menuConfig, setMenuConfig] = useState<ShellMenuItem[]>([
    { key: 'dashboard', label: '首页仪表盘', icon: <DashboardOutlined />, path: '/', enabled: true },
    {
      key: 'iam',
      label: 'IAM 管理',
      icon: <UserAddOutlined />,
      enabled: true,
      children: [
        { key: 'iam-accounts', label: '用户管理', icon: <UserOutlined />, path: '/iam', hash: '/accounts', enabled: true },
        { key: 'iam-roles', label: '角色管理', icon: <TeamOutlined />, path: '/iam', hash: '/roles', enabled: true },
        { key: 'iam-permissions', label: '权限管理', icon: <SafetyCertificateOutlined />, path: '/iam', hash: '/permissions', enabled: true },
        { key: 'iam-audit', label: '审计日志', icon: <AuditOutlined />, path: '/iam', hash: '/audit-logs', enabled: true },
        { key: 'iam-tenants', label: '租户管理', icon: <PartitionOutlined />, path: '/iam', hash: '/tenants', enabled: true },
      ],
    },
    {
      key: 'metadata',
      label: '元数据管理',
      icon: <DatabaseOutlined />,
      enabled: true,
      children: [
        { key: 'metadata-entities', label: '实体管理', icon: <ApiOutlined />, path: '/metadata', hash: '/entities', enabled: true },
        { key: 'metadata-fields', label: '字段管理', icon: <OrderedListOutlined />, path: '/metadata', hash: '/fields', enabled: true },
        { key: 'metadata-relations', label: '关系管理', icon: <BranchesOutlined />, path: '/metadata', hash: '/relations', enabled: true },
        { key: 'metadata-runtime', label: '运行时数据', icon: <ThunderboltOutlined />, path: '/metadata', hash: '/runtime', enabled: true },
      ],
    },
    {
      key: 'masterdata',
      label: '主数据管理',
      icon: <ClusterOutlined />,
      enabled: true,
      children: [
        { key: 'masterdata-entities', label: '实体管理', icon: <ApiOutlined />, path: '/masterdata', hash: '/entities', enabled: true },
        { key: 'masterdata-fields', label: '字段管理', icon: <OrderedListOutlined />, path: '/masterdata', hash: '/fields', enabled: true },
        { key: 'masterdata-rules', label: '质量规则', icon: <ReconciliationOutlined />, path: '/masterdata', hash: '/rules', enabled: true },
        { key: 'masterdata-records', label: '记录管理', icon: <FileTextOutlined />, path: '/masterdata', hash: '/records', enabled: true },
      ],
    },
    {
      key: 'integration',
      label: '集成管理',
      icon: <LinkOutlined />,
      enabled: true,
      children: [
        { key: 'integration-connectors', label: '连接器管理', icon: <NodeIndexOutlined />, path: '/integration', hash: '/connectors', enabled: true },
        { key: 'integration-flows', label: '流程编排', icon: <ControlOutlined />, path: '/integration', hash: '/flows', enabled: true },
        { key: 'integration-monitor', label: '运行监控', icon: <LineChartOutlined />, path: '/integration', hash: '/monitor', enabled: true },
      ],
    },
    {
      key: 'extension',
      label: '扩展管理',
      icon: <AppstoreOutlined />,
      enabled: true,
      children: [
        { key: 'extension-points', label: '扩展点目录', icon: <NodeCollapseOutlined />, path: '/extension', hash: '/points', enabled: true },
        { key: 'extension-plugins', label: '插件仓库', icon: <UnorderedListOutlined />, path: '/extension', hash: '/plugins', enabled: true },
        { key: 'extension-deploy', label: '部署管理', icon: <CloudServerOutlined />, path: '/extension', hash: '/deploy', enabled: true },
        { key: 'extension-graph', label: '依赖图谱', icon: <BranchesOutlined />, path: '/extension', hash: '/graph', enabled: true },
        { key: 'extension-market', label: '低代码市场', icon: <CoffeeOutlined />, path: '/extension', hash: '/market', enabled: true },
        { key: 'extension-logs', label: '运行日志', icon: <ProfileOutlined />, path: '/extension', hash: '/logs', enabled: true },
      ],
    },
    {
      key: 'generator',
      label: '代码生成',
      icon: <CodeOutlined />,
      enabled: true,
      children: [
        { key: 'generator-datasources', label: '数据源管理', icon: <DatabaseOutlined />, path: '/generator', hash: '/datasources', enabled: true },
        { key: 'generator-generate', label: '代码生成', icon: <CodeOutlined />, path: '/generator', hash: '/generate', enabled: true },
        { key: 'generator-templates', label: '模板管理', icon: <FileTextOutlined />, path: '/generator', hash: '/templates', enabled: true },
        { key: 'generator-history', label: '生成历史', icon: <HistoryOutlined />, path: '/generator', hash: '/history', enabled: true },
      ],
    },
    {
      key: 'system',
      label: '系统管理',
      icon: <SettingOutlined />,
      enabled: true,
      children: [
        { key: 'system-config', label: '系统配置', icon: <ControlOutlined />, path: '/system', hash: '/config', enabled: true },
        { key: 'system-alerts', label: '监控告警', icon: <AlertOutlined />, path: '/system', hash: '/alerts', enabled: true },
        { key: 'system-logs', label: '日志管理', icon: <CloudOutlined />, path: '/system', hash: '/logs', enabled: true },
        { key: 'system-k8s', label: 'K8s 部署', icon: <CloudServerOutlined />, path: '/system', hash: '/k8s', enabled: true },
      ],
    },
  ]);

  useEffect(() => {
    const path = window.location.pathname;
    const hash = window.location.hash;
    
    const findTitle = (items: ShellMenuItem[]): string => {
      for (const item of items) {
        if (item.path === path) {
          if (hash && item.children) {
            const child = item.children.find(c => c.hash === hash);
            if (child) return `${item.label} - ${child.label}`;
          }
          return item.label;
        }
        if (item.children) {
          const found = findTitle(item.children);
          if (found) return found;
        }
      }
      return '';
    };
    
    const title = findTitle(menuConfig);
    setCurrentPageTitle(title);
  }, [menuConfig]);

  useEffect(() => {
    applyTheme(theme);
    // 仅初始化一次：随后的 theme 变化由下方 effect 处理
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    applyTheme(theme);
    publishThemeChange(theme);
  }, [theme, user]);

  // 初始化 qiankun 微应用（登录后执行，仅注册一次）
  useEffect(() => {
    if (!user) return;

    const token = localStorage.getItem('token') || '';

    const microApps = [
      {
        name: 'bone-iam-app',
        entry: import.meta.env.VITE_IAM_APP_ENTRY || '//localhost:3003',
        container: '#subapp-viewport',
        activeRule: '/iam',
        props: { token },
      },
      {
        name: 'bone-metadata-app',
        entry: import.meta.env.VITE_METADATA_APP_ENTRY || '//localhost:3004',
        container: '#subapp-viewport',
        activeRule: '/metadata',
        props: { token },
      },
      {
        name: 'bone-masterdata-app',
        entry: import.meta.env.VITE_MASTERDATA_APP_ENTRY || '//localhost:3005',
        container: '#subapp-viewport',
        activeRule: '/masterdata',
        props: { token },
      },
      {
        name: 'bone-integration-app',
        entry: import.meta.env.VITE_INTEGRATION_APP_ENTRY || '//localhost:3006',
        container: '#subapp-viewport',
        activeRule: '/integration',
        props: { token },
      },
      {
        name: 'bone-system-app',
        entry: import.meta.env.VITE_SYSTEM_APP_ENTRY || '//localhost:3007',
        container: '#subapp-viewport',
        activeRule: '/system',
        props: { token },
      },
      {
        name: 'bone-extension-app',
        entry: import.meta.env.VITE_EXTENSION_APP_ENTRY || '//localhost:3008',
        container: '#subapp-viewport',
        activeRule: '/extension',
        props: { token },
      },
      {
        name: 'bone-generator-app',
        entry: import.meta.env.VITE_GENERATOR_APP_ENTRY || '//localhost:3009',
        container: '#subapp-viewport',
        activeRule: '/generator',
        props: { token },
      },
    ];

    registerMicroApps(microApps);

    addGlobalUncaughtErrorHandler((event: Event | string) => {
      console.error('[qiankun] 微应用加载异常:', event);
    });

    startQiankun({
      prefetch: 'all',
      sandbox: { strictStyleIsolation: false, experimentalStyleIsolation: true },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

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

  // 递归更新菜单配置（支持子菜单）
  const updateMenuConfig = (key: string, enabled: boolean) => {
    const updateRecursive = (items: ShellMenuItem[]): ShellMenuItem[] =>
      items.map((item) => {
        if (item.key === key) {
          return { ...item, enabled };
        }
        if (item.children) {
          return { ...item, children: updateRecursive(item.children) };
        }
        return item;
      });
    setMenuConfig((prev) => updateRecursive(prev));
  };

  // 递归过滤启用的菜单项
  const filterEnabled = (items: ShellMenuItem[]): ShellMenuItem[] =>
    items
      .filter((item) => item.enabled)
      .map((item) =>
        item.children ? { ...item, children: filterEnabled(item.children) } : item,
      );

  // 登录处理
  const [requirePasswordChange, setRequirePasswordChange] = useState(false);

  const handleLogin = async (values: { username: string; password: string }): Promise<void> => {
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
        // 解析 JWT scopes 落地缓存，供 <Authorized> 路由守卫使用（详设 §5.0）。
        persistScopesFromToken(token);
      }
      localStorage.setItem('username', username);

      const userInfo = { name: username, forceChangePassword: false };
      setUser(userInfo);
      localStorage.setItem('bone-user', JSON.stringify(userInfo));
      message.success('登录成功');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      message.error(err?.response?.data?.message || '登录失败，请检查用户名或密码');
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
      clearScopes();
      message.success('退出登录成功');
    }
  };

  // 处理叶子菜单点击的跳转逻辑
  const handleMenuClick = (info: { key: string }, navigate: (path: string) => void) => {
    const findItemByKey = (items: ShellMenuItem[]): ShellMenuItem | undefined => {
      for (const item of items) {
        if (item.key === info.key) return item;
        if (item.children) {
          const found = findItemByKey(item.children);
          if (found) return found;
        }
      }
      return undefined;
    };
    const item = findItemByKey(menuConfig);
    if (item?.path) {
      // 使用字符串形式导航，确保 pathname 和 hash 正确设置
      // item.hash 格式为 "/accounts"，拼接为 "/iam#/accounts"
      const fullPath = item.hash ? `${item.path}#${item.hash}` : item.path;
      navigate(fullPath);
    }
  };

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
                    onPasswordChange={() => {
                      message.success('密码修改成功，请重新登录');
                      setRequirePasswordChange(false);
                    }}
                  />
                ) : (
                  <MainLayout
                    collapsed={collapsed}
                    setCollapsed={setCollapsed}
                    resolvedTheme={resolvedTheme}
                    layoutMode={layoutMode}
                    menuConfig={menuConfig}
                    filterEnabled={filterEnabled}
                    handleMenuClick={handleMenuClick}
                    handleLogout={handleLogout}
                    user={user}
                    theme={theme}
                    toggleTheme={toggleTheme}
                    toggleLayoutMode={toggleLayoutMode}
                    currentPageTitle={currentPageTitle}
                  />
                )}
              </Router>
            </div>
          </MenuConfigContext.Provider>
        </LayoutContext.Provider>
      </ThemeContext.Provider>
    </BoneAppProvider>
  );
}

// 主布局组件（放在 Router 内部以便使用 useNavigate/useLocation）
interface MainLayoutProps {
  collapsed: boolean;
  setCollapsed: (value: boolean) => void;
  resolvedTheme: 'light' | 'dark';
  layoutMode: 'side' | 'top' | 'mix';
  menuConfig: ShellMenuItem[];
  filterEnabled: (items: ShellMenuItem[]) => ShellMenuItem[];
  handleMenuClick: (info: { key: string }, navigate: (path: string) => void) => void;
  handleLogout: () => void;
  user: { name: string } | null;
  theme: Theme;
  toggleTheme: () => void;
  toggleLayoutMode: () => void;
  currentPageTitle: string;
}

function MainLayout(props: MainLayoutProps): JSX.Element {
  const {
    collapsed, setCollapsed, resolvedTheme, layoutMode,
    menuConfig, filterEnabled, handleMenuClick,
    handleLogout, user, theme, toggleTheme, toggleLayoutMode,
    currentPageTitle,
  } = props;
  const navigate = useNavigate();
  const location = useLocation();
  const [openKeys, setOpenKeys] = useState<string[]>([]);

  const enabledMenus = useMemo(() => filterEnabled(menuConfig), [menuConfig, filterEnabled]);

  // 基于当前 pathname + hash 计算 selectedKey 和 openKey
  const { selectedKey, parentKey } = useMemo(() => {
    const { pathname, hash } = location;
    const currentPath = pathname;
    const currentHash = hash.replace('#', '');
    let sKey = 'dashboard';
    let pKey = '';

    if (currentPath === '/' || currentPath === '') {
      sKey = 'dashboard';
    } else {
      for (const group of menuConfig) {
        if (!group.children) continue;
        for (const child of group.children) {
          if (child.path === currentPath && child.hash === `/${currentHash}`) {
            sKey = child.key;
            pKey = group.key;
            return { selectedKey: sKey, parentKey: pKey };
          }
        }
        // 未精确匹配 hash 时，回退到该分组的第一个子菜单
        const firstChild = group.children.find(c => c.path === currentPath);
        if (firstChild) {
          sKey = firstChild.key;
          pKey = group.key;
        }
      }
    }
    return { selectedKey: sKey, parentKey: pKey };
  }, [location, menuConfig]);

  // 初始化展开父分组
  useEffect(() => {
    if (parentKey && !openKeys.includes(parentKey)) {
      setOpenKeys([parentKey]);
    }
    if (!parentKey) {
      setOpenKeys([]);
    }
  }, [parentKey]);

  // 将 ShellMenuItem[] 转换为 antd Menu items
  const buildMenuItems = (items: ShellMenuItem[]): any[] =>
    items.map((item) => {
      if (item.children && item.children.length > 0) {
        return {
          key: item.key,
          icon: item.icon,
          label: item.label,
          children: buildMenuItems(item.children),
        };
      }
      return {
        key: item.key,
        icon: item.icon,
        label: item.label,
      };
    });

  const menuItems = buildMenuItems(enabledMenus);

  const onMenuClick = (info: { key: string }) => {
    handleMenuClick(info, navigate);
  };

  const onOpenChange = (keys: string[]) => {
    // 手风琴效果：一次只展开一个分组
    const newOpenKeys = keys.filter(k => !openKeys.includes(k));
    if (newOpenKeys.length > 0) {
      setOpenKeys(newOpenKeys);
    } else {
      setOpenKeys(keys);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {layoutMode !== 'top' && (
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
        >
          <div className="logo">
            <div className="logo-icon">B</div>
            {!collapsed && (
              <div className="logo-text">
                <span className="main">Bone Admin</span>
                <span className="sub">企业级快速开发平台</span>
              </div>
            )}
          </div>
          <Menu
            theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
            mode="inline"
            selectedKeys={[selectedKey]}
            openKeys={openKeys}
            onOpenChange={onOpenChange}
            onClick={onMenuClick}
            items={menuItems}
          />
        </Sider>
      )}
      <Layout className="site-layout">
        <Header
          className={`site-layout-background ${resolvedTheme === 'dark' ? 'dark-header' : ''}`}
          style={{ padding: 0, height: '56px', lineHeight: '56px' }}
        >
          <div className="header-left">
            {layoutMode === 'top' ? (
              <Menu
                theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
                mode="horizontal"
                selectedKeys={[selectedKey]}
                openKeys={openKeys}
                onOpenChange={onOpenChange}
                onClick={onMenuClick}
                style={{ lineHeight: '56px' }}
                items={menuItems}
              />
            ) : (
              <div className="page-title">
                {currentPageTitle || 'BONE 平台控制台'}
              </div>
            )}
          </div>
          <div className="header-right">
            <Tooltip title="通知 (3)">
              <Button type="text" className="header-button notification-btn">
                <Badge count={3} size="small">
                  <BellOutlined style={{ fontSize: 15 }} />
                </Badge>
              </Button>
            </Tooltip>
            <Tooltip title={`切换主题（当前：${themePreferenceLabel(theme)}）`}>
              <Button
                type="text"
                icon={resolvedTheme === 'light' ? <MoonOutlined /> : <SunOutlined />}
                onClick={toggleTheme}
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
            <Route
              path="/"
              element={
                <Authorized required={PermissionCodes.SYS_CONSOLE_READ}>
                  <DashboardPage />
                </Authorized>
              }
            />
            {/* qiankun 微应用挂载容器：所有微应用路由都渲染此容器 */}
            <Route path="/iam/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/metadata/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/masterdata/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/integration/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/system/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/extension/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="/generator/*" element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

interface LoginPageProps {
  onLogin: (values: { username: string; password: string }) => void | Promise<void>;
  requirePasswordChange: boolean;
  onPasswordChange: (newPassword: string) => void;
}

function LoginPage({ onLogin, requirePasswordChange, onPasswordChange }: LoginPageProps): JSX.Element {
  const [form] = Form.useForm();
  const { resolvedTheme } = useContext(ThemeContext);

  useEffect(() => {
    form.setFieldsValue({
      username: '',
      password: ''
    });
  }, [form]);

  const handleSubmit = (values: { username: string; password: string }): void => {
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

interface PasswordChangePageProps {
  theme: 'light' | 'dark';
  onPasswordChange: (newPassword: string) => void;
}

function PasswordChangePage({ theme, onPasswordChange }: PasswordChangePageProps): JSX.Element {
  const [form] = Form.useForm();

  const handleSubmit = (values: { newPassword: string; confirmPassword: string }): void => {
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

function MenuConfig(): JSX.Element {
  const { menuConfig, updateMenuConfig } = useContext(MenuConfigContext);
  const { resolvedTheme } = useContext(ThemeContext);

  const renderItem = (item: ShellMenuItem, level: number = 0): JSX.Element => (
    <div key={item.key}>
      <div
        className="menu-config-item"
        style={{ paddingLeft: `${level * 16}px` }}
      >
        <span style={{ fontWeight: item.children ? 600 : 400 }}>{item.label}</span>
        <Switch
          checked={item.enabled}
          onChange={(checked) => updateMenuConfig(item.key, checked)}
        />
      </div>
      {item.children && item.children.map(child => renderItem(child, level + 1))}
    </div>
  );

  return (
    <Popover
      content={
        <div className={`menu-config ${resolvedTheme}`}>
          <h3>菜单配置</h3>
          {menuConfig.map(item => renderItem(item))}
        </div>
      }
      title="菜单配置"
      trigger="click"
    >
      <Button type="text" icon={<SettingIcon />} className="header-button" />
    </Popover>
  );
}

function userMenu(onLogout: () => void): Array<{ key: string; icon: JSX.Element; label: string; onClick?: () => void }> {
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

/** 微应用加载错误边界，防止子应用异常导致整个 Shell 崩溃 */
interface MicroAppErrorBoundaryProps {
  children: ReactNode;
}

interface MicroAppErrorBoundaryState {
  hasError: boolean;
}

class MicroAppErrorBoundary extends Component<MicroAppErrorBoundaryProps, MicroAppErrorBoundaryState> {
  constructor(props: MicroAppErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): MicroAppErrorBoundaryState {
    return { hasError: true };
  }

  render() {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="微应用加载失败"
          subTitle="请检查微应用服务是否正常运行"
          extra={<Button type="primary" onClick={() => window.location.reload()}>刷新重试</Button>}
        />
      );
    }
    return this.props.children;
  }
}


export default App;