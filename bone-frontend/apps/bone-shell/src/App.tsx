import { useEffect, useState, useContext, useMemo, useCallback, Component, ReactNode } from 'react';
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { Layout, Menu, Button, Avatar, Dropdown, App as AntdApp, Form, Input, Card, Popover, Tooltip, Badge, Result, List, Tag, Spin, Empty, Select } from 'antd';
const { Password } = Input;
import axios from 'axios';
import { createApiClient, notificationService, type NotificationDTO } from '@bone/shared-services';
import type { MenuNode } from '@bone/shared-types';
import { registerMicroApps, start as startQiankun, addGlobalUncaughtErrorHandler } from 'qiankun';
import {
  UserOutlined, LogoutOutlined, DashboardOutlined,
  LockOutlined, DatabaseOutlined, LinkOutlined, SettingOutlined,
  SunOutlined, MoonOutlined, AppstoreOutlined, CodeOutlined,
  SafetyCertificateOutlined, AuditOutlined, TeamOutlined,
  FileTextOutlined, PartitionOutlined, ApiOutlined, ThunderboltOutlined,
  ClusterOutlined, OrderedListOutlined, ReconciliationOutlined,
  BranchesOutlined, ControlOutlined, HistoryOutlined,
  NodeIndexOutlined, NodeCollapseOutlined, UnorderedListOutlined,
  CoffeeOutlined, ProfileOutlined,
  LineChartOutlined, AlertOutlined, CloudOutlined, CloudServerOutlined,
  BellOutlined, ApartmentOutlined, MenuOutlined, ClockCircleOutlined,
  ShoppingOutlined, TransactionOutlined,
  DeploymentUnitOutlined, CloudUploadOutlined, InboxOutlined, CarOutlined,
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
import {
  currentLocale,
  i18n,
  LOCALE_STORAGE_KEY,
  type SupportedLanguage,
} from '@bone/shared-utils';
import './App.css';

const { Header, Sider, Content } = Layout;

import DashboardPage from './pages/DashboardPage';
import ProfilePage from './pages/Profile';
import {
  LayoutContext,
  ThemeContext,
  type ShellMenuItem,
} from './shellContext';
import Authorized from './auth/Authorized';
import { PermissionCodes, clearScopes, persistScopesFromToken, readScopes } from './auth/jwt';
import { PERMISSIONS_CHANGE_EVENT } from '@bone/shared-utils';

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
  onUnreadChange,
}: {
  onUnreadChange: () => void;
}): JSX.Element {
  const { message } = AntdApp.useApp();
  const [list, setList] = useState<NotificationDTO[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    notificationService
      .getMessages(30)
      .then((data) => alive && setList(data))
      .catch(() => alive && setList([]))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, []);

  const markOne = async (id: string) => {
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
  // locale 是**单源**：唯一 state 在 Shell，经 core-event-bus 广播给各微应用；
  // 微应用只读并被动继承，禁止自决语言（i18n 方案 §4.3）。
  const [locale, setLocale] = useState<SupportedLanguage>(() => currentLocale());
  // 主题同样是**单源**：唯一 state 在 App，向下双通道消费 ——
  // ① BoneAppProvider（AntD token + applyThemeCss 持久化）② AppContent（ThemeContext 下发）。
  // ⚠️ 此前硬编码 themeMode="system"：BoneAppProvider 渲染时经 toAntdTheme → applyThemeCss
  //    把 "system" 写回 localStorage，且父组件先于子组件渲染，导致 AppContent 的
  //    readStoredTheme() 永远读到被覆写的值 —— 用户主题偏好每次启动都被清掉（暗色模式失效根因）。
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme());

  return (
    <BoneAppProvider themeMode={theme} locale={locale}>
      <AntdApp>
        <AppContent
          locale={locale}
          onLocaleChange={setLocale}
          theme={theme}
          onThemeChange={setTheme}
        />
      </AntdApp>
    </BoneAppProvider>
  );
}

