import React from 'react';
import { Layout, Menu, Breadcrumb, theme } from 'antd';
import { AppstoreOutlined, SafetyOutlined, SettingOutlined } from '@ant-design/icons';
import { BrowserRouter, Routes, Route, useLocation, Link } from 'react-router-dom';
import ExtensionPointManagement from '@/pages/ExtensionPointManagement';
import PluginManagement from '@/pages/PluginManagement';
import SandboxManagement from '@/pages/SandboxManagement';
import './App.css';

const { Content, Sider } = Layout;

type MenuKey = 'point' | 'plugin' | 'sandbox';

const AppContent: React.FC = () => {
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const selectedKey = (): MenuKey => {
    if (location.pathname.includes('plugin')) return 'plugin';
    if (location.pathname.includes('sandbox')) return 'sandbox';
    return 'point';
  };

  const breadcrumbLabel: Record<MenuKey, string> = {
    point: '扩展点管理',
    plugin: '插件管理',
    sandbox: '沙箱管理',
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
              <Route path="/extension/point" element={<ExtensionPointManagement />} />
              <Route path="/extension/plugin" element={<PluginManagement />} />
              <Route path="/extension/sandbox" element={<SandboxManagement />} />
              <Route path="*" element={<ExtensionPointManagement />} />
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
