import { Suspense } from 'react';
import { BrowserRouter, HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { App as AntdApp, Button, Layout, Menu, Result, Spin, Tag, Typography } from 'antd';
import { Auth } from '@bone/ui';
import { CLAIM_DEFAULT_PATH, CLAIM_ROUTES } from './routes';
import { ErrorBoundary } from './components/ErrorBoundary';
import { CommandPalette } from './components/CommandPalette';
import { PERMISSIONS } from './constants/permissions';
import { STANDALONE_PATHS, isStandalonePath } from './workbench/sclaimRoutes';
import { Case360Page } from './pages/Case360Page';
import { PolicyManagementPage } from './pages/PolicyManagementPage';
import './App.css';

/**
 * synapClaim 智能理赔微应用外壳。
 *
 * 导航架构（2026-10-28 重构，业界通行的「单一导航」微前端形态）
 * ---------------------------------------------------------------
 * - **嵌入式（qiankun 内）**：不渲染任何侧边栏。全局导航由 shell 依据 IAM 菜单统一渲染，
 *   微应用只负责「hash 路由 → 页面」。此前的双 Sider 是同一批入口的第二份副本，
 *   名字还与 IAM 菜单不一致（证据上传/材料上传、申诉处理/申诉复议、暂停案件/挂起·恢复），
 *   必然漂移，用户看到的就是「两层菜单、子菜单还不一样」。
 * - **独立调试（`:3013` 直开，无 shell）**：给一条仅开发可见的导航，避免「只能看到默认页」。
 *   这条导航**不是产品导航**，不承载权限与可见性，故显式标注「独立调试」，
 *   且数据源同为 {@link CLAIM_ROUTES} —— 路由与名称仍然只有一份真源。
 */
/** hidden 路由不是"无需权限"，而是"不进导航但仍需授权"：对齐后端 @PreAuthorize。 */
const HIDDEN_ROUTE_REQUIRED: Record<string, string> = {
  '/ai-governance': PERMISSIONS.CLAIMS_READ,
  // 运营看板消费 GET /api/v1/claims/analytics/summary，后端 @PreAuthorize('claim:claims:read')。
  '/analytics': PERMISSIONS.CLAIMS_READ,
  '/blind-labels': PERMISSIONS.INSPECTION_WRITE,
};

function RouteDenied(): JSX.Element {
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="无访问权限"
      subTitle="该入口为受控治理页面，当前角色未被授权。前端只做入口收敛，接口仍由后端强制鉴权。"
      extra={<Button type="primary" onClick={() => navigate(CLAIM_DEFAULT_PATH)}>返回工作台</Button>}
    />
  );
}

/**
 * 路由级 chunk 加载兜底。
 *
 * <p>页面已按路由 `lazy` 拆分（见 routes.tsx），首屏只下载工作台 + 外壳。这里只占内容区，
 * 不遮挡 DevNav：用户切换菜单时仍能看到自己点到了哪里，而不是整页白屏。
 * 不加 `tip` 文字，避免 antd v5「Spin tip 仅 nest/fullscreen 有效」的运行时告警。
 */
function RouteLoading(): JSX.Element {
  return (
    <div style={{ padding: 48, textAlign: 'center' }} role="status" aria-live="polite">
      <Spin size="large" />
      <div style={{ marginTop: 12 }}>
        <Typography.Text type="secondary">页面加载中…</Typography.Text>
      </div>
    </div>
  );
}

function ClaimRoutes(): JSX.Element {
  return (
    <Routes>
      {CLAIM_ROUTES.map((r) => {
        const required = HIDDEN_ROUTE_REQUIRED[r.path];
        const element = required
          ? <Auth code={required} fallback={<RouteDenied />}>{r.element}</Auth>
          : r.element;
        return (
          <Route
            key={r.path}
            path={r.path}
            element={<Suspense fallback={<RouteLoading />}>{element}</Suspense>}
          />
        );
      })}
      <Route path="/" element={<Navigate to={CLAIM_DEFAULT_PATH} replace />} />
      {/* 未知 hash 回到默认落点，而不是空白：深链失效时用户仍有可操作界面 */}
      <Route path="*" element={<Navigate to={CLAIM_DEFAULT_PATH} replace />} />
    </Routes>
  );
}

