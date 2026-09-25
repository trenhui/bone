import React from 'react';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { App as AntdApp } from 'antd';
import AccountManagement from './pages/AccountManagement';
import ApplicationManagement from './pages/ApplicationManagement';
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

  // 平台级路由守卫（详设 §2.9）：租户管理 / 权限目录仅平台管理员（tenantId=0）可访问；
  // 租户管理员访问时自动回到本租户功能首页，后端 @PreAuthorize 权限码同时兜底。
  const PlatformRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const token = localStorage.getItem('token');
    if (!token) {
      return <Navigate to="/login" replace />;
    }
    const tenantId = localStorage.getItem('tenantId') || '0';
    if (tenantId !== '0') {
      return <Navigate to="/accounts" replace />;
    }
    return <>{children}</>;
  };

  return (
    <AntdApp>
      <Router>
        <Routes>
          <Route path="/login" element={<Auth />} />
          <Route path="/accounts" element={<ProtectedRoute><AccountManagement /></ProtectedRoute>} />
          {/* 应用管理：应用属租户域资源（平台/租户管理员均可管理本租户应用），仅登录守卫 */}
          <Route path="/apps" element={<ProtectedRoute><ApplicationManagement /></ProtectedRoute>} />
          <Route path="/roles" element={<ProtectedRoute><RoleManagement /></ProtectedRoute>} />
          <Route path="/tenants" element={<PlatformRoute><TenantManagement /></PlatformRoute>} />
          <Route path="/permissions" element={<PlatformRoute><PermissionManagement /></PlatformRoute>} />
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