/**
 * 前端**渲染元数据**（不是可见性真源）。
 *
 * 改造前这里是一份 130 行的 `STATIC_MENU` 常量：11 个分组 / 约 60 个叶子，`enabled: true`
 * 全开、没有任何 permission 字段 ⇒ **任何租户、任何角色登录后看到的侧边栏一模一样**。
 * 可见性现在由 IAM 决定（`GET /api/v1/iam/menus/current` 按租户 + 角色权限码过滤后下发），
 * 这里只回答两件后端不该管、前端必须知道的事：
 *
 *   1. 某个 path 究竟落到哪个微应用容器（qiankun 容器路由的前缀集合）；
 *   2. 后端菜单里的图标名（antd 组件名字符串）该怎么渲染成 React 节点。
 *
 * 反过来说：**这里没有的菜单 = 平台不可达**，需要时在 IAM「菜单管理」里新增即可，
 * 不必再改前端代码、不必重新构建。
 */

/** 由各微前端容器 `<Route>` 提供的 pathname 前缀；一切菜单 path 必须落在其中。 */
const MICRO_APP_ROUTE_PREFIXES = [
  '/iam',
  '/metadata',
  '/masterdata',
  '/commerce',
  '/integration',
  '/system',
  '/extension',
  '/generator',
] as const;

/** path 形态为 `pathname` 或 `pathname#/hash`（微应用内是 HashRouter）。 */
function parseMenuPath(raw?: string | null): { path?: string; hash?: string } {
  if (!raw) {
    return {};
  }
  const [pathname, hash] = raw.split('#');
  if (!hash) {
    return { path: raw };
  }
  return { path: pathname, hash: hash.startsWith('/') ? hash : `/${hash}` };
}

function isKnownMenuPath(path?: string | null): boolean {
  if (!path) {
    return false;
  }
  if (path === '/' || path === '/profile') {
    return true;
  }
  return MICRO_APP_ROUTE_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}#`));
}

/**
 * 图标名 → antd 图标组件。
 *
 * 后端 `iam_menu.icon` 存的是**组件名字符串**（而非 SVG / iconfont 编码）：数据库里可读，
 * 前端改版也不用碰数据。
 * 这里做白名单映射而不是 `require('@ant-design/icons')[name]` 动态取——动态取会把整个图标
 * 包打进产物（体积翻倍）且逃过类型检查，写错名字要到运行时才发现。
 */
const MENU_ICON_MAP: Record<string, JSX.Element> = {
  DashboardOutlined: <DashboardOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  UserOutlined: <UserOutlined />,
  SafetyCertificateOutlined: <SafetyCertificateOutlined />,
  TeamOutlined: <TeamOutlined />,
  MenuOutlined: <MenuOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  AuditOutlined: <AuditOutlined />,
  PartitionOutlined: <PartitionOutlined />,
  SettingOutlined: <SettingOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  ApiOutlined: <ApiOutlined />,
  BranchesOutlined: <BranchesOutlined />,
  ThunderboltOutlined: <ThunderboltOutlined />,
  ClusterOutlined: <ClusterOutlined />,
  OrderedListOutlined: <OrderedListOutlined />,
  ProfileOutlined: <ProfileOutlined />,
  FileTextOutlined: <FileTextOutlined />,
  UnorderedListOutlined: <UnorderedListOutlined />,
  ReconciliationOutlined: <ReconciliationOutlined />,
  AlertOutlined: <AlertOutlined />,
  ShoppingOutlined: <ShoppingOutlined />,
  TransactionOutlined: <TransactionOutlined />,
  DeploymentUnitOutlined: <DeploymentUnitOutlined />,
  CloudUploadOutlined: <CloudUploadOutlined />,
  InboxOutlined: <InboxOutlined />,
  CarOutlined: <CarOutlined />,
  LinkOutlined: <LinkOutlined />,
  NodeIndexOutlined: <NodeIndexOutlined />,
  ControlOutlined: <ControlOutlined />,
  LineChartOutlined: <LineChartOutlined />,
  NodeCollapseOutlined: <NodeCollapseOutlined />,
  CloudServerOutlined: <CloudServerOutlined />,
  CoffeeOutlined: <CoffeeOutlined />,
  CodeOutlined: <CodeOutlined />,
  HistoryOutlined: <HistoryOutlined />,
  CloudOutlined: <CloudOutlined />,
  ClockCircleOutlined: <ClockCircleOutlined />,
};

