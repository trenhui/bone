import React from 'react';
import { App as AntdApp, Breadcrumb, theme } from 'antd';
import { HashRouter, Routes, Route, useLocation } from 'react-router-dom';
import ExtensionOverview from '@/pages/ExtensionOverview';
import ExtensionPointManagement from '@/pages/ExtensionPointManagement';
import PluginManagement from '@/pages/PluginManagement';
import SandboxManagement from '@/pages/SandboxManagement';
import DependencyGraph from '@/pages/DependencyGraph';
import Marketplace from '@/pages/Marketplace';
import './App.css';

type MenuKey = 'overview' | 'point' | 'plugin' | 'sandbox' | 'graph' | 'marketplace';

const AppContent: React.FC = () => {
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const selectedKey = (): MenuKey => {
    const path = location.pathname;
    if (path.includes('marketplace')) return 'marketplace';
    if (path.includes('graph')) return 'graph';
    if (path.includes('plugin')) return 'plugin';
    if (path.includes('sandbox')) return 'sandbox';
    if (path.includes('point')) return 'point';
    return 'overview';
  };

  const breadcrumbLabel: Record<MenuKey, string> = {
    overview: '概览',
    point: '扩展点管理',
    plugin: '插件管理',
    sandbox: '沙箱管理',
    graph: '依赖图',
    marketplace: '插件市场',
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
          <Route path="/point" element={<ExtensionPointManagement />} />
          <Route path="/plugin" element={<PluginManagement />} />
          <Route path="/sandbox" element={<SandboxManagement />} />
          <Route path="/graph" element={<DependencyGraph />} />
          <Route path="/marketplace" element={<Marketplace />} />
          <Route path="*" element={<ExtensionOverview />} />
        </Routes>
      </div>
    </div>
  );
};

const App: React.FC = () => {
  return (
    <AntdApp>
      <HashRouter>
        <AppContent />
      </HashRouter>
    </AntdApp>
  );
};

export default App;
