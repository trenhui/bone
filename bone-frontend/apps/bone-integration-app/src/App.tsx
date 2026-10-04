import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/Layout';
import { ConnectorManagement } from './pages/ConnectorManagement';
import { FlowDesign } from './pages/FlowDesign';
import { FlowMonitor } from './pages/FlowMonitor';
import './App.css';

export const App: React.FC = () => {
  return (
    <AntdApp>
      {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
          消除每次挂载必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
          两者均为 v7 的向后兼容默认值，语义不变。 */}
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppLayout>
          <Routes>
          <Route path="/connectors" element={<ConnectorManagement />} />
            <Route path="/flows" element={<FlowDesign />} />
            <Route path="/monitor" element={<FlowMonitor />} />
            <Route path="/" element={<ConnectorManagement />} />
          </Routes>
        </AppLayout>
      </Router>
    </AntdApp>
  );
};
