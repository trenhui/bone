// 微前端开发工具包使用示例

import { createMicroAppDevKit } from './microAppDevKit';

// 1. 初始化开发工具包
const devKit = createMicroAppDevKit('extension-studio-ui', {
  mockMode: true,
  mockDataPath: '/src/mock-data'
});

// 在应用启动时初始化
function initializeApp() {
  // 初始化微前端开发环境
  devKit.initialize();
  
  console.log('应用已启动，微前端开发环境已准备就绪');
}

// 2. 在组件中使用微前端通信
function useMicroCommunication() {
  // 监听来自主应用的消息
  const handleMainAppMessage = (data) => {
    console.log('收到主应用消息:', data);
    // 处理消息...
  };

  // 模拟微前端环境中的消息监听
  if (window.__POWERED_BY_WUJIE__ || window.__MICRO_APP_ENVIRONMENT__) {
    // 真实微前端环境
    if (window.$wujie?.bus) {
      window.$wujie.bus.$on('main:message', handleMainAppMessage);
    } else if (window.eventCenterForAppVite) {
      window.eventCenterForAppVite.addDataListener('main:message', handleMainAppMessage);
    }
  } else if (window.__MICRO_DEV_KIT__) {
    // 开发环境中的模拟监听
    window.__MICRO_DEV_KIT__.sendMessage('main:register', {
      appName: 'extension-studio-ui'
    });
  }

  // 发送消息到主应用
  const sendToMainApp = (data) => {
    if (window.$wujie?.bus) {
      window.$wujie.bus.$emit('app:message', data);
    } else if (window.eventCenterForAppVite) {
      window.eventCenterForAppVite.dispatch('main', data);
    } else if (window.__MICRO_DEV_KIT__) {
      window.__MICRO_DEV_KIT__.sendMessage('main', data);
    }
  };

  return {
    sendToMainApp
  };
}

// 3. 使用全局状态
function useGlobalState() {
  // 获取全局状态
  const getGlobalState = () => {
    if (window.__GLOBAL_STATE__) {
      return window.__GLOBAL_STATE__.getAll();
    }
    return {};
  };

  // 设置全局状态
  const setGlobalState = (key, value) => {
    if (window.__GLOBAL_STATE__) {
      window.__GLOBAL_STATE__.set(key, value);
    } else if (window.__MICRO_DEV_KIT__) {
      window.__MICRO_DEV_KIT__.setGlobalState(key, value);
    }
  };

  return {
    getGlobalState,
    setGlobalState
  };
}

// 4. 配置自定义Mock数据
function configureMockData() {
  // 设置自定义API响应
  devKit.setMockData('/api/extensions', 'GET', [
    { 
      id: '1', 
      name: '支付扩展', 
      description: '支持多种支付方式的扩展',
      version: '1.0.0',
      enabled: true,
      plugins: [
        { id: 'p1', name: '微信支付', version: '1.0.0' },
        { id: 'p2', name: '支付宝', version: '1.0.0' }
      ]
    },
    { 
      id: '2', 
      name: '通知扩展', 
      description: '多渠道消息通知',
      version: '1.0.0',
      enabled: true,
      plugins: [
        { id: 'p3', name: '短信通知', version: '1.0.0' },
        { id: 'p4', name: '邮件通知', version: '1.0.0' }
      ]
    }
  ]);

  // 配置错误响应
  devKit.setMockData('/api/error-test', 'GET', {
    error: '模拟错误响应',
    code: 500,
    message: '这是一个模拟的服务器错误'
  });
}

// 5. 切换Mock模式的示例
function toggleMockMode() {
  if (window.__MICRO_DEV_KIT__) {
    window.__MICRO_DEV_KIT__.toggleMockMode();
  }
}

// 6. 在应用卸载时清理
function cleanup() {
  devKit.dispose();
}

// 导出示例函数，供实际应用使用
export {
  initializeApp,
  useMicroCommunication,
  useGlobalState,
  configureMockData,
  toggleMockMode,
  cleanup
};