import React from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';
import { ThemeProvider } from '@bone/ui/styled-system';
import { lightTheme } from '@bone/ui/design-system';
import { initializeTheme } from '@bone/ui/design-system';
import { getPerformanceMonitor } from '@bone/core/performance-monitor';
import { getApplicationRegistry } from '@bone/core/micro-fe-runtime';

// 初始化主题
initializeTheme('light');

// 初始化性能监控
const performanceMonitor = getPerformanceMonitor({
  sampleRate: 0.1,
  enableRealTimeReport: false
});

// 注册全局性能监控对象
(window as any).__BONE_PERFORMANCE__ = {
  recordAppInit: (duration: number) => {
    console.log('App initialized in', duration, 'ms');
  }
};

// 标记为Bone微前端环境
(window as any).__BONE_MICRO_FRONTEND__ = true;

// 提供微应用注册方法
(window as any).registerMicroApp = (appConfig: any) => {
  const registry = getApplicationRegistry();
  if (registry && typeof registry.register === 'function') {
    registry.register({
      name: appConfig.name,
      entry: window.location.origin,
      activeRule: `/app/${appConfig.name}`,
      lifecycle: appConfig
    });
    console.log(`Micro app ${appConfig.name} registered`);
  }
};

// 渲染应用
const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element not found');
}

const root = createRoot(container);

// 性能标记
if (window.performance && window.performance.mark) {
  window.performance.mark('app-render-start');
}

root.render(
  <React.StrictMode>
    <ThemeProvider theme={lightTheme}>
      <App />
    </ThemeProvider>
  </React.StrictMode>
);

// 记录渲染完成时间
if (window.performance && window.performance.mark) {
  window.performance.mark('app-render-end');
  window.performance.measure('app-render-time', 'app-render-start', 'app-render-end');
  
  const measure = window.performance.getEntriesByName('app-render-time')[0];
  if (measure) {
    console.log(`App rendered in ${measure.duration.toFixed(2)}ms`);
  }
}