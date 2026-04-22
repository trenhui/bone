import React from 'react';
import { Layout, Menu, Button, theme } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import {
  HomeOutlined,
  AppstoreOutlined,
  FieldTimeOutlined,
  SafetyCertificateOutlined,
  TableOutlined
} from '@ant-design/icons';
import type { MenuItem as MenuItemType } from '../types';

const { Header, Sider, Content } = Layout;

const menuItems: MenuItemType[] = [
  {
    key: 'home',
    label: '首页',
    icon: <HomeOutlined />,
    path: '/'
  },
  {
    key: 'entity',
    label: '实体管理',
    icon: <AppstoreOutlined />,
    path: '/entity'
  },
  {
    key: 'field',
    label: '字段管理',
    icon: <FieldTimeOutlined />,
    path: '/field'
  },
  {
    key: 'quality',
    label: '质量规则管理',
    icon: <SafetyCertificateOutlined />,
    path: '/quality'
  },
  {
    key: 'record',
    label: '记录管理',
    icon: <TableOutlined />,
    path: '/record'
  }
];

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const { token: { colorBgContainer } } = theme.useToken();

  // 获取当前选中的菜单项
  const getSelectedKey = () => {
    const path = location.pathname;
    const item = menuItems.find(item => item.path === path);
    return item ? item.key : 'home';
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <AppstoreOutlined style={{ fontSize: '24px', color: '#fff', marginRight: 16 }} />
          <h1 style={{ color: '#fff', margin: 0, fontSize: '18px' }}>BONE 主数据管理系统</h1>
        </div>
        <div>
          <Button type="text" style={{ color: '#fff' }}>登录</Button>
        </div>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: colorBgContainer }}>
          <Menu
            mode="inline"
            selectedKeys={[getSelectedKey()]}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems.map(item => ({
              key: item.key,
              icon: item.icon,
              label: <Link to={item.path}>{item.label}</Link>
            }))}
          />
        </Sider>
        <Content style={{ padding: '24px', margin: 0, minHeight: 280, background: colorBgContainer }}>
          {children}
        </Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
