/**
 * bone-extension-studio-ui 微应用入口文件
 * 提供微应用的生命周期管理、资源管理、错误处理和通信功能
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './index.css';
import { ResourceManager } from './utils/resourceManager';

// 导入微应用开发工具（仅在开发环境使用）
let createMicroAppDevKit;
try {
  // 动态导入以避免在生产环境中加载不必要的依赖
  createMicroAppDevKit = require('./utils/microAppDevKit').createMicroAppDevKit;
} catch (err) {
  console.debug('MicroAppDevKit not available in production environment');
}

// 微应用配置常量
const APP_CONFIG = {
  ID: 'bone-extension-studio-ui',
  VERSION: '1.0.0',
  DEFAULT_TIMEOUT: 30000,
  BOOTSTRAP_TIMEOUT: 10000,
  MOUNT_TIMEOUT: 10000,
  UNMOUNT_TIMEOUT: 5000,
};

// 检测当前运行环境
const ENVIRONMENT = {
  IS_MICRO_APP: window.__POWERED_BY_WUJIE__ || 
                window.__MICRO_APP_ENVIRONMENT__ || 
                window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__ ||
                window.__MICRO_APP__,
  IS_DEVELOPMENT: process.env.NODE_ENV === 'development',
  NODE_ENV: process.env.NODE_ENV || 'development',
};

// 创建微应用开发工具实例（仅在开发环境使用）
let microAppDevKit;
if (ENVIRONMENT.IS_DEVELOPMENT && createMicroAppDevKit) {
  microAppDevKit = createMicroAppDevKit(APP_CONFIG.ID, {
    mockMode: true,
    simulateMicroEnv: !ENVIRONMENT.IS_MICRO_APP
  });
  microAppDevKit.initialize();
}

/**
 * MicroFrontendCommunicator
 * 提供与主应用及其他微应用之间的统一通信接口
 * 支持多框架适配、消息队列、错误处理和自动重试机制
 */
class MicroFrontendCommunicator {
  /**
   * 构造函数
   * @param {Object} options 配置选项
   */
  constructor(options = {}) {
    this.appId = APP_CONFIG.ID;
    this.listeners = new Map();
    this.messageQueue = [];
    this.isReady = false;
    this.messageIdCounter = 0;
    this.retryInterval = options.retryInterval || 1000;
    this.maxRetryAttempts = options.maxRetryAttempts || 3;
    this.loggerEnabled = options.loggerEnabled ?? true;
    
    // 初始化通信环境
    this._initializeCommunication();
  }