/** 默认图标：后端没配 icon（大量历史菜单为 NULL）时的占位，避免侧边栏出现空白。 */
const DEFAULT_MENU_ICON = <AppstoreOutlined />;

function resolveMenuIcon(icon?: string | null): JSX.Element {
  return (icon && MENU_ICON_MAP[icon]) || DEFAULT_MENU_ICON;
}

/** 后端节点类型；未下发时按命名保守推断为菜单。 */
const MENU_NODE_TYPE = { GROUP: 0, MENU: 1, BUTTON: 2 } as const;

/**
 * 把后端菜单树转换为 Shell 菜单结构。
 *
 * 与旧实现的两处**实质性**差异：
 *  - 旧实现按 **label 文本**去静态表里借路由（`findStaticByLabel`），label 一改就全部
 *    对不上 ⇒ 菜单点击跳错地方。现在路由**只**来自后端 `path`。
 *  - 旧实现无脑保留所有节点。现在 type=2 的**按钮权限点**不入导航树，而是收集成 action
 *    清单下发给微应用（见 `actionCodes`），并剪掉「权限过滤后已空掉」的分组
 *    ——否则用户会看到一个没有任何子项的空分组。
 */
function buildMenuFromNodes(
  nodes: MenuNode[],
  actions: Set<string> = new Set<string>(),
): ShellMenuItem[] {
  const items: ShellMenuItem[] = [];
  for (const node of nodes) {
    const type = node.type ?? MENU_NODE_TYPE.MENU;
    const permission = node.permission ?? undefined;

    // 按钮权限点：登记到 action 清单，不进导航树
    if (type === MENU_NODE_TYPE.BUTTON) {
      if (permission) {
        actions.add(permission);
      }
      continue;
    }

    const children = buildMenuFromNodes(node.children ?? [], actions);
    const isLeaf = children.length === 0;
    const { path, hash } = parseMenuPath(node.path);

    // 叶子在这之前已被后端按权限码过滤；前端再拦一次未知路由，避免跳到 `<Route path="*">`
    // 被静默重定向回首页（用户点击无反应最难排查）。
    if (isLeaf && !isKnownMenuPath(path)) {
      continue;
    }
    // 目录/分组若无可见子项则整体剪掉——否则用户会看到一个点不开的空分组
    if (!isLeaf || isKnownMenuPath(path)) {
      items.push({
        key: node.id,
        label: node.name,
        icon: resolveMenuIcon(node.icon),
        path,
        hash,
        permission,
        type,
        children: isLeaf ? undefined : children,
      });
    }
  }
  return items;
}

