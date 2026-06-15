import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import RelationManagement from './pages/RelationManagement';
import RuntimeDataManagement from './pages/RuntimeDataManagement';
import './App.css';

function App(): JSX.Element {
  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/fields" element={<FieldManagement />} />
          <Route path="/relations" element={<RelationManagement />} />
          <Route path="/runtime" element={<RuntimeDataManagement />} />
          <Route path="/" element={<Navigate to="/entities" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
