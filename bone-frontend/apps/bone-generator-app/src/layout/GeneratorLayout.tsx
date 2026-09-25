import React from 'react';
import { Layout, Menu, Breadcrumb, theme } from 'antd';
import {
  DatabaseOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const { Header, Sider, Content } = Layout;

const MENU_ITEMS = [
  { key: '/datasources', icon: <DatabaseOutlined />, label: '数据源管理' },
  { key: '/generate', icon: <ThunderboltOutlined />, label: '代码生成' },
  { key: '/templates', icon: <FileTextOutlined />, label: '模板管理' },
  { key: '/history', icon: <HistoryOutlined />, label: '生成历史' },
];

const BREADCRUMB_MAP: Record<string, string> = {
  '/datasources': '数据源管理',
  '/generate': '代码生成',
  '/templates': '模板管理',
  '/history': '生成历史',
};

const GeneratorLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = React.useState(false);
  const { token } = theme.useToken();

  const selectedKey =
    MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ??
    '/datasources';
  const breadcrumbLabel = BREADCRUMB_MAP[selectedKey] ?? '首页';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={240}
        theme="light"
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 600,
            color: token.colorPrimary,
            fontSize: collapsed ? 14 : 16,
          }}
        >
          {collapsed ? 'SG' : 'Studio Generator'}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            paddingInline: 24,
            display: 'flex',
            alignItems: 'center',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Breadcrumb
            items={[{ title: 'Studio Generator' }, { title: breadcrumbLabel }]}
          />
        </Header>
        <Content className="page-container">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default GeneratorLayout;
