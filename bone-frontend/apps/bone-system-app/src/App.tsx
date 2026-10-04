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
import AppErrorBoundary from './components/AppErrorBoundary';
import Authorized from './components/Authorized';
import { BonePermissionCodes } from './auth/permission';
import { i18n } from '@bone/shared-utils';
import './App.css';

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
            <Authorized required={BonePermissionCodes.SYS_CONSOLE_READ}>
              <Routes>
                <Route path="/config" element={<SystemConfig />} />
                <Route path="/alerts" element={<MonitorAlert />} />
                <Route path="/logs" element={<LogManagement />} />
                <Route path="/k8s" element={<SystemDeployment />} />
                <Route path="/dict" element={<DictManagement />} />
                <Route path="/schedule" element={<ScheduleTaskManagement />} />
                <Route path="/" element={<Navigate to="/config" replace />} />
              </Routes>
            </Authorized>
          </AppErrorBoundary>
        </Router>
      </AntdApp>
    </I18nextProvider>
  );
};

export default App;
