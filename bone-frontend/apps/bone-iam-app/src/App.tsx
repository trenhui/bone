import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './components/Layout';
import AccountManagement from './pages/AccountManagement';
import RoleManagement from './pages/RoleManagement';
import PermissionManagement from './pages/PermissionManagement';
import AuditLog from './pages/AuditLog';
import Auth from './pages/Auth';

interface AppProps {
  user?: any;
}

const App: React.FC<AppProps> = () => {
  const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const token = localStorage.getItem('token');
    if (!token) {
      return <Navigate to="/login" replace />;
    }
    return <AppLayout>{children}</AppLayout>;
  };

  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Auth />} />
        <Route path="/accounts" element={<ProtectedRoute><AccountManagement /></ProtectedRoute>} />
        <Route path="/roles" element={<ProtectedRoute><RoleManagement /></ProtectedRoute>} />
        <Route path="/permissions" element={<ProtectedRoute><PermissionManagement /></ProtectedRoute>} />
        <Route path="/audit-logs" element={<ProtectedRoute><AuditLog /></ProtectedRoute>} />
        <Route path="/" element={<Navigate to="/accounts" replace />} />
        <Route path="*" element={<Navigate to="/accounts" />} />
      </Routes>
    </Router>
  );
};

export default App;
