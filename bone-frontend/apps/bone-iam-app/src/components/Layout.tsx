import React, { useState } from 'react';
import { Layout, Menu, Button, Dropdown, message } from 'antd';
import { LogoutOutlined, UserOutlined, TeamOutlined, LockOutlined, AuditOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import * as api from '../services/api';

const { Header, Sider, Content } = Layout;

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await api.logout();
      localStorage.removeItem('token');
      navigate('/login');
    } catch {
      message.error('退出登录失败');
    }
  };

  const menuItems = [
    {
      key: '1',
      icon: <UserOutlined />,
      label: <Link to="/accounts">账号管理</Link>,
    },
    {
      key: '2',
      icon: <TeamOutlined />,
      label: <Link to="/roles">角色管理</Link>,
    },
    {
      key: '3',
      icon: <LockOutlined />,
      label: <Link to="/permissions">权限管理</Link>,
    },
    {
      key: '4',
      icon: <AuditOutlined />,
      label: <Link to="/audit-logs">审计日志</Link>,
    },
  ];

  const userMenu = [
    {
      key: 'profile',
      label: '个人资料',
    },
    {
      key: 'settings',
      label: '设置',
    },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={(value) => setCollapsed(value)}>
        <div style={{ height: '64px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '16px', fontWeight: 'bold' }}>
          Bone IAM
        </div>
        <Menu theme="dark" mode="inline" defaultSelectedKeys={['1']} items={menuItems} />
      </Sider>
      <Layout>
        <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px' }}>
          <div style={{ fontSize: '18px', fontWeight: 'bold' }}>IAM 账号权限管理</div>
          <Dropdown menu={{ items: userMenu }}>
            <Button type="text" icon={<UserOutlined />} style={{ marginLeft: '8px' }}>
              {localStorage.getItem('username') || '管理员'}
            </Button>
          </Dropdown>
        </Header>
        <Content style={{ margin: '24px', padding: '24px', background: '#fff', minHeight: 280 }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
