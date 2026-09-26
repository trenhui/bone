import React from 'react';
import { Layout, Menu, Button, theme } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import {
  AppstoreOutlined,
  TableOutlined,
  SafetyCertificateOutlined,
  ClusterOutlined,
  ApartmentOutlined,
  CrownOutlined,
  FileDoneOutlined,
  DatabaseOutlined,
  FieldTimeOutlined
} from '@ant-design/icons';

const { Header, Sider, Content } = Layout;

interface MenuItemType {
  key: string;
  label: string;
  icon: React.ReactNode;
  path: string;
}

interface MenuGroup {
  groupLabel: string;
  items: MenuItemType[];
}

// 六段式能力链导航（3a 设计 §5）：建模 → 采集 → 治理 → 发布 → 分发 → 监控
const menuGroups: MenuGroup[] = [
  {
    groupLabel: '主数据域',
    items: [
      { key: 'entity', label: '主数据域', icon: <AppstoreOutlined />, path: '/entities' },
      { key: 'field', label: '字段管理', icon: <FieldTimeOutlined />, path: '/fields' },
      { key: 'record', label: '记录管理', icon: <TableOutlined />, path: '/records' }
    ]
  },
  {
    groupLabel: '治理与质量',
    items: [
      { key: 'rule', label: '质量规则', icon: <SafetyCertificateOutlined />, path: '/rules' },
      { key: 'quality-results', label: '质检结果', icon: <FileDoneOutlined />, path: '/quality-results' },
      { key: 'quality-issues', label: '整改工单', icon: <FileDoneOutlined />, path: '/quality-issues' },
      { key: 'category', label: '分类体系', icon: <ApartmentOutlined />, path: '/categories' },
      { key: 'governance', label: '治理看板', icon: <CrownOutlined />, path: '/governance' }
    ]
  },
  {
    groupLabel: '平台能力',
    items: [
      { key: 'template', label: '域模板', icon: <ClusterOutlined />, path: '/templates' },
      { key: 'reference', label: '参考数据', icon: <DatabaseOutlined />, path: '/reference-sets' }
    ]
  }
];

const allItems = menuGroups.flatMap((g) => g.items);

const AppLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  const { token: { colorBgContainer } } = theme.useToken();

  // 获取当前选中的菜单项
  const getSelectedKey = () => {
    const path = location.pathname;
    const item = allItems.find((i) => i.path === path);
    return item ? item.key : 'entity';
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
            items={menuGroups.map((group) => ({
              key: `group-${group.groupLabel}`,
              type: 'group',
              label: group.groupLabel,
              children: group.items.map((item) => ({
                key: item.key,
                icon: item.icon,
                label: <Link to={item.path}>{item.label}</Link>
              }))
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
