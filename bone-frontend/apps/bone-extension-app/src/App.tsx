import React from 'react';
import { App as AntdApp, Breadcrumb, theme } from 'antd';
import { HashRouter, Routes, Route, useLocation } from 'react-router-dom';
import ExtensionOverview from '@/pages/ExtensionOverview';
import ExtensionPointManagement from '@/pages/ExtensionPointManagement';
import PluginManagement from '@/pages/PluginManagement';
import SandboxManagement from '@/pages/SandboxManagement';
import DependencyGraph from '@/pages/DependencyGraph';
import Marketplace from '@/pages/Marketplace';
import DeploymentManagementPage from '@/pages/DeploymentManagementPage';
import ExecutionLogPage from '@/pages/ExecutionLogPage';
import './App.css';

type MenuKey = 'overview' | 'point' | 'plugin' | 'deploy' | 'sandbox' | 'graph' | 'marketplace' | 'logs';

const AppContent: React.FC = () => {
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const selectedKey = (): MenuKey => {
    const path = location.pathname;
    if (path.includes('market')) return 'marketplace';
    if (path.includes('graph')) return 'graph';
    if (path.includes('plugin')) return 'plugin';
    if (path.includes('sandbox')) return 'sandbox';
    if (path.includes('deploy')) return 'deploy';
    if (path.includes('point')) return 'point';
    if (path.includes('logs')) return 'logs';
    return 'overview';
  };

  const breadcrumbLabel: Record<MenuKey, string> = {
    overview: '概览',
    point: '扩展点管理',
    plugin: '插件管理',
    sandbox: '沙箱管理',
    graph: '依赖图',
    marketplace: '插件市场',
    deploy: '部署管理',
    logs: '运行日志',
  };

  const key = selectedKey();

  return (
    <div style={{ padding: 16, minHeight: '100vh' }}>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[{ title: '扩展管理' }, { title: breadcrumbLabel[key] }]}
      />
      <div
        style={{
          padding: 24,
          minHeight: 360,
          background: colorBgContainer,
          borderRadius: borderRadiusLG,
        }}
      >
        <Routes>
          <Route path="/overview" element={<ExtensionOverview />} />
          <Route path="/points" element={<ExtensionPointManagement />} />
          <Route path="/plugins" element={<PluginManagement />} />
          <Route path="/sandbox" element={<SandboxManagement />} />
          <Route path="/graph" element={<DependencyGraph />} />
          <Route path="/market" element={<Marketplace />} />
          <Route path="/deploy" element={<DeploymentManagementPage />} />
          <Route path="/logs" element={<ExecutionLogPage />} />
          <Route path="*" element={<ExtensionOverview />} />
        </Routes>
      </div>
    </div>
  );
};

const App: React.FC = () => {
  return (
    <AntdApp>
      {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
          消除每次挂载必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
          两者均为 v7 的向后兼容默认值，语义不变。 */}
      <HashRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppContent />
      </HashRouter>
    </AntdApp>
  );
};

export default App;
