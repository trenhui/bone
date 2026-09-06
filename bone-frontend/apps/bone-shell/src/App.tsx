import { useEffect, useState, useContext, useMemo, Component, ReactNode } from 'react';
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, App as AntdApp, Form, Input, Card, Switch, Popover, Tooltip, Badge, Result, List, Tag, Spin, Empty } from 'antd';
const { Password } = Input;
import axios from 'axios';
import { createApiClient, notificationService, type NotificationDTO } from '@bone/shared-services';
import type { MenuNode } from '@bone/shared-types';
import { registerMicroApps, start as startQiankun, addGlobalUncaughtErrorHandler } from 'qiankun';
import {
  UserOutlined, LogoutOutlined, DashboardOutlined, UserAddOutlined,
  LockOutlined, DatabaseOutlined, LinkOutlined, SettingOutlined,
  SunOutlined, MoonOutlined, AppstoreOutlined, CodeOutlined,
  SettingOutlined as SettingIcon,
  SafetyCertificateOutlined, AuditOutlined, TeamOutlined,
  FileTextOutlined, PartitionOutlined, ApiOutlined, ThunderboltOutlined,
  ClusterOutlined, OrderedListOutlined, ReconciliationOutlined,
  BranchesOutlined, ControlOutlined, HistoryOutlined,
  NodeIndexOutlined, NodeCollapseOutlined, UnorderedListOutlined,
  CoffeeOutlined, ProfileOutlined,
  LineChartOutlined, AlertOutlined, CloudOutlined, CloudServerOutlined,
  BellOutlined, ApartmentOutlined, MenuOutlined, ClockCircleOutlined,
} from '@ant-design/icons';
import {
  applyTheme,
  BoneAppProvider,
  readStoredTheme,
  resolveThemeMode,
  themePreferenceLabel,
  type Theme,
} from '@bone/ui';
import { globalEventBus } from '@bone/core-event-bus';
import './App.css';

const { Header, Sider, Content } = Layout;

import DashboardPage from './pages/DashboardPage';
import ProfilePage from './pages/Profile';
import {
  LayoutContext,
  MenuConfigContext,
  ThemeContext,
  type ShellMenuItem,
} from './shellContext';
import Authorized from './auth/Authorized';
import { PermissionCodes, clearScopes, persistScopesFromToken, readScopes } from './auth/jwt';

/** 通知等级 → antd Tag 颜色 */
function levelColor(level?: string): string {
  switch ((level || '').toUpperCase()) {
    case 'ERROR':
      return 'red';
    case 'WARN':
    case 'WARNING':
      return 'orange';
    case 'INFO':
    default:
      return 'blue';
  }
}

/**
 * 通知面板：列出当前用户站内信，支持单条/全部标为已读。
 * 标记已读后通过 onUnreadChange 回调让宿主刷新红点计数。
 */
function NotificationPanel({
  userId,
  onUnreadChange,
}: {
  userId: number;
  onUnreadChange: () => void;
}): JSX.Element {
  const { message } = AntdApp.useApp();
  const [list, setList] = useState<NotificationDTO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    notificationService
      .getMessages(userId, 30)
      .then((data) => alive && setList(data))
      .catch(() => alive && setList([]))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [userId]);

  const markOne = async (id: number) => {
    try {
      await notificationService.markRead(id);
      setList((prev) => prev.map((m) => (m.id === id ? { ...m, read: true } : m)));
      onUnreadChange();
    } catch {
      message.error('标记已读失败');
    }
  };

  const markAll = async () => {
    const unread = list.filter((m) => !m.read);
    if (unread.length === 0) return;
    try {
      await Promise.all(unread.map((m) => notificationService.markRead(m.id)));
      setList((prev) => prev.map((m) => ({ ...m, read: true })));
      onUnreadChange();
    } catch {
      message.error('批量标记已读失败');
    }
  };

  const unreadNum = list.filter((m) => !m.read).length;

  return (
    <div style={{ width: 340 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8,
        }}
      >
        <span style={{ fontWeight: 600 }}>
          通知{unreadNum > 0 ? `（${unreadNum} 条未读）` : ''}
        </span>
        <Button type="link" size="small" disabled={unreadNum === 0} onClick={markAll}>
          全部已读
        </Button>
      </div>
      <Spin spinning={loading}>
        {list.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无通知"
            style={{ padding: '16px 0' }}
          />
        ) : (
          <div style={{ maxHeight: 360, overflowY: 'auto' }}>
            <List
              dataSource={list}
              split={false}
              renderItem={(m) => (
                <List.Item
                  style={{
                    cursor: m.read ? 'default' : 'pointer',
                    opacity: m.read ? 0.55 : 1,
                    padding: '10px 4px',
                    borderBottom: '1px solid rgba(0,0,0,0.06)',
                  }}
                  onClick={() => {
                    if (!m.read) markOne(m.id);
                  }}
                  actions={
                    m.read
                      ? []
                      : [
                          <Button
                            type="link"
                            size="small"
                            key="r"
                            onClick={(e) => {
                              e.stopPropagation();
                              markOne(m.id);
                            }}
                          >
                            标为已读
                          </Button>,
                        ]
                  }
                >
                  <List.Item.Meta
                    title={
                      <span>
                        {m.level && (
                          <Tag color={levelColor(m.level)} style={{ marginRight: 6 }}>
                            {m.level}
                          </Tag>
                        )}
                        {m.title || '通知'}
                      </span>
                    }
                    description={
                      <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.65)' }}>
                        {m.content || ''}
                      </div>
                    }
                  />
                </List.Item>
              )}
            />
          </div>
        )}
      </Spin>
    </div>
  );
}