/** 仅独立调试使用：极简导航（嵌入式下不渲染）。 */
function DevNav({ collapsed }: { collapsed?: boolean }): JSX.Element {
  const location = useLocation();
  const navigate = useNavigate();
  const activePath =
    CLAIM_ROUTES.map((r) => r.path).find((k) => location.pathname.startsWith(k)) ?? CLAIM_DEFAULT_PATH;
  // hidden 路由不占菜单项：/plan（保单配置下钻页）高亮归到其入口「保单管理」，
  // 否则菜单无项可高亮、用户会迷失当前位置。
  const selectedKey = activePath === '/plan' ? '/policy' : activePath;

  return (
    <Layout.Sider theme="light" breakpoint="lg" collapsedWidth={0} collapsed={collapsed}>
      <div style={{ padding: '16px 16px 8px' }}>
        <Typography.Text strong style={{ fontSize: 16, color: '#1677ff' }}>
          智能理赔
        </Typography.Text>
        <div style={{ marginTop: 4 }}>
          <Tag color="orange">独立调试 · 非产品导航</Tag>
        </div>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={[
          // hidden 路由不占产品菜单，但独立调试态需要能回到默认落点，否则 :3013 直开
          // 只能看到作业页、无法验证工作台本身（不是产品导航，不承载权限）。
          { key: '/workbench', label: '我的工作台（默认落点）' },
          ...CLAIM_ROUTES.filter((r) => !r.hidden).map((r) => ({
            key: r.path,
            icon: r.icon,
            label: r.name,
          })),
        ]}
        onClick={({ key }) => navigate(key)}
      />
    </Layout.Sider>
  );
}

export interface AppProps {
  /**
   * 是否运行在 shell（qiankun）容器内。
   * 由入口 `main.tsx` 依据 qiankun mount props / `__POWERED_BY_QIANKUN__` 传入，
   * 微应用不自行嗅探全局变量（避免 SSR / 单测环境下误判）。
   */
  embedded?: boolean;
}

export const App: React.FC<AppProps> = ({ embedded = false }) => {
  // SynapClaim 规范独立页：/sclaim/claim/detail?claimId=... 或 /sclaim/policy/detail?policyNo=...
  // 干净的 path + query（无 hash），因此必须用 BrowserRouter 让 useSearchParams 直接读到
  // window.location.search；不渲染 CommandPalette / DevNav（那是 shell 与调试态能力）。
  if (isStandalonePath(window.location.pathname)) {
    return (
      <AntdApp>
        <BrowserRouter>
          <ErrorBoundary>
            <Routes>
              <Route path={STANDALONE_PATHS.CLAIM_DETAIL} element={<Case360Page />} />
              <Route path={STANDALONE_PATHS.POLICY_DETAIL} element={<PolicyManagementPage />} />
            </Routes>
          </ErrorBoundary>
        </BrowserRouter>
      </AntdApp>
    );
  }

  return (
    <AntdApp>
      {/* 必须用 HashRouter：微应用内路由挂在 URL hash 上（/claim#/review），
          与 shell 的 MICRO_APP_ROUTE_PREFIXES 约定（pathname#/hash）一致。
          此前误用 BrowserRouter 会去读 pathname=/claim 而忽略 #/review，
          导致内部 <Routes> 永远匹配不到业务页面 ⇒ 内容区空白。 */}
      <HashRouter future={{ v7_relativeSplatPath: true }}>
        {/* 全局命令面板（⌘/Ctrl+K）只在根布局挂一次；必须在 Router 内使用 useNavigate。 */}
        <CommandPalette />
        <ErrorBoundary>
          {embedded ? (
            <ClaimRoutes />
          ) : (
            <Layout style={{ minHeight: '100vh' }}>
              <DevNav />
              <Layout.Content style={{ padding: 0 }}>
                <ClaimRoutes />
              </Layout.Content>
            </Layout>
          )}
        </ErrorBoundary>
      </HashRouter>
    </AntdApp>
  );
};
