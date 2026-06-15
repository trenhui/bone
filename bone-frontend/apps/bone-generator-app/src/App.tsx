import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import DataSourceManagement from './pages/DataSourceManagement';
import CodeGeneration from './pages/CodeGeneration';
import TemplateManagement from './pages/TemplateManagement';
import GenerationHistory from './pages/GenerationHistory';

function App(): JSX.Element {
  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/datasources" element={<DataSourceManagement />} />
          <Route path="/generate" element={<CodeGeneration />} />
          <Route path="/templates" element={<TemplateManagement />} />
          <Route path="/history" element={<GenerationHistory />} />
          <Route path="/" element={<Navigate to="/datasources" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
