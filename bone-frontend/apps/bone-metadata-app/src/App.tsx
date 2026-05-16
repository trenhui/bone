import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import { Layout, Menu, Breadcrumb } from 'antd';
import { DatabaseOutlined, FieldTimeOutlined, CodeOutlined, FileTextOutlined } from '@ant-design/icons';
import './App.css';

const { Header, Content, Sider } = Layout;

interface AppProps {
  user?: any;
}

function App({ user: _user }: AppProps) {
  return (
    <Router>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider width={200} style={{ background: '#fff' }}>
          <Menu
            mode="inline"
            defaultSelectedKeys={['entity']}
            style={{ height: '100%', borderRight: 0 }}
            items={[
              {
                key: 'entity',
                icon: <DatabaseOutlined />,
                label: <Link to="/metadata/entity">实体管理</Link>
              },
              {
                key: 'field',
                icon: <FieldTimeOutlined />,
                label: <Link to="/metadata/field">字段管理</Link>
              },
              {
                key: 'code',
                icon: <CodeOutlined />,
                label: <Link to="/metadata/code">代码生成</Link>
              },
              {
                key: 'template',
                icon: <FileTextOutlined />,
                label: <Link to="/metadata/template">模板管理</Link>
              }
            ]}
          />
        </Sider>
        <Layout>
          <Header style={{ padding: 0, background: '#fff' }} />
          <Content style={{ margin: '0 16px' }}>
            <Breadcrumb style={{ margin: '16px 0' }} items={[
              { title: '元数据管理' },
              { title: '实体管理' }
            ]} />
            <div className="content">
              <Routes>
                <Route path="/metadata/entity" element={<EntityManagement />} />
                <Route path="/metadata/field" element={<FieldManagement />} />
                <Route path="/metadata/code" element={<CodeGeneration />} />
                <Route path="/metadata/template" element={<TemplateManagement />} />
                <Route path="*" element={<EntityManagement />} />
              </Routes>
            </div>
          </Content>
        </Layout>
      </Layout>
    </Router>
  );
}

function EntityManagement() {
  return <div className="page">实体管理页面</div>;
}

function FieldManagement() {
  return <div className="page">字段管理页面</div>;
}

function CodeGeneration() {
  return <div className="page">代码生成页面</div>;
}

function TemplateManagement() {
  return <div className="page">模板管理页面</div>;
}

export default App;