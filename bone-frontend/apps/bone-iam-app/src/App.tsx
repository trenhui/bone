import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './components/Layout';
import UserManagement from './pages/UserManagement';
import RoleManagement from './pages/RoleManagement';
import PermissionManagement from './pages/PermissionManagement';
import AuditLog from './pages/AuditLog';

interface AppProps {
  user?: any;
}

const App: React.FC<AppProps> = ({ user }) => {
  // 主应用已经处理了登录状态，直接使用 AppLayout
  const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    return <AppLayout>{children}</AppLayout>;
  };

  return (
    <Router>
      <Routes>
        <Route path="/users" element={<ProtectedRoute><UserManagement /></ProtectedRoute>} />
        <Route path="/roles" element={<ProtectedRoute><RoleManagement /></ProtectedRoute>} />
        <Route path="/permissions" element={<ProtectedRoute><PermissionManagement /></ProtectedRoute>} />
        <Route path="/audit-logs" element={<ProtectedRoute><AuditLog /></ProtectedRoute>} />
        <Route path="/" element={<Navigate to="/users" />} />
        <Route path="*" element={<Navigate to="/users" />} />
      </Routes>
    </Router>
  );
};

export default App;