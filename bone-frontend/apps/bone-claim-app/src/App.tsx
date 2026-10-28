import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { App as AntdApp, Layout, Menu, Tag, Typography } from 'antd';
import { CLAIM_DEFAULT_PATH, CLAIM_ROUTES } from './routes';
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
function ClaimRoutes(): JSX.Element {
  return (
    <Routes>
      {CLAIM_ROUTES.map((r) => (
        <Route key={r.path} path={r.path} element={r.element} />
      ))}
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
  const selectedKey =
    CLAIM_ROUTES.map((r) => r.path).find((k) => location.pathname.startsWith(k)) ?? CLAIM_DEFAULT_PATH;

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
        items={CLAIM_ROUTES.map((r) => ({ key: r.path, icon: r.icon, label: r.name }))}
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
  return (
    <AntdApp>
      {/* 必须用 HashRouter：微应用内路由挂在 URL hash 上（/claim#/review），
          与 shell 的 MICRO_APP_ROUTE_PREFIXES 约定（pathname#/hash）一致。
          此前误用 BrowserRouter 会去读 pathname=/claim 而忽略 #/review，
          导致内部 <Routes> 永远匹配不到业务页面 ⇒ 内容区空白。 */}
      <HashRouter future={{ v7_relativeSplatPath: true }}>
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
      </HashRouter>
    </AntdApp>
  );
};
