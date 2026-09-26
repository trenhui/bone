import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate, useParams } from 'react-router-dom';
import ModelingWorkspace from './pages/ModelingWorkspace';
import TemplateWizard from './pages/TemplateWizard';
import ModuleManagement from './pages/ModuleManagement';
import EntityManagement from './pages/EntityManagement';
import EntityDetail from './pages/EntityDetail';
import RuntimeDataManagement from './pages/RuntimeDataManagement';
import './App.css';

/** 旧独立页（字段/关系）已并入实体详情 Tabs（2b §6-4），旧路由 302 兼容 */
const ToEntities: React.FC = () => {
  const { appId, moduleId } = useParams<{ appId?: string; moduleId?: string }>();
  return <Navigate to={appId && moduleId ? `/apps/${appId}/modules/${moduleId}/entities` : '/entities'} replace />;
};

function App(): JSX.Element {
  return (
    <AntdApp>
      <Router>
        <Routes>
          {/* 应用 → 模块 → 领域建模（企业级架构）；应用 CRUD 管理在 bone-iam-app，此处仅消费侧入口 */}
          <Route path="/apps" element={<ModelingWorkspace />} />
          <Route path="/template-wizard" element={<TemplateWizard />} />
          <Route path="/apps/:appId/modules" element={<ModuleManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/entities" element={<EntityManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/entities/:id" element={<EntityDetail />} />
          <Route path="/apps/:appId/modules/:moduleId/entities/:id/data" element={<RuntimeDataManagement />} />
          {/* 兼容旧路径 */}
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/entities/:id" element={<EntityDetail />} />
          <Route path="/entities/:id/data" element={<RuntimeDataManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/relations" element={<ToEntities />} />
          <Route path="/fields" element={<ToEntities />} />
          <Route path="/fields/:entityId" element={<EntityDetail />} />
          <Route path="/relations" element={<ToEntities />} />
          <Route path="/runtime" element={<RuntimeDataManagement />} />
          <Route path="/" element={<Navigate to="/apps" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
