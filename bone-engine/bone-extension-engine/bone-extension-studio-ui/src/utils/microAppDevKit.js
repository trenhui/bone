import axios from 'axios';

/**
 * 微应用开发工具包 - 用于在独立开发环境中模拟微前端环境
 */
export class MicroAppDevKit {
  constructor(appName, options = {}) {
    this.appName = appName;
    this.mockMode = options.mockMode ?? true;
    this.mockDataPath = options.mockDataPath || '/mock-data';
    this.mockEventBus = new MockEventBus();
    this.mockGlobalState = new MockGlobalState();
    this.isInitialized = false;
    this.originalFetch = window.fetch;
    this.originalAxios = axios.create();
  }

  /**
   * 初始化开发环境
   */
  initialize() {
    if (this.isInitialized) return;

    // 检测是否在真实微前端环境中
    const isInRealMicroEnv = !!window.__POWERED_BY_WUJIE__ || !!window.__MICRO_APP_ENVIRONMENT__;

    if (!isInRealMicroEnv && this.mockMode) {
      // 模拟微前端环境
      this.mockMicroEnv();
      
      // 拦截API请求
      this.interceptApiRequests();
      
      // 设置开发工具面板
      this.setupDevToolsPanel();
      
      console.log(`[DevKit] 微前端模拟环境已初始化: ${this.appName}`);
    }

    this.isInitialized = true;
  }

  /**
   * 模拟微前端环境
   */
  mockMicroEnv() {
    // 模拟无界框架环境变量
    window.__POWERED_BY_WUJIE__ = true;
    window.__WUJIE_APPNAME__ = this.appName;
    
    // 模拟micro-app环境变量
    window.__MICRO_APP_ENVIRONMENT__ = true;
    window.__MICRO_APP_NAME__ = this.appName;
    
    // 模拟无界bus
    window.$wujie = {
      bus: {
        $on: (event, callback) => this.mockEventBus.on(event, callback),
        $emit: (event, ...args) => this.mockEventBus.emit(event, args),
        $off: (event, callback) => this.mockEventBus.off(event, callback)
      }
    };
    
    // 模拟micro-app eventCenter
    window.eventCenterForAppVite = {
      addDataListener: (key, callback) => this.mockEventBus.on(key, callback),
      removeDataListener: (key, callback) => this.mockEventBus.off(key, callback),
      dispatch: (target, data) => this.mockEventBus.emit(target, data),
      clearDataListener: () => this.mockEventBus.clear()
    };
    
    // 模拟全局状态
    window.__GLOBAL_STATE__ = this.mockGlobalState;
  }

  /**
   * 拦截API请求
   */
  interceptApiRequests() {
    // 拦截fetch请求
    window.fetch = async (url, options) => {
      // 检查是否有对应的mock数据
      if (this.mockMode && this.hasMockData(url, options?.method || 'GET')) {
        console.log(`[Mock API] ${options?.method || 'GET'} ${url}`);
        return this.getMockResponse(url, options?.method || 'GET');
      }
      
      // 否则使用原始fetch
      return this.originalFetch(url, options);
    };
    
    // 拦截axios请求
    axios.interceptors.request.use(async (config) => {
      if (this.mockMode && this.hasMockData(config.url, config.method || 'get')) {
        console.log(`[Mock API] ${config.method || 'get'} ${config.url}`);
        const response = await this.getMockResponse(config.url, config.method || 'get');
        return Promise.resolve({ data: await response.json() });
      }
      return config;
    });
  }

  /**
   * 检查是否有mock数据
   */
  hasMockData(url, method) {
    // 简单实现，实际可以从配置文件或API获取
    return this.getMockData(url, method) !== null;
  }

  /**
   * 获取mock数据
   */
  getMockData(url, method) {
    // 这里可以实现从文件或内存中获取mock数据
    // 简单示例实现
    const mockDataMap = this.getMockDataMap();
    const key = `${method.toLowerCase()}:${url}`;
    return mockDataMap[key] || null;
  }

  /**
   * 获取mock数据映射表
   */
  getMockDataMap() {
    // 这里应该从配置或文件中加载mock数据
    // 示例数据
    return {
      'get:/api/extensions': [
        { id: '1', name: '支付扩展', description: '支付功能扩展', enabled: true },
        { id: '2', name: '通知扩展', description: '通知功能扩展', enabled: true }
      ],
      'get:/api/plugins': [
        { id: '1', name: '微信支付插件', extensionId: '1', version: '1.0.0' },
        { id: '2', name: '支付宝支付插件', extensionId: '1', version: '1.0.0' }
      ]
    };
  }

