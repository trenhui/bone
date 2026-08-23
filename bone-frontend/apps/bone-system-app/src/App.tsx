import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import SystemConfig from './pages/SystemConfig';
import MonitorAlert from './pages/MonitorAlert';
import LogManagement from './pages/LogManagement';
import SystemDeployment from './pages/SystemDeployment';
import DictManagement from './pages/DictManagement';
import ScheduleTaskManagement from './pages/ScheduleTaskManagement';
import './App.css';

const App: React.FC = () => {
  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/config" element={<SystemConfig />} />
          <Route path="/alerts" element={<MonitorAlert />} />
          <Route path="/logs" element={<LogManagement />} />
          <Route path="/k8s" element={<SystemDeployment />} />
          <Route path="/dict" element={<DictManagement />} />
          <Route path="/schedule" element={<ScheduleTaskManagement />} />
          <Route path="/" element={<Navigate to="/config" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
};

export default App;
