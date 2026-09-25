import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import ModelingWorkspace from './pages/ModelingWorkspace';
import ModuleManagement from './pages/ModuleManagement';
import EntityManagement from './pages/EntityManagement';
import EntityDetail from './pages/EntityDetail';
import FieldManagement from './pages/FieldManagement';
import RelationManagement from './pages/RelationManagement';
import RuntimeDataManagement from './pages/RuntimeDataManagement';
import './App.css';

function App(): JSX.Element {
  return (
    <AntdApp>
      <Router>
        <Routes>
          {/* 应用 → 模块 → 领域建模（企业级架构）；应用 CRUD 管理在 bone-iam-app，此处仅消费侧入口 */}
          <Route path="/apps" element={<ModelingWorkspace />} />
          <Route path="/apps/:appId/modules" element={<ModuleManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/entities" element={<EntityManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/entities/:id" element={<EntityDetail />} />
          <Route path="/apps/:appId/modules/:moduleId/entities/:id/data" element={<RuntimeDataManagement />} />
          <Route path="/apps/:appId/modules/:moduleId/relations" element={<RelationManagement />} />
          {/* 兼容旧路径 */}
          <Route path="/entities" element={<EntityManagement />} />
          <Route path="/entities/:id" element={<EntityDetail />} />
          <Route path="/entities/:id/data" element={<RuntimeDataManagement />} />
          <Route path="/fields" element={<FieldManagement />} />
          <Route path="/relations" element={<RelationManagement />} />
          <Route path="/runtime" element={<RuntimeDataManagement />} />
          <Route path="/" element={<Navigate to="/apps" replace />} />
        </Routes>
      </Router>
    </AntdApp>
  );
}

export default App;
