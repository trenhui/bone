import React, { createContext, useContext } from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { EntityScopeProvider } from './context/EntityScopeContext';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import QualityRuleManagement from './pages/QualityRuleManagement';
import RecordManagement from './pages/RecordManagement';
import QualityResult from './pages/QualityResult';
import TemplateManagement from './pages/TemplateManagement';
import CategoryManagement from './pages/CategoryManagement';
import GovernanceBoard from './pages/GovernanceBoard';
import QualityIssueBoard from './pages/QualityIssueBoard';
import ReferenceDataManagement from './pages/ReferenceDataManagement';
import DomainWorkbench from './pages/DomainWorkbench';

type MessageApi = ReturnType<typeof AntdApp.useApp>['message'];

// MessageContext：提供 antd 动态 message API，替代静态 message 调用
const MessageContext = createContext<MessageApi | null>(null);

/** 在组件中获取 antd 动态 message API，替代 `import { message } from 'antd'` */
export function useMessage(): MessageApi {
  const api = useContext(MessageContext);
  if (!api) {
    throw new Error('useMessage 必须在 MessageContext.Provider 内使用');
  }
  return api;
}

const AppContent: React.FC = () => {
  const { message: messageApi } = AntdApp.useApp();

  return (
    <MessageContext.Provider value={messageApi}>
      {/* 全局「当前主数据模型」作用域：一次选择，全站共享（分类 / 治理 / 工单 / 字段 / 记录 / 工作台） */}
      <EntityScopeProvider>
        {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
            消除每次挂载必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
            两者均为 v7 的向后兼容默认值，语义不变。 */}
        <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
          {/* 主数据域视图 */}
          <Route path="/workbench" element={<DomainWorkbench />} />
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/fields" element={<FieldManagement />} />
          <Route path="/records" element={<RecordManagement />} />
          {/* 治理 */}
          <Route path="/rules" element={<QualityRuleManagement />} />
          <Route path="/quality-results" element={<QualityResult />} />
          <Route path="/quality-issues" element={<QualityIssueBoard />} />
          <Route path="/categories" element={<CategoryManagement />} />
          <Route path="/governance" element={<GovernanceBoard />} />
          {/* 平台能力 */}
          <Route path="/templates" element={<TemplateManagement />} />
          <Route path="/reference-sets" element={<ReferenceDataManagement />} />
          <Route path="/" element={<Navigate to="/workbench" replace />} />
          </Routes>
        </Router>
      </EntityScopeProvider>
    </MessageContext.Provider>
  );
};

const App: React.FC = () => {
  return (
    <AntdApp>
      <AppContent />
    </AntdApp>
  );
};

export default App;
