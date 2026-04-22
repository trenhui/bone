import React from 'react';
import { Layout, Menu, Button } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import { AppstoreOutlined, ApiOutlined, BarChartOutlined } from '@ant-design/icons';

const { Header, Sider, Content } = Layout;

interface LayoutProps {
  children: React.ReactNode;
}

export const AppLayout: React.FC<LayoutProps> = ({ children }) => {
  const location = useLocation();
  
  const menuItems = [
    {
      key: '/',
      icon: <AppstoreOutlined />,
      label: <Link to="/">连接器管理</Link>,
    },
    {
      key: '/flow-design',
      icon: <ApiOutlined />,
      label: <Link to="/flow-design">流程设计</Link>,
    },
    {
      key: '/flow-monitor',
      icon: <BarChartOutlined />,
      label: <Link to="/flow-monitor">流程监控</Link>,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', background: '#001529' }}>
        <div style={{ color: '#fff', fontSize: '18px', fontWeight: 'bold', marginRight: '24px' }}>
          BONE 集成管理
        </div>
        <Button type="text" style={{ color: '#fff' }}>
          帮助
        </Button>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: '#001529' }}>
          <Menu
            mode="inline"
            items={menuItems}
            selectedKeys={[location.pathname]}
            style={{ height: '100%', borderRight: 0, background: '#001529' }}
            theme="dark"
          />
        </Sider>
        <Content style={{ padding: '24px', background: '#f0f2f5' }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};