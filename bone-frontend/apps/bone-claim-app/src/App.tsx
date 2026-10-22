import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ClaimSubmitPage } from './pages/ClaimSubmitPage';
import { ReviewWorkbenchPage } from './pages/ReviewWorkbenchPage';
import { ClaimProgressPage } from './pages/ClaimProgressPage';
import './App.css';

/**
 * synapClaim 智能理赔微应用路由。
 *
 * - `/submit` 员工端报案（RQ-MVP-001~003）
 * - `/progress` 员工端进度追踪（PRD §7.2「进度追踪」）
 * - `/review` 核赔工作台（RQ-MVP-004~005）
 * - `/` 默认进工作台（核赔员是主要用户；员工端入口由 OA/企微侧直达 /submit）
 */
export const App: React.FC = () => {
  return (
    <AntdApp>
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <div className="app-container">
          <Routes>
            <Route path="/submit" element={<ClaimSubmitPage />} />
            <Route path="/progress" element={<ClaimProgressPage />} />
            <Route path="/review" element={<ReviewWorkbenchPage />} />
            <Route path="/" element={<Navigate to="/review" replace />} />
          </Routes>
        </div>
      </Router>
    </AntdApp>
  );
};
