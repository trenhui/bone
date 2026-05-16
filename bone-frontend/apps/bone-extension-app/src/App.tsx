import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import { Layout, Menu, Breadcrumb } from 'antd';
import { AppstoreOutlined, SafetyOutlined, SettingOutlined } from '@ant-design/icons';
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
            defaultSelectedKeys={['extension-point']}
            style={{ height: '100%', borderRight: 0 }}
          >
            <Menu.Item key="extension-point" icon={<SettingOutlined />}>
              <Link to="/extension/point">扩展点管理</Link>
            </Menu.Item>
            <Menu.Item key="plugin" icon={<AppstoreOutlined />}>
              <Link to="/extension/plugin">插件管理</Link>
            </Menu.Item>
            <Menu.Item key="sandbox" icon={<SafetyOutlined />}>
              <Link to="/extension/sandbox">沙箱管理</Link>
            </Menu.Item>
          </Menu>
        </Sider>
        <Layout>
          <Header style={{ padding: 0, background: '#fff' }} />
          <Content style={{ margin: '0 16px' }}>
            <Breadcrumb style={{ margin: '16px 0' }}>
              <Breadcrumb.Item>扩展管理</Breadcrumb.Item>
              <Breadcrumb.Item>扩展点管理</Breadcrumb.Item>
            </Breadcrumb>
            <div className="content">
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
    </Router>
  );
}

function ExtensionPointManagement() {
  return <div className="page">扩展点管理页面</div>;
}

function PluginManagement() {
  return <div className="page">插件管理页面</div>;
}

function SandboxManagement() {
  return <div className="page">沙箱管理页面</div>;
}

export default App;