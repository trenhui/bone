import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import GeneratorLayout from './layout/GeneratorLayout';
import DataSourceManagement from './pages/DataSourceManagement';
import CodeGeneration from './pages/CodeGeneration';
import TemplateManagement from './pages/TemplateManagement';
import GenerationHistory from './pages/GenerationHistory';

function App(): JSX.Element {
  return (
    <AntdApp>
      {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
          消除每次挂载必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
          两者均为 v7 的向后兼容默认值，语义不变。 */}
      <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route element={<GeneratorLayout />}>
            <Route path="/datasources" element={<DataSourceManagement />} />
            <Route path="/generate" element={<CodeGeneration />} />
            <Route path="/templates" element={<TemplateManagement />} />
            <Route path="/history" element={<GenerationHistory />} />
            <Route path="/" element={<Navigate to="/datasources" replace />} />
          </Route>
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
