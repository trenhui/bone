import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { App as AntdApp } from 'antd';
import AccountManagement from './pages/AccountManagement';
import RoleManagement from './pages/RoleManagement';
import PermissionManagement from './pages/PermissionManagement';
import OrganizationManagement from './pages/OrganizationManagement';
import MenuManagement from './pages/MenuManagement';
import TenantManagement from './pages/TenantManagement';
import AuditLog from './pages/AuditLog';
import AuditSettings from './pages/AuditSettings';
import Auth from './pages/Auth';

const App: React.FC = () => {
  const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const token = localStorage.getItem('token');
    if (!token) {
      return <Navigate to="/login" replace />;
    }
    return <>{children}</>;
  };

  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/login" element={<Auth />} />
          <Route path="/accounts" element={<ProtectedRoute><AccountManagement /></ProtectedRoute>} />
          <Route path="/roles" element={<ProtectedRoute><RoleManagement /></ProtectedRoute>} />
          <Route path="/tenants" element={<ProtectedRoute><TenantManagement /></ProtectedRoute>} />
          <Route path="/permissions" element={<ProtectedRoute><PermissionManagement /></ProtectedRoute>} />
          <Route path="/organizations" element={<ProtectedRoute><OrganizationManagement /></ProtectedRoute>} />
          <Route path="/menus" element={<ProtectedRoute><MenuManagement /></ProtectedRoute>} />
          <Route path="/audit-logs" element={<ProtectedRoute><AuditLog /></ProtectedRoute>} />
          <Route path="/audit-settings" element={<ProtectedRoute><AuditSettings /></ProtectedRoute>} />
          <Route path="/" element={<Navigate to="/accounts" replace />} />
          <Route path="*" element={<Navigate to="/accounts" />} />
        </Routes>
      </Router>
    </AntdApp>
  );
};

export default App;
