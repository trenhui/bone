import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import SystemConfig from './pages/SystemConfig';
import MonitorAlert from './pages/MonitorAlert';
import LogManagement from './pages/LogManagement';
import SystemDeployment from './pages/SystemDeployment';
import './App.css';

const App: React.FC = () => {
  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/system-config" element={<SystemConfig />} />
          <Route path="/monitor-alert" element={<MonitorAlert />} />
          <Route path="/logs" element={<LogManagement />} />
          <Route path="/deployment" element={<SystemDeployment />} />
          <Route path="/" element={<Navigate to="/system-config" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
};

export default App;
