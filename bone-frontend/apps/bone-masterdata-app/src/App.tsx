import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import AppLayout from './components/Layout';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import QualityRuleManagement from './pages/QualityRuleManagement';
import RecordManagement from './pages/RecordManagement';
import { Card, Typography } from 'antd';

const { Title, Paragraph } = Typography;

const Home: React.FC = () => {
  return (
    <Card>
      <Typography>
        <Title level={2}>欢迎使用 BONE 主数据管理系统</Title>
        <Paragraph>
          BONE 主数据管理系统是一个基于 React + TypeScript + Ant Design 开发的现代化主数据管理平台，
          提供实体管理、字段管理、数据质量规则管理和记录管理等功能。
        </Paragraph>
        <Paragraph>
          系统特点：
        </Paragraph>
        <ul>
          <li>基于 DDD 领域驱动设计思想</li>
          <li>支持丰富的字段类型和验证规则</li>
          <li>内置数据质量检查功能</li>
          <li>支持 Excel 导入导出</li>
          <li>现代化的用户界面</li>
        </ul>
      </Typography>
    </Card>
  );
};

interface AppProps {
  user?: Record<string, unknown>;
}

const App: React.FC<AppProps> = () => {
  return (
    <Router>
      <AppLayout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/entity" element={<EntityManagement />} />
          <Route path="/field" element={<FieldManagement />} />
          <Route path="/quality" element={<QualityRuleManagement />} />
          <Route path="/record" element={<RecordManagement />} />
        </Routes>
      </AppLayout>
    </Router>
  );
};

export default App;
