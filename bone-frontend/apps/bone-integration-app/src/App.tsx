import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/Layout';
import { ConnectorManagement } from './pages/ConnectorManagement';
import { FlowDesign } from './pages/FlowDesign';
import { FlowMonitor } from './pages/FlowMonitor';
import './App.css';

interface AppProps {
  user?: any;
}

export const App: React.FC<AppProps> = ({ user }) => {
  return (
    <Router>
      <AppLayout>
        <Routes>
          <Route path="/" element={<ConnectorManagement />} />
          <Route path="/flow-design" element={<FlowDesign />} />
          <Route path="/flow-monitor" element={<FlowMonitor />} />
        </Routes>
      </AppLayout>
    </Router>
  );
};