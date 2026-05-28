import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import { setupAxiosAuthInterceptor } from './auth/axiosAuth';
import './index.css';

setupAxiosAuthInterceptor();

// 初始化性能监控（模拟）
const performanceMonitor = {
  init: () => {
    console.log('Performance monitor initialized');
  }
};
performanceMonitor.init();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);