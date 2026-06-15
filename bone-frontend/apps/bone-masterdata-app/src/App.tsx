import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import QualityRuleManagement from './pages/QualityRuleManagement';
import RecordManagement from './pages/RecordManagement';

const App: React.FC = () => {
  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/fields" element={<FieldManagement />} />
          <Route path="/rules" element={<QualityRuleManagement />} />
          <Route path="/records" element={<RecordManagement />} />
          <Route path="/" element={<Navigate to="/entities" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
};

export default App;
