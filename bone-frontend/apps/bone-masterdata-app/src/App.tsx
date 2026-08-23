import React, { createContext, useContext } from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import EntityManagement from './pages/EntityManagement';
import FieldManagement from './pages/FieldManagement';
import QualityRuleManagement from './pages/QualityRuleManagement';
import RecordManagement from './pages/RecordManagement';
import QualityResult from './pages/QualityResult';

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
      <Router>
        <Routes>
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/fields" element={<FieldManagement />} />
          <Route path="/rules" element={<QualityRuleManagement />} />
          <Route path="/records" element={<RecordManagement />} />
          <Route path="/quality-results" element={<QualityResult />} />
          <Route path="/" element={<Navigate to="/entities" replace />} />
        </Routes>
      </Router>
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
