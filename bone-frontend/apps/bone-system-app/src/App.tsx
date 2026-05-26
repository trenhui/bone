import React, { useState } from 'react';
import { Layout, Menu, theme } from 'antd';
import {
  SettingOutlined,
  MonitorOutlined,
  FileTextOutlined,
  CloudServerOutlined,
} from '@ant-design/icons';
import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import SystemConfig from './pages/SystemConfig';
import MonitorAlert from './pages/MonitorAlert';
import LogManagement from './pages/LogManagement';
import SystemDeployment from './pages/SystemDeployment';
import './App.css';

const { Header, Content, Sider } = Layout;

type MenuKey = 'system-config' | 'monitor-alert' | 'log-management' | 'system-deployment';

const AppContent: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const getSelectedKey = (): MenuKey => {
    const path = location.pathname;
    if (path.includes('monitor')) return 'monitor-alert';
    if (path.includes('logs')) return 'log-management';
    if (path.includes('deployment')) return 'system-deployment';
    return 'system-config';
  };

  const menuItems = [
    {
      key: 'system-config',
      icon: <SettingOutlined />,
      label: '系统配置',
    },
    {
      key: 'monitor-alert',
      icon: <MonitorOutlined />,
      label: '监控告警',
    },
    {
      key: 'log-management',
      icon: <FileTextOutlined />,
      label: '日志管理',
    },
    {
      key: 'system-deployment',
      icon: <CloudServerOutlined />,
      label: '系统部署',
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    const pathMap: Record<string, string> = {
      'system-config': '/system-config',
      'monitor-alert': '/monitor-alert',
      'log-management': '/logs',
      'system-deployment': '/deployment',
    };
    navigate(pathMap[key] || '/');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={(value) => setCollapsed(value)}>
        <div className="demo-logo-vertical" />
        <div style={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontSize: collapsed ? 14 : 18,
          fontWeight: 'bold',
        }}>
          {collapsed ? '系统' : 'BONE 系统管理'}
        </div>
        <Menu
          theme="dark"
          selectedKeys={[getSelectedKey()]}
          mode="inline"
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: 0, background: colorBgContainer }} />
        <Content style={{ margin: '16px 16px' }}>
          <div
            style={{
              padding: 24,
              minHeight: 360,
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
            }}
          >
            <Routes>
              <Route path="/" element={<SystemConfig />} />
              <Route path="/system-config" element={<SystemConfig />} />
              <Route path="/monitor-alert" element={<MonitorAlert />} />
              <Route path="/logs" element={<LogManagement />} />
              <Route path="/deployment" element={<SystemDeployment />} />
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

const App: React.FC<AppProps> = () => {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
};

export default App;
