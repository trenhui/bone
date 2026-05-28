import React from 'react';
import { Layout, Menu, Breadcrumb, theme } from 'antd';
import {
  AppstoreOutlined,
  ApartmentOutlined,
  DashboardOutlined,
  SafetyOutlined,
  SettingOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { BrowserRouter, Routes, Route, useLocation, Link } from 'react-router-dom';
import ExtensionOverview from '@/pages/ExtensionOverview';
import ExtensionPointManagement from '@/pages/ExtensionPointManagement';
import PluginManagement from '@/pages/PluginManagement';
import SandboxManagement from '@/pages/SandboxManagement';
import DependencyGraph from '@/pages/DependencyGraph';
import Marketplace from '@/pages/Marketplace';
import './App.css';

const { Content, Sider } = Layout;

type MenuKey = 'overview' | 'point' | 'plugin' | 'sandbox' | 'graph' | 'marketplace';

const AppContent: React.FC = () => {
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const selectedKey = (): MenuKey => {
    if (location.pathname.includes('marketplace')) return 'marketplace';
    if (location.pathname.includes('dependency')) return 'graph';
    if (location.pathname.includes('plugin')) return 'plugin';
    if (location.pathname.includes('sandbox')) return 'sandbox';
    if (location.pathname.includes('point')) return 'point';
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
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={200} style={{ background: colorBgContainer }}>
        <div
          style={{
            height: 48,
            margin: 16,
            fontWeight: 600,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          扩展管理
        </div>
        <Menu
          mode="inline"
          selectedKeys={[key]}
          style={{ borderRight: 0 }}
          items={[
            {
              key: 'overview',
              icon: <DashboardOutlined />,
              label: <Link to="/extension">概览</Link>,
            },
            {
              key: 'point',
              icon: <SettingOutlined />,
              label: <Link to="/extension/point">扩展点管理</Link>,
            },
            {
              key: 'plugin',
              icon: <AppstoreOutlined />,
              label: <Link to="/extension/plugin">插件管理</Link>,
            },
            {
              key: 'graph',
              icon: <ApartmentOutlined />,
              label: <Link to="/extension/dependency">依赖图</Link>,
            },
            {
              key: 'marketplace',
              icon: <ShopOutlined />,
              label: <Link to="/extension/marketplace">插件市场</Link>,
            },
            {
              key: 'sandbox',
              icon: <SafetyOutlined />,
              label: <Link to="/extension/sandbox">沙箱管理</Link>,
            },
          ]}
        />
      </Sider>
      <Layout>
        <Content style={{ margin: 16 }}>
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
              <Route path="/extension" element={<ExtensionOverview />} />
              <Route path="/extension/point" element={<ExtensionPointManagement />} />
              <Route path="/extension/plugin" element={<PluginManagement />} />
              <Route path="/extension/dependency" element={<DependencyGraph />} />
              <Route path="/extension/marketplace" element={<Marketplace />} />
              <Route path="/extension/sandbox" element={<SandboxManagement />} />
              <Route path="*" element={<ExtensionOverview />} />
            </Routes>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

interface AppProps {
  user?: Record<string, unknown>;
}

const App: React.FC<AppProps> = () => (
  <BrowserRouter>
    <AppContent />
  </BrowserRouter>
);

export default App;
