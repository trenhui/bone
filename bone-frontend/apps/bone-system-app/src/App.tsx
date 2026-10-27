import React from 'react';
import { App as AntdApp } from 'antd';
import { HashRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import SystemConfig from './pages/SystemConfig';
import MonitorAlert from './pages/MonitorAlert';
import LogManagement from './pages/LogManagement';
import SystemDeployment from './pages/SystemDeployment';
import DictManagement from './pages/DictManagement';
import ScheduleTaskManagement from './pages/ScheduleTaskManagement';
import WorkflowDesigner from './pages/WorkflowDesigner';
import AppErrorBoundary from './components/AppErrorBoundary';
import Authorized from './components/Authorized';
import { BonePermissionCodes } from './auth/permission';
import { i18n } from '@bone/shared-utils';
import './App.css';

/** 控制台通用页面守卫：sys:console:read（全局配置 / 告警 / 日志 / 部署 / 字典 / 调度）。 */
const ConsolePage: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Authorized required={BonePermissionCodes.SYS_CONSOLE_READ}>{children}</Authorized>
);

const App: React.FC = () => {
  return (
    // ★ 显式注入 shared-utils 初始化的 i18n 实例：dev 预构建下 react-i18next chunk 内嵌了
    //   自己的 i18next 拷贝，useTranslation 默认走的实例没有语言包（t() 裸返 key）；
    //   Provider 注入是 react-i18next 的官方解法，让组件树与本应用 init 的单例对齐。
    <I18nextProvider i18n={i18n}>
      <AntdApp>
        {/* v7_startTransition / v7_relativeSplatPath：提前 opt-in React Router v7 行为，
            消除每次挂载必打的 2 条 future flag 警告（2026-10-02 UI 巡检实测）。
            两者均为 v7 的向后兼容默认值，语义不变。 */}
        <Router future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <AppErrorBoundary>
            {/* 权限守卫下沉到**每个路由**：原先整棵 <Routes> 共用一个 sys:console:read，
                导致「需要另一枚权限码的新页面」要么被迫复用 console 码（授权面放大），
                要么得为它破例——见工作流设计器（workflow:definitions:read）。
                逐路由包装后每个页面只要求自己那枚码，既有页面语义不变。 */}
            <Routes>
              <Route path="/config" element={<ConsolePage><SystemConfig /></ConsolePage>} />
              <Route path="/alerts" element={<ConsolePage><MonitorAlert /></ConsolePage>} />
              <Route path="/logs" element={<ConsolePage><LogManagement /></ConsolePage>} />
              <Route path="/k8s" element={<ConsolePage><SystemDeployment /></ConsolePage>} />
              <Route path="/dict" element={<ConsolePage><DictManagement /></ConsolePage>} />
              <Route path="/schedule" element={<ConsolePage><ScheduleTaskManagement /></ConsolePage>} />
              <Route
                path="/workflow-designer"
                element={
                  <Authorized required={BonePermissionCodes.WORKFLOW_DEFINITIONS_READ}>
                    <WorkflowDesigner />
                  </Authorized>
                }
              />
              <Route path="/" element={<Navigate to="/config" replace />} />
            </Routes>
          </AppErrorBoundary>
        </Router>
      </AntdApp>
    </I18nextProvider>
  );
};

export default App;
