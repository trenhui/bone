import React, { createContext, useContext } from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import SupplierManagement from './pages/SupplierManagement';
import PurchaseConsole from './pages/PurchaseConsole';
import FulfillmentConsole from './pages/FulfillmentConsole';
import ContractReviewDisclosure from './pages/ContractReviewDisclosure';
import ContractHitlConsole from './pages/ContractHitlConsole';
import AiReadiness from './pages/AiReadiness';
import AdvisoryConsole from './pages/AdvisoryConsole';
import HumanReviewConsole from './pages/HumanReviewConsole';
import GovernanceStatus from './pages/GovernanceStatus';
import SourcingConsole from './pages/SourcingConsole';
import SupplierOnboarding from './pages/SupplierOnboarding';
import SupplierRisk from './pages/SupplierRisk';

type MessageApi = ReturnType<typeof AntdApp.useApp>['message'];

const MessageContext = createContext<MessageApi | null>(null);

/** 在组件中获取 antd 动态 message API（与 masterdata-app 同构，供迁入的供应商页使用）。 */
export function useMessage(): MessageApi {
  const api = useContext(MessageContext);
  const fallback = AntdApp.useApp().message;
  // 无 Provider 时回退到 antd 的 App.useApp()，使组件可在测试里独立挂载
  return api ?? fallback;
}

const AppContent: React.FC = () => {
  const { message: messageApi } = AntdApp.useApp();
  return (
    <MessageContext.Provider value={messageApi}>
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/transactions" element={<PurchaseConsole />} />
          <Route path="/suppliers" element={<SupplierManagement />} />
          <Route path="/fulfillment" element={<FulfillmentConsole />} />
          <Route path="/transactions/fulfillment" element={<Navigate to="/fulfillment" replace />} />
          <Route path="/contract-review" element={<ContractReviewDisclosure />} />
          <Route path="/contract-hitl" element={<ContractHitlConsole />} />
          <Route path="/ai-readiness" element={<AiReadiness />} />
          <Route path="/advisory" element={<AdvisoryConsole />} />
          <Route path="/human-review" element={<HumanReviewConsole />} />
          <Route path="/governance" element={<GovernanceStatus />} />
          <Route path="/sourcing" element={<SourcingConsole />} />
          <Route path="/supplier-onboarding" element={<SupplierOnboarding />} />
          <Route path="/supplier-risk" element={<SupplierRisk />} />
          <Route path="/" element={<Navigate to="/transactions" replace />} />
        </Routes>
      </Router>
    </MessageContext.Provider>
  );
};

const App: React.FC = () => (
  <AntdApp>
    <AppContent />
  </AntdApp>
);

export default App;