  /**
   * 初始化通信环境
   * @private
   */
  _initializeCommunication() {
    try {
      this._setupMessageHandlers();
      this.isReady = true;
      this._processMessageQueue();
      
      if (this.loggerEnabled) {
        console.log(`[${this.appId}] 通信管理器初始化完成`);
      }
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 初始化通信环境失败:`, error);
      }
      // 即使初始化失败，也设置为就绪以避免队列阻塞
      this.isReady = true;
    }
  }

  /**
   * 发送消息到目标应用
   * @param {string} target 目标应用ID
   * @param {string} eventName 事件名称
   * @param {Object} data 消息数据
   * @param {Object} options 发送选项
   * @returns {boolean} 发送是否成功
   */
  sendMessage(target, eventName, data, options = {}) {
    try {
      // 生成唯一消息ID
      const messageId = this._generateMessageId(eventName);
      
      // 标准化消息格式
      const message = {
        source: this.appId,
        target,
        event: eventName,
        data,
        timestamp: Date.now(),
        messageId
      };
      
      // 如果通信未就绪，将消息加入队列
      if (!this.isReady) {
        if (this.loggerEnabled) {
          console.log(`[${this.appId}] 通信通道未就绪，将消息加入队列: ${eventName}`);
        }
        this.messageQueue.push({ target, eventName, data, message });
        return true;
      }
      
      // 构建事件完整名称
      const fullEventName = `${target}:${eventName}`;
      
      // 适配不同的微前端框架
      if (this._sendMessageToMicroFrontend(fullEventName, data)) {
        if (this.loggerEnabled && options.debug) {
          console.log(`[${this.appId}] 消息发送成功: ${fullEventName}`, data);
        }
        return true;
      }
      
      // 发送失败时加入队列
      this.messageQueue.push({ target, eventName, data });
      return false;
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 发送消息失败:`, error);
      }
      // 发送失败时加入队列，稍后重试
      this.messageQueue.push({ target, eventName, data });
      return false;
    }
  }
  
  /**
   * 向微前端框架发送消息
   * @private
   * @param {string} fullEventName 完整事件名称
   * @param {Object} data 消息数据
   * @returns {boolean} 发送是否成功
   */
  _sendMessageToMicroFrontend(fullEventName, data) {
    try {
      if (window.eventCenterForAppVite) {
        // micro-app框架
        window.eventCenterForAppVite.dispatch(fullEventName, data);
        return true;
      } else if (window.$wujie) {
        // wujie框架
        window.$wujie.bus.$emit(fullEventName, data);
        return true;
      } else if (window.__POWERED_BY_QIANKUN__) {
        // qiankun框架
        window.dispatchEvent(new CustomEvent(fullEventName, { detail: data }));
        return true;
      } else {
        // 通用自定义事件
        window.dispatchEvent(new CustomEvent(fullEventName, { detail: data }));
        return true;
      }
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 通过微前端框架发送消息失败:`, error);
      }
      return false;
    }
  }

  /**
   * 处理消息队列
   * @private
   */
  _processMessageQueue() {
    if (!this.isReady || this.messageQueue.length === 0) {
      return;
    }
    
    const queueCopy = [...this.messageQueue];
    this.messageQueue = [];
    
    let successCount = 0;
    let failureCount = 0;
    
    queueCopy.forEach(({ target, eventName, data }) => {
      const fullEventName = `${target}:${eventName}`;
      
      if (this._sendMessageToMicroFrontend(fullEventName, data)) {
        successCount++;
        if (this.loggerEnabled && ENVIRONMENT.IS_DEVELOPMENT) {
          console.log(`[${this.appId}] 队列消息发送成功: ${eventName}`);
        }
      } else {
        failureCount++;
        // 重试失败的消息重新加入队列
        this.messageQueue.push({ target, eventName, data });
      }
    });
    
    // 记录队列处理结果
    if (this.loggerEnabled && (successCount > 0 || failureCount > 0)) {
      console.log(`[${this.appId}] 消息队列处理结果: 成功 ${successCount}, 失败 ${failureCount}`);
    }
    
    // 如果还有未发送的消息，设置定时器重试
    if (this.messageQueue.length > 0) {
      setTimeout(() => this._processMessageQueue(), this.retryInterval);
    }
  }

  /**
   * 监听来自目标应用的消息
   * @param {string} source 消息源应用ID
   * @param {string} eventName 事件名称
   * @param {Function} callback 回调函数
   * @param {Object} options 监听选项
   * @returns {Function} 取消监听的函数
   */
  onMessage(source, eventName, callback, options = {}) {
    if (typeof callback !== 'function') {
      throw new Error('Callback must be a function');
    }
    
    try {
      const fullEventName = `${source}:${eventName}`;
      
      // 包装回调函数以支持一次性监听
      const actualCallback = options.once 
        ? this._createOneTimeCallback(source, eventName, callback)
        : callback;
      
      // 适配不同的微前端框架
      const unsubscribe = this._setupFrameworkListener(fullEventName, actualCallback);
      
      // 保存监听器引用
      this._saveListener(source, eventName, actualCallback, unsubscribe);
      
      return unsubscribe;
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 注册消息监听器失败:`, error);
      }
      return () => {};
    }
  }
  
  /**
   * 创建一次性回调函数
   * @private
   */
  _createOneTimeCallback(source, eventName, originalCallback) {
    return (data) => {
      originalCallback(data);
      // 使用setTimeout避免在回调执行期间修改监听器列表
      setTimeout(() => {
        this._findAndRemoveListener(source, eventName, originalCallback);
      }, 0);
    };
  }
  
  /**
   * 设置微前端框架的监听器
   * @private
   */
  _setupFrameworkListener(fullEventName, callback) {
    try {
      if (window.eventCenterForAppVite) {
        // micro-app框架
        window.eventCenterForAppVite.addDataListener(fullEventName, callback);
        return () => window.eventCenterForAppVite.removeDataListener(fullEventName, callback);
      } else if (window.$wujie) {
        // wujie框架
        window.$wujie.bus.$on(fullEventName, callback);
        return () => window.$wujie.bus.$off(fullEventName, callback);
      } else if (window.__POWERED_BY_QIANKUN__) {
        // qiankun框架
        const handler = (e) => callback(e.detail);
        window.addEventListener(fullEventName, handler);
        return () => window.removeEventListener(fullEventName, handler);
      } else {
        // 通用自定义事件
        const handler = (e) => callback(e.detail);
        window.addEventListener(fullEventName, handler);
        return () => window.removeEventListener(fullEventName, handler);
      }
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 设置框架监听器失败:`, error);
      }
      return () => {};
    }
  }
  
  /**
   * 保存监听器引用
   * @private
   */
  _saveListener(source, eventName, callback, unsubscribe) {
    const listenerKey = `${source}:${eventName}`;
    
    if (!this.listeners.has(listenerKey)) {
      this.listeners.set(listenerKey, []);
    }
    
    this.listeners.get(listenerKey).push({
      callback,
      unsubscribe
    });
  }
  
  /**
   * 查找并移除监听器
   * @private
   */
  _findAndRemoveListener(source, eventName, callback) {
    try {
      const listenerKey = `${source}:${eventName}`;
      
      if (this.listeners.has(listenerKey)) {
        const listeners = this.listeners.get(listenerKey);
        
        // 查找匹配的监听器
        for (let i = 0; i < listeners.length; i++) {
          if (listeners[i].callback === callback) {
            // 执行取消订阅
            listeners[i].unsubscribe();
            // 从列表中移除
            listeners.splice(i, 1);
            break;
          }
        }
        
        // 如果没有监听器了，删除该键
        if (listeners.length === 0) {
          this.listeners.delete(listenerKey);
        }
      }
    } catch (error) {
      if (this.loggerEnabled) {
        console.error(`[${this.appId}] 移除监听器失败:`, error);
      }
    }
  }
  
  /**
   * 获取监听器数量
   * @param {string} source 可选，指定消息源
   * @param {string} eventName 可选，指定事件名称
   * @returns {number} 监听器数量
   */
  getListenerCount(source, eventName) {
    // 获取特定事件的监听器数量
    if (source && eventName) {
      const listenerKey = `${source}:${eventName}`;
      return this.listeners.has(listenerKey) 
        ? this.listeners.get(listenerKey).length 
        : 0;
    }
    
    // 获取所有监听器的总数
    return Array.from(this.listeners.values()).reduce(
      (total, listeners) => total + listeners.length,
      0
    );
  }

  /**
   * 清理所有监听器
   * @returns {number} 清理的监听器数量
   */
  clearListeners() {
    let clearedCount = 0;
    
    this.listeners.forEach((listeners) => {
      listeners.forEach(item => {
        try {
          item.unsubscribe();
          clearedCount++;
        } catch (error) {
          if (this.loggerEnabled) {
            console.error(`[${this.appId}] 取消订阅监听器失败:`, error);
          }
        }
      });
    });
    
    this.listeners.clear();
    
    if (this.loggerEnabled && clearedCount > 0) {
      console.log(`[${this.appId}] 已清理 ${clearedCount} 个监听器`);
    }
    
    return clearedCount;
  }

  /**
   * 设置消息处理器
   * @private
   */
  _setupMessageHandlers() {
    // 初始化全局错误处理器
    this._setupGlobalErrorHandlers();
  }
  
  /**
   * 设置全局错误处理器
   * @private
   */
  _setupGlobalErrorHandlers() {
    if (typeof window === 'undefined') {
      return;
    }
    
    // 全局错误处理器
    const handleGlobalError = (event) => {
      console.error(`[${this.appId}] 捕获到全局错误:`, event.error);
      this.sendMessage('main', 'error', {
        type: 'global',
        error: event.error?.message || '未知错误',
        stack: event.error?.stack,
        source: event.filename,
        lineno: event.lineno,
        colno: event.colno,
        timestamp: Date.now()
      });
    };
    
    // Promise拒绝处理器
    const handlePromiseRejection = (event) => {
      console.error(`[${this.appId}] 捕获到未处理的Promise拒绝:`, event.reason);
      this.sendMessage('main', 'error', {
        type: 'promise',
        error: event.reason?.message || '未知Promise拒绝',
        stack: event.reason?.stack,
        timestamp: Date.now()
      });
    };
    
    // 添加事件监听器
    window.addEventListener('error', handleGlobalError);
    window.addEventListener('unhandledrejection', handlePromiseRejection);
  }
  
  /**
   * 生成唯一消息ID
   * @private
   */
  _generateMessageId(eventName) {
    return `${this.appId}_${eventName}_${this.messageIdCounter++}_${Date.now()}`;
  }
}

/**
 * 安全执行函数 - 带超时保护和错误处理
 * 包装异步函数执行，提供超时控制和错误规范化处理
 * 适用于微应用生命周期方法等关键操作的安全执行
 * 
 * @param {Function} fn 要执行的函数
 * @param {number} timeout 超时时间（毫秒）
 * @param {Object} context 执行上下文信息
 * @param {string} context.operation 操作名称，用于日志和错误追踪
 * @param {string} context.stage 执行阶段，如bootstrap、mount、unmount等
 * @returns {Promise<any>} 执行结果
 * @throws {Error} 执行失败或超时错误
 */
async function safeExecute(fn, timeout = 5000, context = {}) {
  // 验证输入参数
  if (typeof fn !== 'function') {
    throw new TypeError('First argument must be a function');
  }
  
  // 创建超时Promise
  const timeoutPromise = new Promise((_, reject) => {
    setTimeout(() => {
      const timeoutError = new Error(`操作执行超时：${timeout}毫秒`);
      timeoutError.code = 'EXECUTION_TIMEOUT';
      reject(timeoutError);
    }, timeout);
  });
  
  try {
    // 执行函数并设置超时保护
    return await Promise.race([fn(), timeoutPromise]);
  } catch (error) {
    console.error(`[${APP_CONFIG.ID}] 安全执行失败:`, error, '上下文:', context);
    
    // 标准化错误信息
    const errorInfo = {
      type: context.type || 'execution',
      error: error.message,
      operation: context.operation || fn.name || 'unknown',
      code: error.code || 'UNKNOWN_ERROR',
      timestamp: Date.now(),
      timeout: error.code === 'EXECUTION_TIMEOUT'
    };
    
    // 尝试通过全局错误处理器上报
    if (window.__bone_error_handler__) {
      try {
        window.__bone_error_handler__.handleError(error, {
          ...context,
          ...errorInfo
        });
      } catch (handlerError) {
        console.error(`[${APP_CONFIG.ID}] 错误处理器执行失败:`, handlerError);
      }
    }
    
    // 抛出错误以允许上层处理
    throw error;
  }
}

// 初始化通信工具
const communicator = new MicroFrontendCommunicator({
  retryInterval: 1000,
  loggerEnabled: ENVIRONMENT.IS_DEVELOPMENT
});

// 导出通信工具到全局作用域，方便其他组件使用
window.__bone_communicator__ = communicator;

/**
 * 初始化资源跟踪器
 * 用于跟踪和管理全局资源，如定时器和事件监听器
 * @param {ResourceManager} resourceManager 资源管理器实例
 * @returns {Function} 用于恢复原始方法的函数
 */
function initResourceTracker(resourceManager) {
  // 创建资源存储对象
  window.__bone_resources = {
    timers: [],
    intervals: [],
    eventListeners: [],
    objects: []
  };
  
  // 保存原始方法引用
  const originalMethods = {
    setTimeout: window.setTimeout,
    setInterval: window.setInterval,
    clearTimeout: window.clearTimeout,
    clearInterval: window.clearInterval
  };
  
  /**
   * 重写setTimeout函数以实现自动跟踪和清理
   */
  window.setTimeout = function(...args) {
    const timerId = originalMethods.setTimeout.apply(this, args);
    window.__bone_resources.timers.push(timerId);
    
    // 注册到资源管理器
    if (resourceManager && typeof resourceManager.addResource === 'function') {
      resourceManager.addResource(() => {
        originalMethods.clearTimeout(timerId);
      }, { category: 'timeout', priority: 1 });
    }
    
    return timerId;
  };
  
  /**
   * 重写setInterval函数以实现自动跟踪和清理
   */
  window.setInterval = function(...args) {
    const intervalId = originalMethods.setInterval.apply(this, args);
    window.__bone_resources.intervals.push(intervalId);
    
    // 注册到资源管理器
    if (resourceManager && typeof resourceManager.addResource === 'function') {
      resourceManager.addResource(() => {
        originalMethods.clearInterval(intervalId);
      }, { category: 'interval', priority: 1 });
    }
    
    return intervalId;
  };
  
  /**
   * 重写clearTimeout函数以更新跟踪列表
   */
  window.clearTimeout = function(timerId) {
    const index = window.__bone_resources.timers.indexOf(timerId);
    if (index > -1) {
      window.__bone_resources.timers.splice(index, 1);
    }
    return originalMethods.clearTimeout.apply(this, arguments);
  };
  
  /**
   * 重写clearInterval函数以更新跟踪列表
   */
  window.clearInterval = function(intervalId) {
    const index = window.__bone_resources.intervals.indexOf(intervalId);
    if (index > -1) {
      window.__bone_resources.intervals.splice(index, 1);
    }
    return originalMethods.clearInterval.apply(this, arguments);
  };
  
  /**
   * 扩展方法 - 跟踪事件监听器
   * @param {EventTarget} target 事件目标
   * @param {string} eventName 事件名称
   * @param {Function} handler 事件处理函数
   * @param {Object|boolean} options 事件选项
   * @returns {Function} 取消订阅的函数
   */
  window.__bone_resources.trackEventListener = function(target, eventName, handler, options) {
    // 验证参数
    if (!target || typeof target.addEventListener !== 'function') {
      console.warn(`[${APP_CONFIG.ID}] 无效的事件目标`);
      return () => {};
    }
    
    target.addEventListener(eventName, handler, options);
    const listenerInfo = { target, eventName, handler, options };
    window.__bone_resources.eventListeners.push(listenerInfo);
    
    // 注册到资源管理器
    if (resourceManager && typeof resourceManager.addResource === 'function') {
      resourceManager.addResource(() => {
        try {
          target.removeEventListener(eventName, handler, options);
        } catch (error) {
          console.warn(`[${APP_CONFIG.ID}] 移除跟踪的事件监听器失败:`, error);
        }
      }, { category: 'eventListener', priority: 2 });
    }
    
    // 返回取消订阅函数
    return () => {
      try {
        target.removeEventListener(eventName, handler, options);
        const index = window.__bone_resources.eventListeners.indexOf(listenerInfo);
        if (index > -1) {
          window.__bone_resources.eventListeners.splice(index, 1);
        }
      } catch (error) {
        console.warn(`[${APP_CONFIG.ID}] 移除事件监听器失败:`, error);
      }
    };
  };
  
  /**
   * 资源清理助手函数
   */
  window.__bone_resources.cleanupAll = function() {
    try {
      // 清理所有定时器
      window.__bone_resources.timers.forEach(id => {
        try {
          originalMethods.clearTimeout(id);
        } catch (e) {}
      });
      
      // 清理所有间隔定时器
      window.__bone_resources.intervals.forEach(id => {
        try {
          originalMethods.clearInterval(id);
        } catch (e) {}
      });
      
      // 清理所有事件监听器
      window.__bone_resources.eventListeners.forEach(info => {
        try {
          info.target.removeEventListener(info.eventName, info.handler, info.options);
        } catch (e) {}
      });
      
      // 清空所有数组
      window.__bone_resources.timers = [];
      window.__bone_resources.intervals = [];
      window.__bone_resources.eventListeners = [];
      window.__bone_resources.objects = [];
      
      console.log(`[${APP_CONFIG.ID}] 资源跟踪器已清理所有资源`);
    } catch (error) {
      console.error(`[${APP_CONFIG.ID}] 资源清理过程中发生错误:`, error);
    }
  };
  
  // 返回恢复原始方法的函数
  return () => {
    window.setTimeout = originalMethods.setTimeout;
    window.setInterval = originalMethods.setInterval;
    window.clearTimeout = originalMethods.clearTimeout;
    window.clearInterval = originalMethods.clearInterval;
    console.log(`[${APP_CONFIG.ID}] 资源跟踪器已恢复原始方法`);
  };
}

/**
 * 错误处理器
 * 提供统一的错误分类、处理、上报和统计功能
 */
class ErrorHandler {
  /**
   * 构造函数
   * @param {Object} options 配置选项
   */
  constructor(options = {}) {
    this.reportUrl = options.reportUrl || '/api/error/report';
    this.throttleTime = options.throttleTime || 5000;
    this.lastReportTime = 0;
    this.errorCount = 0;
    this.errorCache = new Map();
    this.loggerEnabled = options.loggerEnabled ?? ENVIRONMENT.IS_DEVELOPMENT;
  }

  /**
   * 错误类型常量
   */
  static ERROR_TYPES = {
    API_ERROR: 'API_ERROR',
    NETWORK_ERROR: 'NETWORK_ERROR',
    TYPE_ERROR: 'TYPE_ERROR',
    TIMEOUT_ERROR: 'TIMEOUT_ERROR',
    UNKNOWN_ERROR: 'UNKNOWN_ERROR',
    GLOBAL_ERROR: 'GLOBAL_ERROR',
    PROMISE_ERROR: 'PROMISE_ERROR'
  };

  /**
   * 错误严重程度常量
   */
  static SEVERITY_LEVELS = {
    CRITICAL: 'critical',
    HIGH: 'high',
    MEDIUM: 'medium',
    LOW: 'low'
  };

  /**
   * 分类错误
   * @param {Error} error 错误对象
   * @returns {Object} 分类后的错误信息
   */
  classifyError(error) {
    // 验证参数
    if (!error) {
      return this._createBaseErrorInfo('未知错误对象', ErrorHandler.ERROR_TYPES.UNKNOWN_ERROR);
    }

    // 基于错误特征进行分类
    if (error.response) {
      // 可能是Axios等HTTP客户端库的错误
      return {
        type: ErrorHandler.ERROR_TYPES.API_ERROR,
        severity: error.response.status >= 500 
          ? ErrorHandler.SEVERITY_LEVELS.HIGH 
          : ErrorHandler.SEVERITY_LEVELS.MEDIUM,
        status: error.response.status,
        message: error.message || `API错误: ${error.response.status}`,
        stack: error.stack
      };
    } else if (error.request) {
      // 网络请求错误（请求已发出但没有收到响应）
      return {
        type: ErrorHandler.ERROR_TYPES.NETWORK_ERROR,
        severity: ErrorHandler.SEVERITY_LEVELS.HIGH,
        message: error.message || '网络请求失败',
        stack: error.stack
      };
    } else if (error.code === 'EXECUTION_TIMEOUT' || error.message?.includes('timeout')) {
      // 超时错误
      return {
        type: ErrorHandler.ERROR_TYPES.TIMEOUT_ERROR,
        severity: ErrorHandler.SEVERITY_LEVELS.MEDIUM,
        message: error.message || '操作超时',
        stack: error.stack
      };
    } else if (error.name === 'TypeError') {
      // 类型错误
      return {
        type: ErrorHandler.ERROR_TYPES.TYPE_ERROR,
        severity: ErrorHandler.SEVERITY_LEVELS.HIGH,
        message: error.message,
        stack: error.stack
      };
    } else {
      // 通用错误
      return this._createBaseErrorInfo(error.message, ErrorHandler.ERROR_TYPES.UNKNOWN_ERROR);
    }
  }

  /**
   * 处理错误
   * @param {Error} error 错误对象
   * @param {Object} context 错误上下文
   * @returns {Object} 分类后的错误信息
   */
  handleError(error, context = {}) {
    if (this.loggerEnabled) {
      console.error(`[${APP_CONFIG.ID}] 错误处理:`, error, '上下文:', context);
    }

    // 分类错误
    const classifiedError = this.classifyError(error);
    
    // 生成错误ID用于去重
    const errorId = this._generateErrorId(classifiedError);
    
    // 更新错误统计
    this._updateErrorStatistics(errorId);

    // 节流上报错误
    this._throttledErrorReport(classifiedError, context);

    return classifiedError;
  }

  /**
   * 上报错误
   * @param {Object} errorInfo 错误信息对象
   * @param {Object} context 错误上下文
   */
  async reportError(errorInfo, context = {}) {
    try {
      // 构建上报数据
      const reportData = {
        error: errorInfo,
        context,
        appId: APP_CONFIG.ID,
        timestamp: Date.now(),
        url: typeof window !== 'undefined' ? window.location.href : '',
        userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
        version: APP_CONFIG.VERSION
      };

      // 通过通信管理器发送错误信息到主应用
      if (communicator && typeof communicator.sendMessage === 'function') {
        communicator.sendMessage('main', 'error:report', reportData);
      }
      
      if (this.loggerEnabled) {
        console.log(`[${APP_CONFIG.ID}] 错误已上报:`, errorInfo.type);
      }
    } catch (reportError) {
      console.error(`[${APP_CONFIG.ID}] 错误上报失败:`, reportError);
    }
  }

  /**
   * 获取错误统计
   * @returns {Object} 错误统计信息
   */
  getErrorStatistics() {
    return {
      totalErrors: this.errorCount,
      uniqueErrors: this.errorCache.size,
      errorDetails: Array.from(this.errorCache.entries()).map(([id, info]) => ({
        id,
        count: info.count,
        firstTime: info.firstTime,
        lastTime: info.lastTime,
        lastTimeHuman: new Date(info.lastTime).toLocaleString()
      }))
    };
  }

  /**
   * 重置错误统计
   */
  resetErrorStatistics() {
    this.errorCount = 0;
    this.errorCache.clear();
    this.lastReportTime = 0;
    
    if (this.loggerEnabled) {
      console.log(`[${APP_CONFIG.ID}] 错误统计已重置`);
    }
  }

  /**
   * 创建基本错误信息
   * @private
   */
  _createBaseErrorInfo(message, type) {
    return {
      type,
      severity: ErrorHandler.SEVERITY_LEVELS.MEDIUM,
      message: message || '未知错误',
      stack: ''
    };
  }

  /**
   * 生成错误ID
   * @private
   */
  _generateErrorId(errorInfo) {
    // 使用错误类型和消息前100个字符作为唯一标识
    const messagePrefix = errorInfo.message ? errorInfo.message.substring(0, 100) : 'unknown';
    return `${errorInfo.type}:${messagePrefix}`;
  }

  /**
   * 更新错误统计
   * @private
   */
  _updateErrorStatistics(errorId) {
    this.errorCount++;
    
    const now = Date.now();
    
    if (this.errorCache.has(errorId)) {
      const cached = this.errorCache.get(errorId);
      cached.count++;
      cached.lastTime = now;
    } else {
      this.errorCache.set(errorId, {
        count: 1,
        firstTime: now,
        lastTime: now
      });
    }
  }

  /**
   * 节流错误上报
   * @private
   */
  _throttledErrorReport(errorInfo, context) {
    const now = Date.now();
    
    // 应用节流控制，避免频繁上报
    if (now - this.lastReportTime > this.throttleTime) {
      this.reportError(errorInfo, context).catch(reportError => {
        console.error(`[${APP_CONFIG.ID}] 异步错误上报失败:`, reportError);
      });
      this.lastReportTime = now;
    }
  }
}

/**
 * 资源管理器
 * 负责跟踪、管理和清理应用生命周期中的各种资源，防止内存泄漏
 * 已从utils/resourceManager.js导入，避免代码重复
 */
// 创建全局单例
const errorHandler = new ErrorHandler();
const resourceManager = new ResourceManager();

/**
 * 清理应用资源
 * 负责清理微应用运行过程中创建的各类资源，防止内存泄漏
 * 按照预定义的步骤进行资源清理，支持分步清理和资源统计
 * @param {Object} options 清理选项
 * @param {boolean} [options.fullCleanup=false] 是否完全清理（包括全局引用）
 * @param {string} [options.category] 可选的资源类别过滤
 * @returns {Object} 清理结果统计
 */
const cleanupAppResources = (options = {}) => {
  const loggerPrefix = `[${APP_CONFIG.ID}][ResourceManager]`;
  console.log(`${loggerPrefix} 开始资源清理流程...`);
  
  // 初始化清理结果对象
  const cleanupResults = {
    success: true,
    stats: {},
    errors: [],
    resourceBreakdown: {},
    cleanupTime: 0,
    timestamp: Date.now()
  };
  
  const startTime = Date.now();
  
  // 统计清理前的资源状态
  const preCleanupStats = resourceManager.getResourceStatistics();
  cleanupResults.stats = preCleanupStats;
  
  console.log(`${loggerPrefix} 清理前资源统计:`, {
    totalResources: preCleanupStats.total,
    categoryCount: Object.keys(preCleanupStats.categories || {}).length
  });
  
  try {
    // 步骤1: 清理通信器资源
    console.log(`${loggerPrefix} 步骤1: 清理通信器资源`);
    if (window.__bone_communicator__) {
      try {
        window.__bone_communicator__.clearListeners();
        console.log(`${loggerPrefix} 已清理消息监听器`);
        cleanupResults.resourceBreakdown.messageListeners = 'cleared';
      } catch (error) {
        console.error(`${loggerPrefix} 清理消息监听器失败:`, error);
        cleanupResults.errors.push({
          type: 'messageListeners', 
          error: error.message,
          timestamp: Date.now()
        });
      }
    }
    
    // 步骤2: 清理资源跟踪器中的资源
    console.log(`${loggerPrefix} 步骤2: 清理资源跟踪器中的资源`);
    if (window.__bone_resources) {
      // 先清理事件监听器
      try {
        let clearedListeners = 0;
        let failedListeners = 0;
        
        if (window.__bone_resources.eventListeners && Array.isArray(window.__bone_resources.eventListeners)) {
          window.__bone_resources.eventListeners.forEach(({ event, handler, target = window, options: listenerOptions }) => {
            try {
              target.removeEventListener(event, handler, listenerOptions);
              clearedListeners++;
            } catch (e) {
              console.warn(`${loggerPrefix} 移除事件监听器失败:`, e);
              results.errors.push({
                type: 'eventListener', 
                error: e.message,
                event,
                timestamp: Date.now()
              });
              failedListeners++;
            }
          });
          window.__bone_resources.eventListeners = [];
        }
        
        console.log(`${loggerPrefix} 事件监听器清理完成: 成功=${clearedListeners}, 失败=${failedListeners}`);
        cleanupResults.resourceBreakdown.eventListeners = {
          cleared: clearedListeners,
          failed: failedListeners
        };
      } catch (error) {
        console.error(`${loggerPrefix} 清理事件监听器组失败:`, error);
        cleanupResults.errors.push({
          type: 'eventListenerGroup', 
          error: error.message,
          timestamp: Date.now()
        });
      }
      
      // 再清理定时器和间隔定时器
      try {
        let clearedTimers = 0;
        let clearedIntervals = 0;
        
        if (window.__bone_resources.timers && Array.isArray(window.__bone_resources.timers)) {
          window.__bone_resources.timers.forEach(timerId => {
            try {
              clearTimeout(timerId);
              clearedTimers++;
            } catch (e) {
              console.warn(`${loggerPrefix} 清理定时器失败:`, e);
            }
          });
          window.__bone_resources.timers = [];
        }
        
        if (window.__bone_resources.intervals && Array.isArray(window.__bone_resources.intervals)) {
          window.__bone_resources.intervals.forEach(intervalId => {
            try {
              clearInterval(intervalId);
              clearedIntervals++;
            } catch (e) {
              console.warn(`${loggerPrefix} 清理间隔定时器失败:`, e);
            }
          });
          window.__bone_resources.intervals = [];
        }
        
        console.log(`${loggerPrefix} 定时器和间隔定时器清理完成: 定时器=${clearedTimers}, 间隔定时器=${clearedIntervals}`);
        cleanupResults.resourceBreakdown.timers = {
          cleared: clearedTimers,
          clearedIntervals: clearedIntervals
        };
      } catch (error) {
        console.error(`${loggerPrefix} 清理定时器失败:`, error);
        cleanupResults.errors.push({
          type: 'timers', 
          error: error.message,
          timestamp: Date.now()
        });
      }
      
      // 最后清理对象引用
      try {
        let clearedObjects = 0;
        
        if (window.__bone_resources.objects && Array.isArray(window.__bone_resources.objects)) {
          window.__bone_resources.objects.forEach(obj => {
            try {
              if (obj && typeof obj === 'object') {
                for (const key in obj) {
                  if (Object.prototype.hasOwnProperty.call(obj, key)) {
                    delete obj[key];
                  }
                }
                clearedObjects++;
              }
            } catch (e) {
              console.warn(`${loggerPrefix} 清理对象失败:`, e);
            }
          });
          window.__bone_resources.objects = [];
        }
        
        console.log(`${loggerPrefix} 对象引用清理完成: 成功=${clearedObjects}`);
        cleanupResults.resourceBreakdown.objects = {
          cleared: clearedObjects
        };
      } catch (error) {
        console.error(`${loggerPrefix} 清理对象失败:`, error);
        cleanupResults.errors.push({
          type: 'objects', 
          error: error.message,
          timestamp: Date.now()
        });
      }
    }
    
    // 步骤3: 清理资源管理器中的资源
    console.log(`${loggerPrefix} 步骤3: 清理资源管理器中的资源`);
    try {
      const cleanupResult = resourceManager.clearResources({
        category: options.category,
        clearCategories: true
      });
      cleanupResults.clearedResources = cleanupResult;
      cleanupResults.resourceBreakdown.customResources = cleanupResult;
      console.log(`${loggerPrefix} 自定义资源清理完成:`, {
        cleared: cleanupResult.clearedCount,
        errors: cleanupResult.errorCount
      });
    } catch (error) {
      console.error(`${loggerPrefix} 清理自定义资源失败:`, error);
      cleanupResults.errors.push({
        type: 'customResources', 
        error: error.message,
        timestamp: Date.now()
      });
      cleanupResults.success = false;
    }
    
    // 步骤4: 清理全局变量（如果需要完全清理）
    console.log(`${loggerPrefix} 步骤4: 全局资源清理 (${options.fullCleanup ? '已启用' : '已跳过'})`);
    if (options.fullCleanup) {
      try {
        // 停止泄漏检测
        if (resourceManager && typeof resourceManager.stopLeakDetection === 'function') {
          resourceManager.stopLeakDetection();
          console.log(`${loggerPrefix} 已停止资源泄漏检测`);
        }
        
        // 清理全局引用
        const globalRefs = [
          '__bone_communicator__',
          '__bone_error_handler__',
          '__bone_resource_manager__'
        ];
        
        let clearedRefs = 0;
        globalRefs.forEach(key => {
          try {
            if (key in window) {
              window[key] = null;
              clearedRefs++;
            }
          } catch (e) {
            console.warn(`${loggerPrefix} 清理全局引用 ${key} 失败:`, e);
          }
        });
        
        cleanupResults.resourceBreakdown.globalReferences = {
          cleared: clearedRefs,
          total: globalRefs.length
        };
        
        console.log(`${loggerPrefix} 已清理 ${clearedRefs}/${globalRefs.length} 个全局引用`);
      } catch (error) {
        console.error(`${loggerPrefix} 清理全局引用失败:`, error);
        cleanupResults.errors.push({
          type: 'globalReferences', 
          error: error.message,
          timestamp: Date.now()
        });
      }
    }
    
    // 执行自定义清理函数（如果存在）
    if (window.__bone_custom_cleanup__ && typeof window.__bone_custom_cleanup__ === 'function') {
      try {
        console.log(`${loggerPrefix} 执行自定义清理函数`);
        window.__bone_custom_cleanup__();
        cleanupResults.resourceBreakdown.customCleanup = 'executed';
      } catch (customCleanupError) {
        console.error(`${loggerPrefix} 执行自定义清理函数失败:`, customCleanupError);
        cleanupResults.errors.push({
          type: 'customCleanup',
          error: customCleanupError.message,
          timestamp: Date.now()
        });
      }
    }
    
    // 计算清理耗时
    cleanupResults.cleanupTime = Date.now() - startTime;
    
    // 通知主应用清理完成
    if (window.__bone_communicator__) {
      try {
        window.__bone_communicator__.sendMessage('main', 'app:resources:cleaned', {
          appId: APP_CONFIG.ID,
          stats: cleanupResults,
          timestamp: Date.now()
        });
      } catch (sendError) {
        console.error(`${loggerPrefix} 发送清理完成消息失败:`, sendError);
        // 不添加到错误数组，因为这是最后的通知步骤
      }
    }
    
    console.log(`${loggerPrefix} 资源清理流程完成，耗时 ${cleanupResults.cleanupTime}ms`);
    return cleanupResults;
  } catch (error) {
    console.error(`${loggerPrefix} 资源清理流程中发生错误:`, error);
    cleanupResults.success = false;
    cleanupResults.globalError = error.message;
    cleanupResults.cleanupTime = Date.now() - startTime;
    
    // 通知主应用清理失败
    if (window.__bone_communicator__) {
      try {
        window.__bone_communicator__.sendMessage('main', 'app:resources:cleanup:failed', {
          appId: APP_CONFIG.ID,
          error: error.message,
          timestamp: Date.now(),
          partialResults: cleanupResults
        });
      } catch (sendError) {
        console.error(`${loggerPrefix} 发送清理失败消息失败:`, sendError);
      }
    }
    
    return cleanupResults;
  }
};

/**
 * 微应用生命周期对象
 * 遵循微前端架构的标准生命周期方法
 */
const microApp = {
  /**
   * 应用初始化阶段
   * 用于加载资源、初始化配置、注册全局错误处理器等
   * @returns {Promise<void>} 初始化完成Promise
   */
  bootstrap: async () => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 开始初始化 (bootstrap)`);
    
    // 使用安全执行包装初始化过程
    return safeExecute(async () => {
      try {
        // 初始化资源跟踪器，传入资源管理器
        const restoreResourceTracker = initResourceTracker(resourceManager);
        
        // 注册全局错误处理器到资源管理器
        const errorListener = (event) => {
          const classifiedError = errorHandler.handleError(event.error, {
            type: ErrorHandler.ERROR_TYPES.GLOBAL_ERROR,
            source: event.filename,
            lineno: event.lineno,
            colno: event.colno,
            timestamp: Date.now()
          });
          
          if (classifiedError) {
            communicator.sendMessage('main', 'error', {
              type: 'global',
              error: classifiedError,
              source: event.filename,
              lineno: event.lineno,
              colno: event.colno,
              timestamp: Date.now()
            });
          }
        };
        
        const rejectionListener = (event) => {
          const classifiedError = errorHandler.handleError(event.reason, {
            type: ErrorHandler.ERROR_TYPES.PROMISE_ERROR,
            timestamp: Date.now()
          });
          
          if (classifiedError) {
            communicator.sendMessage('main', 'error', {
              type: 'promise',
              error: classifiedError,
              timestamp: Date.now()
            });
          }
        };
        
        window.addEventListener('error', errorListener);
        window.addEventListener('unhandledrejection', rejectionListener);
        
        // 注册到资源管理器，确保在卸载时移除
        resourceManager.addResource(() => {
          try {
            window.removeEventListener('error', errorListener);
            window.removeEventListener('unhandledrejection', rejectionListener);
            if (restoreResourceTracker) {
              restoreResourceTracker();
            }
          } catch (cleanupError) {
            console.error(`${loggerPrefix} 清理bootstrap资源失败:`, cleanupError);
          }
        }, { 
          category: ResourceManager.RESOURCE_CATEGORIES.BOOTSTRAP,
          priority: ResourceManager.PRIORITY_LEVELS.HIGH,
          description: '全局错误处理器资源'
        });
        
        // 初始化全局配置对象
        window.__boneConfig = window.__boneConfig || {
          apiBaseUrl: '/api',
          environment: ENVIRONMENT.NODE_ENV,
          timeout: 30000
        };
        
        // 初始化上下文对象
        window.__boneContext = window.__boneContext || {};
        
        // 通知主应用bootstrap完成
        communicator.sendMessage('main', 'bootstrap:completed', {
          appId: APP_CONFIG.ID,
          version: APP_CONFIG.VERSION || '1.0.0',
          timestamp: Date.now()
        });
        
        console.log(`${loggerPrefix} 初始化完成 (bootstrap)`);
        return Promise.resolve();
      } catch (error) {
        const classifiedError = errorHandler.handleError(error, { 
          stage: 'bootstrap',
          timestamp: Date.now()
        });
        
        console.error(`${loggerPrefix} 初始化失败 (bootstrap):`, error);
        
        try {
          communicator.sendMessage('main', 'bootstrap:failed', {
            appId: APP_CONFIG.ID,
            error: classifiedError,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送初始化失败消息失败:`, sendError);
        }
        
        return Promise.reject(error);
      }
    }, 10000, { operation: 'bootstrap' }); // bootstrap超时时间10秒
  },

  /**
   * 应用挂载阶段
   * @param {HTMLElement} container 容器元素
   * @param {Object} props 传递给微应用的属性
   * @returns {Promise<void>} 挂载完成Promise
   */
  mount: async (container, props) => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 开始挂载 (mount)`, { 
      containerExists: !!container,
      hasProps: !!props 
    });
    
    // 使用安全执行包装挂载过程，设置10秒超时
    return safeExecute(async () => {
      try {
        // 确保容器存在且类型正确
        if (!container || !(container instanceof HTMLElement)) {
          const error = new Error('挂载容器必须是有效的HTML元素');
          error.code = 'INVALID_CONTAINER';
          throw error;
        }

        // 保存挂载容器和props
        window.__bone_micro_container__ = container;
        window.__bone_micro_props__ = props || {};

        // 为容器添加唯一ID，用于React挂载
        if (!container.id) {
          container.id = `${APP_CONFIG.ID}-root`;
        }

        // 清理容器，确保没有残留内容
        container.innerHTML = '';

        // 创建根元素
        const rootElement = document.createElement('div');
        rootElement.id = `${APP_CONFIG.ID}-app`;
        container.appendChild(rootElement);
        
        // 注册到资源管理器，确保在卸载时移除根元素
        resourceManager.addResource(() => {
          try {
            if (container.contains(rootElement)) {
              container.removeChild(rootElement);
              console.log(`${loggerPrefix} 已移除根DOM元素: ${rootElement.id}`);
            }
          } catch (error) {
            console.error(`${loggerPrefix} 移除根DOM元素失败:`, error);
          }
        }, { 
          category: ResourceManager.RESOURCE_CATEGORIES.DOM,
          priority: ResourceManager.PRIORITY_LEVELS.HIGH,
          description: '应用根DOM元素'
        });

        // 如果在微前端环境中，通过props获取配置
        if (props) {
          // 合并配置
          if (props.config) {
            window.__boneConfig = { ...window.__boneConfig, ...props.config };
          }
          
          // 合并上下文信息
          if (props.context) {
            window.__boneContext = { ...window.__boneContext, ...props.context };
          }
        }

        // 挂载React应用
        const root = ReactDOM.createRoot(rootElement);
        
        // 保存根引用，用于后续卸载
        container.__bone_app_info = {
          root,
          app: microApp,
          mountedAt: new Date().toISOString()
        };
        
        // 保存到全局，便于访问
        window.__bone_micro_root__ = root;
        
        // 渲染应用组件，传递通信器、错误处理器和资源管理器
        root.render(
          <React.StrictMode>
            <ConfigProvider locale={zhCN}>
              <App 
                {...props} 
                communicator={communicator} 
                errorHandler={errorHandler}
                resourceManager={resourceManager}
                appConfig={APP_CONFIG}
              />
            </ConfigProvider>
          </React.StrictMode>
        );

        // 通知主应用mount完成
        communicator.sendMessage('main', 'mount:completed', {
          appId: APP_CONFIG.ID,
          containerId: container.id,
          timestamp: Date.now()
        });

        console.log(`${loggerPrefix} 挂载完成 (mount)`);
        return Promise.resolve();
      } catch (error) {
        const classifiedError = errorHandler.handleError(error, { 
          stage: 'mount',
          timestamp: Date.now()
        });
        
        console.error(`${loggerPrefix} 挂载失败 (mount):`, error);
        
        try {
          communicator.sendMessage('main', 'mount:failed', {
            appId: APP_CONFIG.ID,
            error: classifiedError,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送挂载失败消息失败:`, sendError);
        }
        
        // 即使挂载失败，也尝试清理已创建的资源
        if (container) {
          try {
            container.innerHTML = '';
            console.log(`${loggerPrefix} 已清理容器内容`);
          } catch (cleanupError) {
            console.error(`${loggerPrefix} 清理容器失败:`, cleanupError);
          }
        }
        
        return Promise.reject(error);
      }
    }, 10000, { operation: 'mount' }); // mount超时时间10秒
  },

  /**
   * 应用更新阶段
   * @param {Object} props 更新的属性
   * @returns {Promise<void>} 更新完成Promise
   */
  update: async (props) => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 开始更新 (update)`, { hasProps: !!props });
    
    // 使用安全执行包装更新过程
    return safeExecute(async () => {
      try {
        // 验证props参数
        const updateProps = props || {};
        
        // 更新全局props引用
        window.__bone_micro_props__ = { 
          ...(window.__bone_micro_props__ || {}), 
          ...updateProps 
        };
        
        // 合并配置
        if (updateProps.config) {
          window.__boneConfig = { ...window.__boneConfig, ...updateProps.config };
        }
        
        // 合并上下文信息
        if (updateProps.context) {
          window.__boneContext = { ...window.__boneContext, ...updateProps.context };
        }
        
        // 如果需要，可以触发应用内部的更新机制
        if (window.__bone_update_handler__) {
          try {
            window.__bone_update_handler__(updateProps);
            console.log(`${loggerPrefix} 已触发内部更新处理器`);
          } catch (handlerError) {
            console.error(`${loggerPrefix} 内部更新处理器错误:`, handlerError);
            // 记录错误但不中断更新流程
            errorHandler.handleError(handlerError, {
              stage: 'update',
              component: 'updateHandler',
              timestamp: Date.now()
            });
          }
        }
        
        // 通知主应用update完成
        communicator.sendMessage('main', 'update:completed', {
          appId: APP_CONFIG.ID,
          timestamp: Date.now()
        });

        console.log(`${loggerPrefix} 更新完成 (update)`);
        return Promise.resolve();
      } catch (error) {
        console.error(`${loggerPrefix} 更新失败 (update):`, error);
        
        // 分类错误
        const classifiedError = errorHandler.handleError(error, {
          stage: 'update',
          timestamp: Date.now()
        });
        
        try {
          communicator.sendMessage('main', 'update:failed', {
            appId: APP_CONFIG.ID,
            error: classifiedError,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送更新失败消息失败:`, sendError);
        }
        
        return Promise.reject(error);
      }
    }, 5000, { operation: 'update' }); // update超时时间5秒
  },

  /**
   * 应用卸载阶段
   * 负责清理DOM、React组件和各种资源，防止内存泄漏
   * 遵循微前端架构规范，确保安全卸载以避免影响主应用
   * @returns {Promise<Object>} 卸载结果对象，包含成功状态、错误统计和资源清理结果
   */
  unmount: async () => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 开始卸载 (unmount)`);
    
    // 初始化卸载结果对象
    const unmountResult = {
      success: true,
      containersUnmounted: 0,
      errorStats: {},
      cleanupResults: {},
      timestamp: Date.now(),
      duration: 0
    };
    
    const startTime = Date.now();
    
    // 使用安全执行包装卸载过程
    return safeExecute(async () => {
      try {
        // 步骤1: 清理通信监听器
        console.log(`${loggerPrefix} 步骤1: 清理通信监听器`);
        try {
          communicator.clearListeners();
          console.log(`${loggerPrefix} 已清理通信监听器`);
        } catch (commError) {
          console.error(`${loggerPrefix} 清理通信监听器失败:`, commError);
          errorHandler.handleError(commError, { 
            stage: 'unmount',
            component: 'communicator',
            timestamp: Date.now()
          });
        }
        
        // 步骤2: 查找所有可能的挂载点并卸载React应用
        console.log(`${loggerPrefix} 步骤2: 卸载React应用`);
        const containers = document.querySelectorAll(`[id*="${APP_CONFIG.ID}-root"]`);
        console.log(`${loggerPrefix} 找到 ${containers.length} 个可能的挂载容器`);
        
        containers.forEach(container => {
          if (container.__bone_app_info) {
            try {
              // 卸载React应用
              if (container.__bone_app_info.root && typeof container.__bone_app_info.root.unmount === 'function') {
                container.__bone_app_info.root.unmount();
                // 清理容器内容
                container.innerHTML = '';
                // 清理引用
                delete container.__bone_app_info;
                unmountResult.containersUnmounted++;
                console.log(`${loggerPrefix} 已卸载容器: ${container.id}`);
              }
            } catch (error) {
              console.error(`${loggerPrefix} 卸载容器 ${container.id} 错误:`, error);
              // 记录但不中断
              errorHandler.handleError(error, { 
                stage: 'unmount',
                component: 'reactUnmount',
                containerId: container.id,
                timestamp: Date.now()
              });
            }
          }
        });
        
        // 步骤3: 清理全局React根引用
        console.log(`${loggerPrefix} 步骤3: 清理全局引用`);
        window.__bone_micro_root__ = null;

        // 步骤4: 清理资源 - 不进行完全清理，保留通信器以便发送消息
        console.log(`${loggerPrefix} 步骤4: 清理资源 (不完全清理)`);
        const cleanupResults = cleanupAppResources({ fullCleanup: false });
        unmountResult.cleanupResults = cleanupResults;
        console.log(`${loggerPrefix} 资源清理结果:`, {
          success: cleanupResults.success,
          clearedResources: cleanupResults.clearedResources?.clearedCount || 0,
          errors: cleanupResults.errors.length
        });
        
        // 获取错误统计
        unmountResult.errorStats = errorHandler.getErrorStatistics();
        unmountResult.duration = Date.now() - startTime;

        // 通知主应用unmount完成，包含详细信息
        try {
          communicator.sendMessage('main', 'unmount:completed', {
            appId: APP_CONFIG.ID,
            errorStats: unmountResult.errorStats,
            cleanupResults: unmountResult.cleanupResults,
            containersUnmounted: unmountResult.containersUnmounted,
            duration: unmountResult.duration,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送卸载完成消息失败:`, sendError);
          // 不影响返回结果，因为卸载已经完成
        }

        console.log(`${loggerPrefix} 卸载完成 (unmount)，耗时 ${unmountResult.duration}ms`);
        return Promise.resolve(unmountResult);
      } catch (error) {
        unmountResult.success = false;
        unmountResult.error = error.message;
        unmountResult.duration = Date.now() - startTime;
        
        errorHandler.handleError(error, { 
          stage: 'unmount',
          timestamp: Date.now()
        });
        console.error(`${loggerPrefix} 卸载失败 (unmount):`, error);
        
        // 即使unmount失败也要继续，确保资源尽可能清理
        try {
          const cleanupResults = cleanupAppResources({ fullCleanup: false });
          unmountResult.cleanupResults = cleanupResults;
        } catch (cleanupError) {
          console.error(`${loggerPrefix} 清理资源失败:`, cleanupError);
        }
        
        // 通知主应用unmount失败
        try {
          communicator.sendMessage('main', 'unmount:failed', {
            appId: APP_CONFIG.ID,
            error: errorHandler.classifyError(error),
            cleanupAttempted: true,
            cleanupResults: unmountResult.cleanupResults,
            duration: unmountResult.duration,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送卸载失败消息失败:`, sendError);
        }
        
        // 即使失败也返回成功，确保微前端框架继续流程
        return Promise.resolve(unmountResult);
      }
    }, 5000, { operation: 'unmount' }); // unmount超时时间5秒
  },

  /**
   * 应用销毁阶段
   * 负责完全清理所有资源，包括全局引用、配置和核心管理器实例
   * 执行最彻底的清理，确保微应用完全退出且不影响主应用
   * @returns {Promise<Object>} 销毁结果对象，包含成功状态、错误统计、资源清理结果和全局变量清理情况
   */
  destroy: async () => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 开始销毁 (destroy)`);
    
    // 初始化销毁结果对象
    const destroyResult = {
      success: true,
      cleanupResults: {},
      globalVariables: {
        total: 0,
        cleared: 0,
        failed: 0
      },
      finalGlobalVariables: {
        total: 0,
        cleared: 0,
        failed: 0
      },
      errorStats: {},
      timestamp: Date.now(),
      duration: 0
    };
    
    const startTime = Date.now();
    
    // 使用安全执行包装销毁过程
    return safeExecute(async () => {
      try {
        console.log(`${loggerPrefix} 步骤1: 执行完全资源清理`);
        // 完全清理应用资源
        const cleanupResults = cleanupAppResources({ fullCleanup: true });
        destroyResult.cleanupResults = cleanupResults;
        console.log(`${loggerPrefix} 完全资源清理结果:`, {
          success: cleanupResults.success,
          errors: cleanupResults.errors.length,
          cleanupTime: cleanupResults.cleanupTime
        });
        
        console.log(`${loggerPrefix} 步骤2: 清理全局变量`);
        // 清理全局变量
        const cleanupKeys = [
          '__bone_micro_container__',
          '__bone_micro_props__',
          '__bone_micro_root__',
          '__bone_update_handler__',
          '__bone_resources',
          '__boneConfig',
          '__boneContext'
        ];
        
        destroyResult.globalVariables.total = cleanupKeys.length;
        
        cleanupKeys.forEach(key => {
          try {
            if (key in window) {
              delete window[key];
              destroyResult.globalVariables.cleared++;
            }
          } catch (e) {
            console.warn(`${loggerPrefix} 清理全局变量 ${key} 失败:`, e);
            destroyResult.globalVariables.failed++;
          }
        });
        
        console.log(`${loggerPrefix} 已清理 ${destroyResult.globalVariables.cleared}/${destroyResult.globalVariables.total} 个全局变量`);
        
        // 清理开发工具（如果存在）
        if (typeof microAppDevKit !== 'undefined') {
          try {
            console.log(`${loggerPrefix} 步骤3: 清理开发工具`);
            if (typeof microAppDevKit.dispose === 'function') {
              microAppDevKit.dispose();
              console.log(`${loggerPrefix} 已清理开发工具`);
            }
          } catch (error) {
            console.error(`${loggerPrefix} 清理开发工具错误:`, error);
          }
        }
        
        // 获取最终错误统计
        destroyResult.errorStats = errorHandler.getErrorStatistics();
        
        // 发送销毁完成消息，包含最终错误统计（在清理前发送）
        try {
          console.log(`${loggerPrefix} 步骤4: 发送销毁完成消息`);
          communicator.sendMessage('main', 'destroy:completed', {
            appId: APP_CONFIG.ID,
            errorStats: destroyResult.errorStats,
            cleanupResults: destroyResult.cleanupResults,
            globalVariables: destroyResult.globalVariables,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送销毁完成消息失败:`, sendError);
        }

        console.log(`${loggerPrefix} 步骤5: 清理核心管理器引用`);
        // 最后清理通信器、错误处理器和资源管理器的全局引用
        const finalCleanupKeys = [
          '__bone_communicator__',
          '__bone_error_handler__',
          '__bone_resource_manager__'
        ];
        
        destroyResult.finalGlobalVariables.total = finalCleanupKeys.length;
        
        // 先销毁资源管理器
        try {
          if (resourceManager && typeof resourceManager.destroy === 'function') {
            resourceManager.destroy();
            console.log(`${loggerPrefix} 已销毁资源管理器`);
          }
        } catch (destroyError) {
          console.error(`${loggerPrefix} 销毁资源管理器失败:`, destroyError);
        }
        
        // 再清理核心管理器的全局引用
        finalCleanupKeys.forEach(key => {
          try {
            if (key in window) {
              delete window[key];
              destroyResult.finalGlobalVariables.cleared++;
            }
          } catch (e) {
            console.warn(`${loggerPrefix} 最终清理全局变量 ${key} 失败:`, e);
            destroyResult.finalGlobalVariables.failed++;
          }
        });
        
        destroyResult.duration = Date.now() - startTime;
        
        console.log(`${loggerPrefix} 销毁完成 (destroy)，耗时 ${destroyResult.duration}ms，清理状态:`, {
          globalVariables: `${destroyResult.globalVariables.cleared}/${destroyResult.globalVariables.total}`,
          finalVariables: `${destroyResult.finalGlobalVariables.cleared}/${destroyResult.finalGlobalVariables.total}`
        });
        
        return Promise.resolve(destroyResult);
      } catch (error) {
        destroyResult.success = false;
        destroyResult.error = error.message;
        destroyResult.duration = Date.now() - startTime;
        
        try {
          errorHandler.handleError(error, { 
            stage: 'destroy',
            timestamp: Date.now()
          });
        } catch (errorHandlerError) {
          // 忽略错误处理器可能的错误
        }
        
        console.error(`${loggerPrefix} 销毁失败 (destroy):`, error);
        
        // 尝试发送销毁失败消息
        try {
          communicator.sendMessage('main', 'destroy:failed', {
            appId: APP_CONFIG.ID,
            error: typeof errorHandler.classifyError === 'function' ? 
              errorHandler.classifyError(error) : error.message,
            duration: destroyResult.duration,
            timestamp: Date.now()
          });
        } catch (sendError) {
          console.error(`${loggerPrefix} 发送销毁失败消息失败:`, sendError);
        }
        
        // 即使destroy失败也要继续
        return Promise.resolve(destroyResult);
      }
    }, 5000, { operation: 'destroy' }); // destroy超时时间5秒
  },
  
  /**
   * 获取应用信息
   * 提供应用的基本元信息，包括配置、状态、资源使用情况等
   * 便于监控和调试应用运行状态
   * @returns {Object} 应用元信息对象
   */
  getAppInfo: () => {
    return {
      appId: APP_CONFIG.ID,
      version: APP_CONFIG.VERSION || '1.0.0',
      environment: ENVIRONMENT.NODE_ENV,
      isMicroApp: typeof window.__POWERED_BY_WUJIE__ !== 'undefined' || 
                  typeof window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__ !== 'undefined',
      resources: typeof resourceManager.getResourceStatistics === 'function' ? 
        resourceManager.getResourceStatistics() : {},
      errors: typeof errorHandler.getErrorStatistics === 'function' ? 
        errorHandler.getErrorStatistics() : {},
      config: window.__boneConfig || {},
      mounted: !!window.__bone_micro_root__,
      timestamp: Date.now()
    };
  },
  
  /**
   * 手动触发资源清理
   * 提供给外部直接调用的清理方法，可用于主动释放资源
   * 支持选择性清理特定类别的资源
   * @param {Object} options 清理选项
   * @param {boolean} options.fullCleanup 是否完全清理
   * @param {string} options.category 可选的资源类别过滤
   * @returns {Object} 清理结果统计
   */
  cleanup: (options = {}) => {
    const loggerPrefix = `[${APP_CONFIG.ID}]`;
    console.log(`${loggerPrefix} 手动触发资源清理`, options);
    
    try {
      return cleanupAppResources(options);
    } catch (error) {
      console.error(`${loggerPrefix} 手动清理资源失败:`, error);
      return {
        success: false,
        error: error.message,
        timestamp: Date.now()
      };
    }
  }
};

// 为其他微应用提供通信工具访问
window.__bone_communicator__ = window.__bone_communicator__ || communicator;
// 提供错误处理器访问
window.__bone_error_handler__ = window.__bone_error_handler__ || errorHandler;
// 提供资源管理器访问
window.__bone_resource_manager__ = window.__bone_resource_manager__ || resourceManager;

// 增强MicroFrontendCommunicator功能
MicroFrontendCommunicator.prototype.emitGlobalEvent = function(event, data) {
  /**
   * 发送全局事件
   * @param {string} event 事件名称
   * @param {Object} data 事件数据
   * @returns {boolean} 发送是否成功
   */
  return this.sendMessage('global', event, data);
};

MicroFrontendCommunicator.prototype.onGlobalEvent = function(event, callback, options) {
  /**
   * 监听全局事件
   * @param {string} event 事件名称
   * @param {Function} callback 回调函数
   * @param {Object} options 监听选项
   * @returns {Function} 取消监听函数
   */
  return this.onMessage('global', event, callback, options);
};

// 添加一次性全局事件监听
MicroFrontendCommunicator.prototype.onceGlobalEvent = function(event, callback) {
  /**
   * 监听一次性全局事件
   * @param {string} event 事件名称
   * @param {Function} callback 回调函数
   * @returns {Function} 取消监听函数
   */
  return this.onGlobalEvent(event, callback, { once: true });
};

// 批量发送消息
MicroFrontendCommunicator.prototype.sendBatchMessages = function(messages) {
  /**
   * 批量发送消息
   * @param {Array} messages 消息数组，每项包含target、event和data
   * @returns {Array} 发送结果数组
   */
  const results = [];
  messages.forEach(({ target, event, data }) => {
    const result = this.sendMessage(target, event, data);
    results.push({
      target,
      event,
      success: result
    });
  });
  return results;
};

// 导出微应用生命周期对象
if (typeof window !== 'undefined') {
  // 兼容不同微前端框架的导出格式
  window[APP_CONFIG.ID] = microApp;
  window.bone_extension_studio_ui = microApp;
  
  // 为不同的微前端框架提供适配
  if (window.__POWERED_BY_WUJIE__) {
    // wujie微前端框架
    window.__WUJIE_MOUNT = microApp.mount;
    window.__WUJIE_UNMOUNT = microApp.unmount;
    window.__WUJIE_BOOTSTRAP = microApp.bootstrap;
  } else if (window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__) {
    // qiankun微前端框架 - 会自动检测这些导出
    // 不需要特殊处理
  } else if (window.__MICRO_APP_ENVIRONMENT__) {
    // 其他微前端框架
    // 注册生命周期
  } else if (window.__BONE_MICRO_FRONTEND__) {
    // Bone自定义微前端框架适配
    console.log(`${APP_CONFIG.ID} detected Bone micro frontend environment`);
    // 确保生命周期方法格式与Bone框架兼容
    if (typeof window.registerMicroApp === 'function') {
      console.log(`${APP_CONFIG.ID} registering with Bone micro frontend framework`);
      window.registerMicroApp({
        name: APP_CONFIG.ID,
        bootstrap: microApp.bootstrap,
        mount: microApp.mount,
        unmount: microApp.unmount,
        update: microApp.update
      });
    }
  }
}

// ES模块导出
export default microApp;
export const bootstrap = microApp.bootstrap;
export const mount = microApp.mount;
export const unmount = microApp.unmount;
export const update = microApp.update;
export const destroy = microApp.destroy;

// 独立运行模式（非微前端环境）
if (!ENVIRONMENT.IS_MICRO_APP && typeof window !== 'undefined') {
  console.log(`${APP_CONFIG.ID} running in standalone mode`);
  
  // 找到或创建根容器
  let rootContainer = document.getElementById('root');
  if (!rootContainer) {
    rootContainer = document.createElement('div');
    rootContainer.id = 'root';
    document.body.appendChild(rootContainer);
  }
  
  // 挂载应用
  microApp.bootstrap().then(() => {
    return microApp.mount(rootContainer, {
      // 模拟微前端环境的props
      config: {
        apiBaseUrl: '/api',
        environment: 'development'
      },
      context: {
        token: localStorage.getItem('bone-token') || '',
        userId: localStorage.getItem('bone-user-id') || '',
        username: localStorage.getItem('bone-username') || ''
      }
    });
  }).catch(error => {
    console.error('Failed to mount app:', error);
    
    // 显示错误信息给用户
    const errorElement = document.createElement('div');
    errorElement.style.padding = '20px';
    errorElement.style.backgroundColor = '#f5222d';
    errorElement.style.color = 'white';
    errorElement.style.textAlign = 'center';
    errorElement.style.fontFamily = 'Arial, sans-serif';
    errorElement.innerHTML = `
      <h2>应用初始化失败</h2>
      <p>${error.message}</p>
      <button onclick="window.location.reload()" style="padding: 8px 16px; margin-top: 10px; background: white; color: #f5222d; border: none; border-radius: 4px; cursor: pointer;">刷新页面重试</button>
    `;
    document.body.appendChild(errorElement);
  });
}

// 为多种微前端框架提供导出支持，符合业界标准
// 支持 wujie, qiankun, single-spa 等主流微前端框架
try {
  // CommonJS 导出
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = microApp;
  }
  
  // ES Module 导出
  if (typeof exports !== 'undefined') {
    exports.default = microApp;
    Object.assign(exports, microApp);
  }
  
  // 全局导出
  if (typeof window !== 'undefined') {
    window.microApp = microApp;
    // 兼容不同微前端框架的导出命名规范
    window[APP_CONFIG.ID] = window[APP_CONFIG.ID] || microApp;
    
    // 支持 single-spa
    if (!window.singleSpaNavigate && typeof window.registerApplication === 'function') {
      window.singleSpaReact = window.singleSpaReact || {};
      window.singleSpaReact.declareChildApplication = window.registerApplication;
    }
  }
  
  // 为其他微前端框架提供标准导出
  if (typeof self !== 'undefined') {
    self[APP_CONFIG.ID] = microApp;
  }
} catch (exportError) {
  console.warn(`[${APP_CONFIG.ID}] 导出微应用失败，但不影响应用运行:`, exportError);
}

console.log(`[${APP_CONFIG.ID}] 微应用初始化完成，支持多种微前端框架`);