function App(): JSX.Element {
  return (
    <BoneAppProvider themeMode="system">
      <AntdApp>
        <AppContent />
      </AntdApp>
    </BoneAppProvider>
  );
}

/**
 * 将后端 MenuNode[] 转换为 Shell 前端菜单结构。
 * 后端已按当前用户角色过滤；此处仅做渲染映射 + 兜底。
 */
function buildMenuFromNodes(nodes: MenuNode[]): ShellMenuItem[] {
  const iconMap: Record<string, JSX.Element> = {
    DashboardOutlined: <DashboardOutlined />,
    UserAddOutlined: <UserAddOutlined />,
    DatabaseOutlined: <DatabaseOutlined />,
    LinkOutlined: <LinkOutlined />,
    AppstoreOutlined: <AppstoreOutlined />,
    CodeOutlined: <CodeOutlined />,
    SettingOutlined: <SettingOutlined />,
  };
  return nodes.map((node) => ({
    key: node.id,
    label: node.name,
    icon: node.icon ? (iconMap[node.icon] ?? <AppstoreOutlined />) : undefined,
    path: node.path,
    enabled: true,
    children: node.children && node.children.length > 0
      ? buildMenuFromNodes(node.children)
      : undefined,
  }));
}

function AppContent(): JSX.Element {
  const { message: messageApi } = AntdApp.useApp();
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
        { key: 'iam-audit-settings', label: '审计设置', icon: <SettingOutlined />, path: '/iam', hash: '/audit-settings', enabled: true },
        { key: 'iam-tenants', label: '租户管理', icon: <PartitionOutlined />, path: '/iam', hash: '/tenants', enabled: true },
        { key: 'iam-organizations', label: '组织机构', icon: <ApartmentOutlined />, path: '/iam', hash: '/organizations', enabled: true },
        { key: 'iam-menus', label: '菜单管理', icon: <MenuOutlined />, path: '/iam', hash: '/menus', enabled: true },
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
        { key: 'masterdata-quality-results', label: '质量结果', icon: <AlertOutlined />, path: '/masterdata', hash: '/quality-results', enabled: true },
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
        { key: 'system-dict', label: '字典管理', icon: <OrderedListOutlined />, path: '/system', hash: '/dict', enabled: true },
        { key: 'system-schedule', label: '定时任务', icon: <ClockCircleOutlined />, path: '/system', hash: '/schedule', enabled: true },
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

  // 动态菜单：拉取 IAM 当前用户菜单树（后端已完成角色过滤），失败保留本地 fallback。
  // 依赖 iam-org-menu-baseline 提供 GET /api/v1/iam/menu/current；未就绪时静默回退。
  useEffect(() => {
    if (!user) return;
    const api = createApiClient('/api/v1/iam');
    api
      .get<never, MenuNode[]>('/menus/current')
      .then((nodes) => {
        if (Array.isArray(nodes) && nodes.length > 0) {
          setMenuConfig(buildMenuFromNodes(nodes));
        }
      })
      .catch(() => {
        // 后端未提供菜单接口时，保留静态 fallback（menuConfig 初始值）
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    // 将全局事件总线挂到 window，供各微应用共享同一实例后订阅
    (window as unknown as Record<string, unknown>).__BONE_EVENT_BUS__ = globalEventBus;
    // 通过 core/event-bus 跨应用广播主题/语言变更（接入示例）
    globalEventBus.emit('bone:theme:change', { theme, locale: 'zh-CN' });
  }, [theme, user]);

  // 初始化 qiankun 微应用（登录后执行，仅注册一次）
  useEffect(() => {
    if (!user) return;

    const token = localStorage.getItem('token') || '';

    // 构建全局上下文，通过 qiankun props 下发给各微应用
    const globalContext = {
      token,
      user: user ? {
        id: user.id,
        username: user.username,
        realName: user.realName,
        avatarUrl: user.avatarUrl,
        tenantId: user.tenantId ?? 0,
        tenantName: user.tenantName,
        isAdmin: user.isAdmin ?? false,
      } : null,
      permissions: readScopes() ? { codes: readScopes(), roles: [] } : null,
      theme: resolveThemeMode(theme) === 'dark' ? 'dark' : 'light',
      locale: 'zh-CN' as const,
    };

    // 同时写入 window，供未通过 props 接收的微应用读取
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = globalContext;

    const microApps = [
      {
        name: 'bone-iam-app',
        entry: import.meta.env.VITE_IAM_APP_ENTRY || '//localhost:3003',
        container: '#subapp-viewport',
        activeRule: '/iam',
        props: globalContext,
      },
      {
        name: 'bone-metadata-app',
        entry: import.meta.env.VITE_METADATA_APP_ENTRY || '//localhost:3004',
        container: '#subapp-viewport',
        activeRule: '/metadata',
        props: globalContext,
      },
      {
        name: 'bone-masterdata-app',
        entry: import.meta.env.VITE_MASTERDATA_APP_ENTRY || '//localhost:3005',
        container: '#subapp-viewport',
        activeRule: '/masterdata',
        props: globalContext,
      },
      {
        name: 'bone-integration-app',
        entry: import.meta.env.VITE_INTEGRATION_APP_ENTRY || '//localhost:3006',
        container: '#subapp-viewport',
        activeRule: '/integration',
        props: globalContext,
      },
      {
        name: 'bone-system-app',
        entry: import.meta.env.VITE_SYSTEM_APP_ENTRY || '//localhost:3007',
        container: '#subapp-viewport',
        activeRule: '/system',
        props: globalContext,
      },
      {
        name: 'bone-extension-app',
        entry: import.meta.env.VITE_EXTENSION_APP_ENTRY || '//localhost:3008',
        container: '#subapp-viewport',
        activeRule: '/extension',
        props: globalContext,
      },
      {
        name: 'bone-generator-app',
        entry: import.meta.env.VITE_GENERATOR_APP_ENTRY || '//localhost:3009',
        container: '#subapp-viewport',
        activeRule: '/generator',
        props: globalContext,
      },
    ];

    registerMicroApps(microApps);

    addGlobalUncaughtErrorHandler((event: Event | string) => {
      console.error('[qiankun] 微应用加载异常:', event);
    });

    startQiankun({
      prefetch: 'all',
      sandbox: {
        strictStyleIsolation: false,
        experimentalStyleIsolation: true,
        // 禁用 localStorage 代理，让微应用直接访问真实 localStorage
        // 这样微应用可以通过 localStorage.getItem('token') 读取 Shell 写入的 token
      },
    });

    // 监听微应用发出的认证过期事件，统一由 Shell 处理登出
    const handleAuthExpired = () => {
      setUser(null);
      localStorage.removeItem('bone-user');
      localStorage.removeItem('token');
      localStorage.removeItem('username');
      delete (window as unknown as Record<string, string>).__BONE_TOKEN__;
      clearScopes();
    };
    window.addEventListener('bone:auth:expired', handleAuthExpired);

    return () => {
      window.removeEventListener('bone:auth:expired', handleAuthExpired);
    };
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
        messageApi.error(resp?.data?.message || '登录失败');
        return;
      }
      const token = resp.data.data?.token;
      const username = resp.data.data?.account?.username || values.username;
      if (token) {
        localStorage.setItem('token', token);
        // 同时写入 window 全局变量，确保 qiankun 沙箱中的微应用也能读取
        (window as unknown as Record<string, string>).__BONE_TOKEN__ = token;
        // 解析 JWT scopes 落地缓存，供 <Authorized> 路由守卫使用（详设 §5.0）。
        persistScopesFromToken(token);
      }
      localStorage.setItem('username', username);

      const account = resp.data.data?.account || {};
      const userInfo = {
        id: account.id,
        username: account.username || username,
        realName: account.realName || username,
        avatarUrl: account.avatarUrl || null,
        tenantId: account.tenantId ?? 0,
        tenantName: account.tenantName || '',
        isAdmin: account.isAdmin ?? false,
        name: username,
        forceChangePassword: false,
      };
      setUser(userInfo);
      localStorage.setItem('bone-user', JSON.stringify(userInfo));
      messageApi.success('登录成功');
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      messageApi.error(err?.response?.data?.message || '登录失败，请检查用户名或密码');
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
      delete (window as unknown as Record<string, string>).__BONE_TOKEN__;
      clearScopes();
      messageApi.success('退出登录成功');
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
                      messageApi.success('密码修改成功，请重新登录');
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
  user: { id?: number; name: string } | null;
  theme: Theme;
  toggleTheme: () => void;
  toggleLayoutMode: () => void;
  currentPageTitle: string;
}

function MainLayout(props: MainLayoutProps): JSX.Element {
  const {
    collapsed, setCollapsed, resolvedTheme, layoutMode,
    menuConfig, filterEnabled, handleMenuClick,
    handleLogout, user, theme, toggleTheme,
    currentPageTitle,
  } = props;
  const navigate = useNavigate();
  const location = useLocation();
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifSeq, setNotifSeq] = useState(0);

  // 点击铃铛标已读后刷新红点计数
  const refreshUnread = () => {
    if (user?.id) {
      notificationService.getUnreadCount(user.id).then(setUnreadCount).catch(() => {});
    }
  };

  // 拉取通知未读计数（对齐后端 /messages/unread-count?userId=，失败回退 0）
  useEffect(() => {
    if (!user?.id) return;
    let alive = true;
    const load = () =>
      notificationService
        .getUnreadCount(user.id)
        .then((n) => alive && setUnreadCount(n))
        .catch(() => alive && setUnreadCount(0));
    load();
    const timer = setInterval(load, 60_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [user]);

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
            <Popover
              open={notifOpen}
              onOpenChange={(o) => {
                setNotifOpen(o);
                if (o) setNotifSeq((s) => s + 1);
              }}
              trigger="click"
              placement="bottomRight"
              arrow={{ pointAtCenter: true }}
              content={
                user?.id ? (
                  <NotificationPanel key={notifSeq} userId={user.id} onUnreadChange={refreshUnread} />
                ) : null
              }
            >
              <Button type="text" className="header-button notification-btn">
                <Badge count={unreadCount} size="small">
                  <BellOutlined style={{ fontSize: 15 }} />
                </Badge>
              </Button>
            </Popover>
            <Tooltip title={`切换主题（当前：${themePreferenceLabel(theme)}）`}>
              <Button
                type="text"
                icon={resolvedTheme === 'light' ? <MoonOutlined /> : <SunOutlined />}
                onClick={toggleTheme}
                className="header-button"
              />
            </Tooltip>
            <MenuConfig />
            <Dropdown menu={{ items: userMenu(handleLogout, () => navigate('/profile')) }} placement="bottomRight">
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
            <Route
              path="/profile"
              element={
                <Authorized required={PermissionCodes.SYS_CONSOLE_READ}>
                  <ProfilePage user={user} />
                </Authorized>
              }
            />
            {/* qiankun 微应用挂载容器：所有微应用路由都渲染此容器（前端登录态守卫，后端 @PreAuthorize 兜底） */}
            <Route
              path="/iam/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/metadata/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/masterdata/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/integration/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/system/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/extension/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
            <Route
              path="/generator/*"
              element={<Authorized required={PermissionCodes.SYS_CONSOLE_READ}><MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary></Authorized>}
            />
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
  const { message: messageApi } = AntdApp.useApp();
  const [form] = Form.useForm();

  const handleSubmit = (values: { newPassword: string; confirmPassword: string }): void => {
    if (values.newPassword !== values.confirmPassword) {
      messageApi.error('两次输入的密码不一致');
      return;
    }
    if (values.newPassword.length < 6) {
      messageApi.error('密码长度至少6位');
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

function userMenu(onLogout: () => void, onProfile: () => void): Array<{ key: string; icon: JSX.Element; label: string; onClick?: () => void }> {
  return [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: onProfile,
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