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
          <Route path="/data-sources" element={<DataSourceManagement />} />
          <Route path="/code-generation" element={<CodeGeneration />} />
          <Route path="/templates" element={<TemplateManagement />} />
          <Route path="/history" element={<GenerationHistory />} />
          <Route path="/" element={<Navigate to="/data-sources" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
