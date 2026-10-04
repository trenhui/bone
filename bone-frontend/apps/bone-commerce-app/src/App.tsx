import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/Layout';
import { OrderManagement } from './pages/OrderManagement';
import { PaymentManagement } from './pages/PaymentManagement';
import './App.css';

export const App: React.FC = () => {
  return (
    <AntdApp>
      {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
          消除每次挂载必打的 2 条 future flag 警告（与其他子应用保持一致）。 */}
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AppLayout>
          <div className="app-container">
            <Routes>
              <Route path="/orders" element={<OrderManagement />} />
              <Route path="/payments" element={<PaymentManagement />} />
              <Route path="/" element={<OrderManagement />} />
            </Routes>
          </div>
        </AppLayout>
      </Router>
    </AntdApp>
  );
};