  /**
   * 获取mock响应
   */
  async getMockResponse(url, method) {
    const data = this.getMockData(url, method);
    return new Response(JSON.stringify(data), {
      status: 200,
      headers: {
        'Content-Type': 'application/json'
      }
    });
  }

  /**
   * 设置开发工具面板
   */
  setupDevToolsPanel() {
    if (process.env.NODE_ENV !== 'development') return;

    // 开发环境下，添加开发工具按钮到控制台
    console.log('========================================');
    console.log('🔧 微前端开发工具已启动');
    console.log(`🚀 应用名称: ${this.appName}`);
    console.log('📋 可用命令:');
    console.log('  - window.__MICRO_DEV_KIT__.sendMessage(target, data) - 发送消息');
    console.log('  - window.__MICRO_DEV_KIT__.setGlobalState(key, value) - 设置全局状态');
    console.log('  - window.__MICRO_DEV_KIT__.toggleMockMode() - 切换Mock模式');
    console.log('========================================');

    // 暴露开发工具到window
    window.__MICRO_DEV_KIT__ = {
      sendMessage: (target, data) => this.sendMessage(target, data),
      setGlobalState: (key, value) => this.setGlobalState(key, value),
      toggleMockMode: () => this.toggleMockMode(),
      getAppName: () => this.appName
    };
  }

  /**
   * 发送消息
   */
  sendMessage(target, data) {
    console.log(`[DevKit] 发送消息到 ${target}:`, data);
    this.mockEventBus.emit(target, data);
  }

  /**
   * 设置全局状态
   */
  setGlobalState(key, value) {
    this.mockGlobalState.set(key, value);
    console.log(`[DevKit] 设置全局状态 ${key}:`, value);
    // 通知应用状态更新
    this.mockEventBus.emit('global_state_updated', { key, value });
  }

  /**
   * 切换Mock模式
   */
  toggleMockMode() {
    this.mockMode = !this.mockMode;
    console.log(`[DevKit] Mock模式已${this.mockMode ? '启用' : '禁用'}`);
    
    if (this.mockMode) {
      this.interceptApiRequests();
    } else {
      // 恢复原始fetch
      window.fetch = this.originalFetch;
      // 清除axios拦截器
      axios.interceptors.request.clear();
    }
  }

  /**
   * 设置自定义mock数据
   */
  setMockData(url, method, data) {
    const mockDataMap = this.getMockDataMap();
    const key = `${method.toLowerCase()}:${url}`;
    mockDataMap[key] = data;
    console.log(`[DevKit] 已设置mock数据: ${key}`);
  }

  /**
   * 清理开发环境
   */
  dispose() {
    // 恢复原始fetch
    window.fetch = this.originalFetch;
    
    // 清除事件总线
    this.mockEventBus.clear();
    
    // 清理模拟的全局变量
    delete window.__POWERED_BY_WUJIE__;
    delete window.__WUJIE_APPNAME__;
    delete window.__MICRO_APP_ENVIRONMENT__;
    delete window.__MICRO_APP_NAME__;
    delete window.$wujie;
    delete window.eventCenterForAppVite;
    delete window.__GLOBAL_STATE__;
    delete window.__MICRO_DEV_KIT__;
    
    this.isInitialized = false;
    console.log('[DevKit] 开发环境已清理');
  }
}

/**
 * 模拟事件总线
 */
class MockEventBus {
  constructor() {
    this.events = new Map();
  }

  on(event, callback) {
    if (!this.events.has(event)) {
      this.events.set(event, new Set());
    }
    this.events.get(event).add(callback);
  }

  emit(event, data) {
    if (this.events.has(event)) {
      this.events.get(event).forEach(callback => {
        try {
          callback(data);
        } catch (error) {
          console.error(`[EventBus] 事件处理器错误:`, error);
        }
      });
    }
  }

  off(event, callback) {
    if (this.events.has(event)) {
      if (callback) {
        this.events.get(event).delete(callback);
      } else {
        this.events.delete(event);
      }
    }
  }

  clear() {
    this.events.clear();
  }
}

/**
 * 模拟全局状态管理
 */
class MockGlobalState {
  constructor() {
    this.state = {};
  }

  set(key, value) {
    this.state[key] = value;
  }

  get(key) {
    return this.state[key];
  }

  getAll() {
    return { ...this.state };
  }

  delete(key) {
    delete this.state[key];
  }

  clear() {
    this.state = {};
  }
}

/**
 * 创建微应用开发工具实例
 */
export function createMicroAppDevKit(appName, options) {
  return new MicroAppDevKit(appName, options);
}