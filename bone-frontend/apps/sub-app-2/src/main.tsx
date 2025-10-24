import React from 'react';
import ReactDOM from 'react-dom/client';
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
    refreshData: () => Promise<{ success: boolean; timestamp: number }>;
    getAnalytics: () => Promise<{
      app: string;
      metrics: {
        activeUsers: number;
        pageViews: number;
        averageSession: number;
      };
    }>;
  };
}

// 定义生命周期属性类型
interface LifecycleProps {
  mountPoint?: string;
  [key: string]: any;
}

// 微前端应用配置
const microAppConfig: MicroAppConfig = {
  name: 'sub-app-2',
  version: '1.0.0',
  mountPoint: '#sub-app-2-container',
  routes: [
    { path: '/', name: 'Dashboard' },
    { path: '/analytics', name: 'Analytics' },
    { path: '/settings', name: 'Settings' }
  ],
  // 暴露给主应用的方法
  exposes: {
    refreshData: async (): Promise<{ success: boolean; timestamp: number }> => {
      console.log('[Sub-App-2] Data refreshed');
      return { success: true, timestamp: Date.now() };
    },
    getAnalytics: async () => {
      return {
        app: 'sub-app-2',
        metrics: {
          activeUsers: 125,
          pageViews: 1024,
          averageSession: 3.5
        }
      };
    }
  }
};

// 创建微应用实例
const microApp = createMicroApp(microAppConfig);

// 定义微前端生命周期钩子
export const bootstrap = (): Promise<void> => {
  console.log('[Sub-App-2] Bootstrap');
  return Promise.resolve();
};

export const mount = (props: LifecycleProps): Promise<void> => {
  console.log('[Sub-App-2] Mount', props);
  
  // 创建挂载点
  const container = document.querySelector(props.mountPoint || microAppConfig.mountPoint);
  if (!container) {
    console.error('[Sub-App-2] Mount point not found');
    return Promise.reject(new Error('Mount point not found'));
  }
  
  // 确保容器为空
  container.innerHTML = '';
  
  // 渲染应用
  const root = ReactDOM.createRoot(container);
  root.render(
    React.createElement(React.StrictMode, null,
      React.createElement(App, props)
    )
  );
  
  return Promise.resolve();
};

export const unmount = (props: LifecycleProps): Promise<void> => {
  console.log('[Sub-App-2] Unmount', props);
  
  const container = document.querySelector(props.mountPoint || microAppConfig.mountPoint);
  if (container) {
    ReactDOM.unmountComponentAtNode(container);
  }
  
  return Promise.resolve();
};

export const update = (props: LifecycleProps): Promise<void> => {
  console.log('[Sub-App-2] Update', props);
  // 处理应用更新逻辑
  return Promise.resolve();
};

// 独立运行模式 - 直接挂载到 #root
if (!window.__MICRO_APP_ENVIRONMENT__) {
  mount({ mountPoint: '#root' });
}