function AppContent({
  locale,
  onLocaleChange,
  theme,
  onThemeChange,
}: {
  locale: SupportedLanguage;
  onLocaleChange: (next: SupportedLanguage) => void;
  /** 主题单源在 App（经 BoneAppProvider 持久化），AppContent 只消费 + 上报变更 */
  theme: Theme;
  onThemeChange: (next: Theme) => void;
}): JSX.Element {
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
  // 主题 state 已提升至 App（见 App 注释）；保留 setTheme 别名以复用既有切换逻辑
  const setTheme = onThemeChange;
  const resolvedTheme = resolveThemeMode(theme);
  const [layoutMode, setLayoutMode] = useState<'side' | 'top' | 'mix'>('side');

  const [menuConfig, setMenuConfig] = useState<ShellMenuItem[]>([]);
  const [menuLoading, setMenuLoading] = useState(false);
  const [menuError, setMenuError] = useState<string | null>(null);
  /**
   * 菜单里登记的按钮权限点（type=2 节点的 permission 集合）。
   * 随菜单一起下发 => 租户管理员可以在 IAM「菜单管理」里直接增删某个页面的按钮。
   */
  const [actionCodes, setActionCodes] = useState<string[]>([]);
  /** 当前登录账号的角色 code（新增 GET /api/v1/iam/me/roles）。 */
  const [roleCodes, setRoleCodes] = useState<string[]>([]);
  /** 平台管理员代操作时选定的租户（仅 JWT tenantId=0 且持 iam:tenants:read 可用）。 */
  const [actingTenantId, setActingTenantId] = useState<string | null>(null);
  const [tenantOptions, setTenantOptions] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    applyTheme(theme);
    // 仅初始化一次：随后的 theme 变化由下方 effect 处理
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 动态菜单：按**当前租户**向 IAM 拉取菜单树（后端已完成「租户 + 角色权限码」双重过滤）。
   *
   * ⚠️ 两处不可回退的写法，都是此前「动态菜单从未生效」的根因：
   *  ① **必须自己解信封**：`createApiClient` 的响应拦截器返回的是 `ApiResponse` 本体
   *     （不是里面的 data），`await api.get(...)` 拿到的是 `{code,message,data}` 对象。
   *     旧代码写 `api.get<never, MenuNode[]>()` 再 `Array.isArray(nodes)` —— 恒为 false，
   *     于是每一次都静默回退到写死的静态菜单。
   *  ② **失败不许回退全量菜单**：空数组要显示「暂无可见菜单」，而不是偷偷把
   *     STATIC_MENU 铺出来。回退会让「租户隔离 / 角色授权」在最关键的失败时刻失效，
   *     表现却是"一切正常"，最难揪。
   */
  useEffect(() => {
    if (!user) return;
    let alive = true;
    setMenuLoading(true);
    setMenuError(null);

    const api = createApiClient('/api/v1/iam');
    const params = actingTenantId != null ? { tenantId: String(actingTenantId) } : undefined;

    api
      .get<never, { code?: number; data?: MenuNode[] }>('/menus/current', { params })
      .then((envelope) => {
        if (!alive) return;
        const nodes = envelope?.data;
        if (!Array.isArray(nodes)) {
          // 后端未就绪/返回空：同样不许回退静态菜单
          setMenuConfig([]);
          setActionCodes([]);
          return;
        }
        const actions = new Set<string>();
        const items = buildMenuFromNodes(nodes, actions);
        setMenuConfig(items);
        setActionCodes([...actions]);
      })
      .catch((e: unknown) => {
        if (!alive) return;
        const err = e as { displayMessage?: string };
        setMenuError(err?.displayMessage ?? '菜单加载失败，请联系管理员');
        setMenuConfig([]);
        setActionCodes([]);
      })
      .finally(() => {
        if (alive) setMenuLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [user, actingTenantId]);

  /** 我的角色（自助端点，无额外权限码要求）。 */
  useEffect(() => {
    if (!user) return;
    let alive = true;
    const api = createApiClient('/api/v1/iam');
    api
      .get<never, { data?: { code?: string }[] }>('/me/roles')
      .then((envelope) => {
        if (!alive) return;
        const rows = envelope?.data ?? [];
        setRoleCodes(rows.map((r) => r?.code).filter((c): c is string => Boolean(c)));
      })
      .catch(() => {
        if (alive) setRoleCodes([]);
      });
    return () => {
      alive = false;
    };
  }, [user]);

  /**
   * 平台管理员的「代租户视角」候选列表。
   *
   * 只有 JWT tenantId=0 且持有 `iam:tenants:read` 才拉——普通租户成员看到租户列表本身
   * 就是越权信息面；后端对 `/api/v1/iam/tenants` 也有同样的 @PreAuthorize。
   */
  const canSwitchTenant = String(user?.tenantId ?? 0) === '0' && readScopes().includes('iam:tenants:read');
  useEffect(() => {
    if (!user || !canSwitchTenant) return;
    let alive = true;
    const api = createApiClient('/api/v1/iam');
    api
      .get<never, { data?: { records?: { id?: string; name?: string }[] } }>('/tenants', {
        params: { page: '1', size: '100' },
      })
      .then((envelope) => {
        if (!alive) return;
        const records = envelope?.data?.records ?? [];
        setTenantOptions(
          records
            .map((r) => ({ id: r?.id != null ? String(r?.id) : '', name: r?.name ?? (r?.id ?? '') }))
            .filter((t) => t.id !== ''),
        );
      })
      .catch(() => {
        if (alive) setTenantOptions([]);
      });
    return () => {
      alive = false;
    };
  }, [user, canSwitchTenant]);

  /**
   * 语言切换：**不刷新页面**的热更新。
   *
   * 顺序固定：① 改 Shell 自身 state（AntD locale + 壳层文案重渲染）
   * → ② 写 localStorage（**唯一写入方**；i18next 的 LanguageDetector 是 `caches: []` 只读）
   * → ③ 切本应用 i18next 实例 → ④ 经事件总线广播，各微应用自行 changeLanguage。
   */
  const handleLocaleChange = (next: SupportedLanguage) => {
    onLocaleChange(next);
    localStorage.setItem(LOCALE_STORAGE_KEY, next);
    void i18n.changeLanguage(next);
    globalEventBus.emit('bone:theme:change', { theme, locale: next });
    // 同步 window 上的全局上下文，供未订阅事件、直接读 window 的代码保持一致
    const ctx = (window as unknown as { __BONE_GLOBAL_CONTEXT__?: Record<string, unknown> })
      .__BONE_GLOBAL_CONTEXT__;
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = {
      ...(ctx ?? {}),
      locale: next,
    };
  };

  useEffect(() => {
    // 将全局事件总线挂到 window，供各微应用共享同一实例后订阅
    (window as unknown as Record<string, unknown>).__BONE_EVENT_BUS__ = globalEventBus;
    // 通过 core/event-bus 跨应用广播主题/语言变更
    globalEventBus.emit('bone:theme:change', { theme, locale });
  }, [theme, user, locale]);

  /**
   * 权限/租户快照：window 上下文的唯一组装处。
   *
   * `__BONE_GLOBAL_CONTEXT__` 是 window 上的**可变对象**，本身不触发 React 更新：
   * 只更新它，已挂载微应用里的 `<AuthButton>` 会停留在旧判定上（典型症状＝切租户后
   * 按钮该出现的不出现）。所以每次重算后必须补一次 `bone:permissions:change` 广播，
   * `usePermission()` 订阅后可重渲染。
   */
  const buildGlobalContext = useCallback(() => {
    const token = localStorage.getItem('token') || '';
    const scopedTenantId = actingTenantId ?? user?.tenantId ?? '0';
    return {
      token,
      /** ⚠ 顶层也必须放 tenantId：generator-app 是直接从这里读的，改动前它取不到。 */
      tenantId: scopedTenantId,
      user: user ? {
        id: user.id,
        username: user.username,
        realName: user.realName,
        avatarUrl: user.avatarUrl,
        tenantId: scopedTenantId,
        tenantName: user.tenantName,
        isAdmin: user.isAdmin ?? false,
      } : null,
      permissions: {
        codes: readScopes(),
        /** 角色：来自新增的 GET /api/v1/iam/me/roles（此前恒为空数组，角色信息根本没下发）。 */
        roles: roleCodes,
        /** 菜单里登记的按钮权限点（type=2 节点），租户管理员可在 IAM 菜单管理维护。 */
        actions: actionCodes,
        tenantId: scopedTenantId,
      },
      theme: resolveThemeMode(theme) === 'dark' ? 'dark' : 'light',
      locale,
    };
  }, [user, roleCodes, actionCodes, actingTenantId, theme, locale]);

  // 登录后始终保持 window 上下文为最新（qiankun 只在首次注册时下发 props，故此处专门同步）
  useEffect(() => {
    if (!user) return;
    (window as unknown as Record<string, unknown>).__BONE_GLOBAL_CONTEXT__ = buildGlobalContext();
    window.dispatchEvent(new CustomEvent(PERMISSIONS_CHANGE_EVENT));
  }, [user, buildGlobalContext]);

  // 初始化 qiankun 微应用（登录后执行，仅注册一次）
  useEffect(() => {
    if (!user) return;

    const globalContext = buildGlobalContext();

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
        name: 'bone-commerce-app',
        entry: import.meta.env.VITE_COMMERCE_APP_ENTRY || '//localhost:3012',
        container: '#subapp-viewport',
        activeRule: '/commerce',
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
      // ⚠️ 必须是 false（按 activeRule 匹配时才加载），不能是 'all'。
      // `#subapp-viewport` 只在微应用路由（/iam/*、/system/* …）被渲染后才存在于 DOM，
      // 登录后默认停在 `/`（仪表盘）——此刻容器尚未挂载。qiankun 的 prefetch 在 start()
      // 里同步发起，若为 'all' 会在容器出现前就加载全部微应用，八个应用全部抛
      // 「Target container with #subapp-viewport not existed while loading」并停在空白页。
      prefetch: false,
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

  // ⚠️ 原「本地菜单开关」（updateMenuConfig + filterEnabled(`enabled`)）已随动态菜单一并删除：
  // 菜单可见性现在是 IAM 的职责（租户 + 角色授权），壳层再留一套本地开关只会製造
  // 「用户在壳层关掉某菜单，刷新后又出现」的假控制感 —— 那是典型的假门禁，宁可没有。

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
        tenantId: account.tenantId ?? '0',
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
        <div className={`app-container ${resolvedTheme}`}>
          {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
                  消除每次启动必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
                  两者均为 v7 的向后兼容默认值，语义不变。 */}
          <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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
                menuLoading={menuLoading}
                menuError={menuError}
                canSwitchTenant={canSwitchTenant}
                tenantOptions={tenantOptions}
                actingTenantId={actingTenantId}
                onTenantChange={setActingTenantId}
                handleMenuClick={handleMenuClick}
                handleLogout={handleLogout}
                user={user}
                theme={theme}
                toggleTheme={toggleTheme}
                toggleLayoutMode={toggleLayoutMode}
                locale={locale}
                onLocaleChange={handleLocaleChange}
              />
            )}
          </Router>
        </div>
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
  /** 菜单首屏加载中：需要显式 loading，避免"空白侧边栏"被误判成"我没有任何权限"。 */
  menuLoading: boolean;
  /** 菜单拉取失败信息（非空时展示告警而非空菜单）。 */
  menuError: string | null;
  /** 是否允许切换租户视角（平台租户 + iam:tenants:read）。 */
  canSwitchTenant: boolean;
  tenantOptions: { id: string; name: string }[];
  actingTenantId: string | null;
  onTenantChange: (tenantId: string | null) => void;
  handleMenuClick: (info: { key: string }, navigate: (path: string) => void) => void;
  handleLogout: () => void;
  user: { id?: string; name: string } | null;
  theme: Theme;
  toggleTheme: () => void;
  toggleLayoutMode: () => void;
  locale: SupportedLanguage;
  onLocaleChange: (next: SupportedLanguage) => void;
}

function MainLayout(props: MainLayoutProps): JSX.Element {
  const {
    collapsed, setCollapsed, resolvedTheme, layoutMode,
    menuConfig, menuLoading, menuError, canSwitchTenant,
    tenantOptions, actingTenantId, onTenantChange,
    handleMenuClick,
    handleLogout, user, theme, toggleTheme,
    locale, onLocaleChange,
  } = props;
  const navigate = useNavigate();
  const location = useLocation();
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifOpen, setNotifOpen] = useState(false);
  const [notifSeq, setNotifSeq] = useState(0);

  // 点击铃铛标已读后刷新红点计数（归属由后端从 JWT 解析，前端不传 userId）
  const refreshUnread = () => {
    notificationService.getUnreadCount().then(setUnreadCount).catch(() => {});
  };

  // 拉取通知未读计数（对齐后端 /messages/unread-count，失败回退 0）
  useEffect(() => {
    if (!user?.id) return;
    let alive = true;
    const load = () =>
      notificationService
        .getUnreadCount()
        .then((n) => alive && setUnreadCount(n))
        .catch(() => alive && setUnreadCount(0));
    load();
    const timer = setInterval(load, 60_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [user]);

  /**
   * 菜单空态 / 加载态 / 错误态。
   *
   * 「空」必须有明确归因：拿到空数组可能是真的没授任何菜单，也可能是后端没配种子数据。
   * 直接渲染空侧边栏，用户只会认为"系统坏了"；给出原因才是可运维的。
   */
  const renderMenuBody = (): JSX.Element => {
    if (menuLoading && menuConfig.length === 0) {
      return (
        <div className="menu-placeholder">
          <Spin size="small" />
          <span>加载菜单…</span>
        </div>
      );
    }
    if (menuError) {
      return (
        <div className="menu-placeholder menu-placeholder-error">
          <AlertOutlined />
          <span>{menuError}</span>
        </div>
      );
    }
    if (menuConfig.length === 0) {
      return (
        <div className="menu-placeholder">
          <span>暂无可见菜单</span>
          <span className="menu-placeholder-hint">请联系管理员在 IAM 中分配菜单与角色</span>
        </div>
      );
    }
    return (
      <Menu
        theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
        mode="inline"
        selectedKeys={[selectedKey]}
        openKeys={openKeys}
        onOpenChange={onOpenChange}
        onClick={onMenuClick}
        items={menuItems}
      />
    );
  };

  // 基于当前 pathname + hash 计算 selectedKey 和 openKey
  const { selectedKey, parentKey } = useMemo(() => {
    const { pathname, hash } = location;
    const currentPath = pathname;
    // '#/audit-logs'.replace('#','') = '/audit-logs'（'#' 后本就带 '/'），直接与 child.hash 比较
    const currentHash = hash.replace('#', '');
    let sKey = 'dashboard';
    let pKey = '';

    if (currentPath === '/' || currentPath === '') {
      sKey = 'dashboard';
    } else {
      for (const group of menuConfig) {
        if (!group.children) continue;
        for (const child of group.children) {
          if (child.path === currentPath && child.hash === currentHash) {
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

  // 顶栏页面标题：随路由实时计算（叶子 = path+hash 双匹配；同 path 无 hash 的动态叶子回退第一个）
  const pageTitle = useMemo(() => {
    const { pathname, hash } = location;
    if (pathname === '/profile') return '个人中心';
    const currentHash = hash.replace('#', '');
    const walk = (items: ShellMenuItem[]): string => {
      let fallback = '';
      for (const item of items) {
        if (item.children && item.children.length > 0) {
          const found = walk(item.children);
          if (found) return found;
          continue;
        }
        if (item.path !== pathname) continue;
        if (item.hash) {
          if (item.hash === currentHash) return item.label;
        } else if (!fallback) {
          fallback = item.label;
        }
      }
      return fallback;
    };
    return walk(menuConfig);
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

  const menuItems = buildMenuItems(menuConfig);

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
          {renderMenuBody()}
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
                {pageTitle || 'BONE 平台控制台'}
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
                  <NotificationPanel key={notifSeq} onUnreadChange={refreshUnread} />
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
            {/* 语言切换：中/英一键切换，热更新不刷新页面（i18n 方案 §5.5.3） */}
            <Tooltip title={locale === 'en-US' ? 'Switch to Chinese' : '切换为英文'}>
              <Button
                type="text"
                className="header-button"
                onClick={() => onLocaleChange(locale === 'en-US' ? 'zh-CN' : 'en-US')}
              >
                {locale === 'en-US' ? 'EN' : '中'}
              </Button>
            </Tooltip>
            {canSwitchTenant && (
              <Tooltip title="切换租户视角：以目标租户的菜单与权限重新加载">
                <Select
                  size="small"
                  className="tenant-switcher"
                  placeholder="平台视角"
                  value={actingTenantId ?? undefined}
                  allowClear
                  clearIcon={<span />}
                  onClear={() => onTenantChange(null)}
                  onChange={(v) => onTenantChange(v == null ? null : String(v))}
                  popupMatchSelectWidth={false}
                  options={tenantOptions.map((t) => ({ value: t.id, label: t.name }))}
                />
              </Tooltip>
            )}
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
            {/* qiankun 微应用挂载容器：所有微应用路由都渲染此容器。
                ⚠ 不做 SYS_CONSOLE_READ 门禁——租户管理员只有 iam:* 作用域，包一层控制台权限码
                会把整个微前端挡成 403（容器不渲染 → qiankun 报 container not existed）。
                登录态由 App 级 user 判空保证；真实授权 = 后端 @PreAuthorize + 子应用内路由守卫。 */}
            <Route
              path="/iam/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/metadata/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/masterdata/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/commerce/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/integration/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/system/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/extension/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
            />
            <Route
              path="/generator/*"
              element={<MicroAppErrorBoundary><div id="subapp-viewport" /></MicroAppErrorBoundary>}
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