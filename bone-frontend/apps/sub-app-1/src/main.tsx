import React from 'react';
import ReactDOM from 'react-dom/client';
import { createMicroApp } from '@bone/core/micro-fe-runtime';
import App from './App';
import './index.css';

// 微前端应用配置
const microAppConfig = {
  name: 'sub-app-1',
  version: '1.0.0',
  mountPoint: '#sub-app-1-container',
  routes: [
    { path: '/', name: 'Home' },
    { path: '/features', name: 'Features' },
    { path: '/settings', name: 'Settings' }
  ],
  // 暴露给主应用的方法
  exposes: {
    showNotification: (message: string, type: 'info' | 'success' | 'error') => {
      console.log(`[Sub-App-1] Notification: ${message} (${type})`);
      return true;
    },
    getData: async () => {
      return { app: 'sub-app-1', data: 'Hello from Sub-App-1' };
    }
  }
};

// 创建微应用实例
const microApp = createMicroApp(microAppConfig);

// 定义微前端生命周期钩子
export const bootstrap = () => {
  console.log('[Sub-App-1] Bootstrap');
  return Promise.resolve();
};

export const mount = (props: any) => {
  console.log('[Sub-App-1] Mount', props);
  
  // 创建挂载点
  const container = document.querySelector(props.mountPoint || microAppConfig.mountPoint);
  if (!container) {
    console.error('[Sub-App-1] Mount point not found');
    return Promise.reject(new Error('Mount point not found'));
  }
  
  // 确保容器为空
  container.innerHTML = '';
  
  // 渲染应用
  const root = ReactDOM.createRoot(container);
  root.render(
    <React.StrictMode>
      <App {...props} />
    </React.StrictMode>
  );
  
  return Promise.resolve();
};

export const unmount = (props: any) => {
  console.log('[Sub-App-1] Unmount', props);
  
  const container = document.querySelector(props.mountPoint || microAppConfig.mountPoint);
  if (container) {
    ReactDOM.unmountComponentAtNode(container);
  }
  
  return Promise.resolve();
};

export const update = (props: any) => {
  console.log('[Sub-App-1] Update', props);
  // 处理应用更新逻辑
  return Promise.resolve();
};

// 独立运行模式 - 直接挂载到 #root
if (!window.__MICRO_APP_ENVIRONMENT__) {
  mount({ mountPoint: '#root' });
}