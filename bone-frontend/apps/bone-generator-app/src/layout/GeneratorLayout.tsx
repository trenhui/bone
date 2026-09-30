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

/**
 * 集成态（qiankun 微前端）下，左侧主导航由父容器 Shell 的「代码生成」分组统一提供，
 * 子菜单为 数据源管理 / 代码生成 / 模板管理 / 生成历史 —— 与本地 Sider 完全重复。
 * 为避免重复导航，集成态不渲染本地 Sider；仅在独立运行（非微前端）时保留本地导航，
 * 以便本地开发联调 generator 应用本身。
 */
const IN_QIANKUN =
  typeof window !== 'undefined' &&
  Boolean(
    (window as unknown as { __POWERED_BY_QIANKUN__?: boolean }).__POWERED_BY_QIANKUN__,
  );

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
      {!IN_QIANKUN && (
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
      )}
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
