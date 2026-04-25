import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link, Navigate } from 'react-router-dom';
import { Layout, Menu, Typography } from 'antd';
import DataSourceManagement from './pages/DataSourceManagement';
import CodeGeneration from './pages/CodeGeneration';
import TemplateManagement from './pages/TemplateManagement';
import GenerationHistory from './pages/GenerationHistory';

const { Header, Content, Sider } = Layout;
const { Title } = Typography;

function App() {
  return (
    <Router>
      <Layout style={{ minHeight: '100vh' }}>
        <Header style={{ display: 'flex', alignItems: 'center', background: '#fff', borderBottom: '1px solid #f0f0f0' }}>
          <Title level={4} style={{ margin: 0, color: '#1890ff' }}>BONE Studio Generator</Title>
        </Header>
        <Layout>
          <Sider width={200} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
            <Menu
              mode="inline"
              defaultSelectedKeys={['1']}
              style={{ height: '100%', borderRight: 0 }}
              items={[
                {
                  key: '1',
                  label: <Link to="/data-sources">数据源管理</Link>,
                },
                {
                  key: '2',
                  label: <Link to="/code-generation">代码生成</Link>,
                },
                {
                  key: '3',
                  label: <Link to="/templates">模板管理</Link>,
                },
                {
                  key: '4',
                  label: <Link to="/history">生成历史</Link>,
                },
              ]}
            />
          </Sider>
          <Content style={{ padding: '24px' }}>
            <Routes>
              <Route path="/data-sources" element={<DataSourceManagement />} />
              <Route path="/code-generation" element={<CodeGeneration />} />
              <Route path="/templates" element={<TemplateManagement />} />
              <Route path="/history" element={<GenerationHistory />} />
              <Route path="/" element={<Navigate to="/data-sources" replace />} />
            </Routes>
          </Content>
        </Layout>
      </Layout>
    </Router>
  );
}

export default App;