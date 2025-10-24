import * as React from 'react';
import * as ReactDOM from 'react-dom/client';
import { createMicroApp } from '@bone/core/micro-fe-runtime';
import App from './App';
import './index.css';

// 扩展Window接口
declare global {
  interface Window {
    __MICRO_APP_ENVIRONMENT__?: boolean;
  }
}

// 定义微应用配置类型
interface MicroAppConfig {
  name: string;
  version: string;
  mountPoint: string;
  routes: Array<{ path: string; name: string }>;
  exposes: {
    showNotification: (message: string, type: 'info' | 'success' | 'error') => boolean;
    getData: () => Promise<{ app: string; data: string }>;
  };
}

// 定义生命周期属性类型
interface LifecycleProps {
  mountPoint?: string;
  [key: string]: any;
}

// 微前端应用配置
const microAppConfig: MicroAppConfig = {
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
    showNotification: (message: string, type: 'info' | 'success' | 'error'): boolean => {
      console.log(`[Sub-App-1] Notification: ${message} (${type})`);
      return true;
    },
    getData: async (): Promise<{ app: string; data: string }> => {
      return { app: 'sub-app-1', data: 'Hello from Sub-App-1' };
    }
  }
};

// 创建微应用实例
const microApp = createMicroApp(microAppConfig);

// 定义微前端生命周期钩子
export const bootstrap = (): Promise<void> => {
  console.log('[Sub-App-1] Bootstrap');
  return Promise.resolve();
};

export const mount = (props: LifecycleProps): Promise<void> => {
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
  const root = ReactDOM.createRoot(container as HTMLElement);
  root.render(
    React.createElement(React.StrictMode, null,
      React.createElement(App as React.ComponentType<LifecycleProps>, props)
    )
  );
  
  return Promise.resolve();
};

export const unmount = (props: LifecycleProps): Promise<void> => {
  console.log('[Sub-App-1] Unmount', props);
  
  const container = document.querySelector(props.mountPoint || microAppConfig.mountPoint);
  if (container) {
    // 对于新版React DOM API，我们移除挂载点的内容
    container.innerHTML = '';
  }
  
  return Promise.resolve();
};

export const update = (props: LifecycleProps): Promise<void> => {
  console.log('[Sub-App-1] Update', props);
  // 处理应用更新逻辑
  return Promise.resolve();
};

// 独立运行模式 - 直接挂载到 #root
if (!window.__MICRO_APP_ENVIRONMENT__) {
  mount({ mountPoint: '#root' });
}