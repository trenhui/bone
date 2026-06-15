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
      <Router>
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
