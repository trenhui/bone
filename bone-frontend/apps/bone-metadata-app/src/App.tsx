import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import { Layout, Menu, Breadcrumb } from 'antd';
import {
  DatabaseOutlined,
  FieldTimeOutlined,
  CodeOutlined,
  FileTextOutlined,
  ShareAltOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import RelationManagement from './pages/RelationManagement';
import RuntimeDataManagement from './pages/RuntimeDataManagement';
import './App.css';

const { Header, Content, Sider } = Layout;

interface AppProps {
  user?: unknown;
}

const breadcrumbMap: Record<string, string> = {
  '/metadata/entity': '实体管理',
  '/metadata/field': '字段管理',
  '/metadata/relation': '关系管理',
  '/metadata/runtime': '运行时数据',
  '/metadata/code': '代码生成',
  '/metadata/template': '模板管理',
};

function AppLayout(): JSX.Element {
  const location = useLocation();
  const pageTitle = breadcrumbMap[location.pathname] ?? '实体管理';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={200} style={{ background: '#fff' }}>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname.split('/').pop() ?? 'entity']}
          style={{ height: '100%', borderRight: 0 }}
          items={[
            {
              key: 'entity',
              icon: <DatabaseOutlined />,
              label: <Link to="/metadata/entity">实体管理</Link>,
            },
            {
              key: 'field',
              icon: <FieldTimeOutlined />,
              label: <Link to="/metadata/field">字段管理</Link>,
            },
            {
              key: 'relation',
              icon: <ShareAltOutlined />,
              label: <Link to="/metadata/relation">关系管理</Link>,
            },
            {
              key: 'runtime',
              icon: <ThunderboltOutlined />,
              label: <Link to="/metadata/runtime">运行时数据</Link>,
            },
            {
              key: 'code',
              icon: <CodeOutlined />,
              label: <Link to="/metadata/code">代码生成</Link>,
            },
            {
              key: 'template',
              icon: <FileTextOutlined />,
              label: <Link to="/metadata/template">模板管理</Link>,
            },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: 0, background: '#fff' }} />
        <Content style={{ margin: '0 16px' }}>
          <Breadcrumb
            style={{ margin: '16px 0' }}
            items={[{ title: '元数据管理' }, { title: pageTitle }]}
          />
          <div className="content">
            <Routes>
              <Route path="/metadata/entity" element={<EntityManagement />} />
              <Route path="/metadata/field" element={<FieldManagement />} />
              <Route path="/metadata/relation" element={<RelationManagement />} />
              <Route path="/metadata/runtime" element={<RuntimeDataManagement />} />
              <Route path="/metadata/code" element={<Placeholder title="代码生成" />} />
              <Route path="/metadata/template" element={<Placeholder title="模板管理" />} />
              <Route path="*" element={<EntityManagement />} />
            </Routes>
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <div className="page">
      <p>{title}（对接 studio-generator，见 MVP-2 后续项）</p>
    </div>
  );
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
function App(_props: AppProps): JSX.Element {
  return (
    <Router>
      <AppLayout />
    </Router>
  );
}

export default App;
