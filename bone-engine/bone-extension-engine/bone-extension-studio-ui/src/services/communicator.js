/**
 * 微应用通信接口 - 用于标准化微应用与主应用之间的通信
 * 提供安全、可靠、结构化的消息传递机制
 */

// 消息类型常量 - 定义标准化的消息事件
const MESSAGE_TYPES = {
  // 应用生命周期事件
  APP_READY: 'app:ready',
  APP_INIT_FAILED: 'app:init:failed',
  APP_INIT_TIMEOUT: 'app:init:timeout',
  APP_CLEANUP_COMPLETED: 'app:cleanup:completed',
  COMPONENT_UNMOUNTING: 'component:unmounting',
  
  // 数据相关事件
  DATA_LOADED: 'data:loaded',
  DATA_REFRESHED: 'data:refreshed',
  DATA_REFRESH_FAILED: 'data:refresh:failed',
  DATA_REFRESH_TIMEOUT: 'data:refresh:timeout',
  
  // 错误相关事件
  MICROAPP_ERROR: 'microapp:error',
  MICROAPP_RECOVERED: 'microapp:recovered',
  
  // 操作相关事件
  OPERATION_STARTED: 'operation:started',
  OPERATION_COMPLETED: 'operation:completed',
  OPERATION_FAILED: 'operation:failed',
  
  // 性能相关事件
  PERFORMANCE_METRIC: 'performance:metric',
  RESOURCE_CONSUMPTION: 'resource:consumption'
};

/**
 * 微应用通信器类
 * 提供标准化的消息发送和接收机制
 */
class MicroAppCommunicator {
  constructor(appId, options = {}) {
    this.appId = appId;
    this.options = {
      debug: false,
      timeout: 30000,
      retryCount: 3,
      ...options
    };
    this.messageHandlers = new Map();
    this.isReady = false;
    this.initialized = false;
    this.messageQueue = [];
    this.messageIdCounter = 0;
    this.receivedMessages = new Set();
  }

  /**
   * 初始化通信器
   * @returns {boolean} 是否初始化成功
   */
  init() {
    if (this.initialized) {
      return true;
    }

    try {
      // 尝试使用全局通信机制
      if (window.bone && window.bone.communicator) {
        this.globalCommunicator = window.bone.communicator;
      }
      // 尝试使用全局变量通信器
      else if (window.__bone_communicator__) {
        this.globalCommunicator = window.__bone_communicator__;
      }
      
      this.initialized = true;
      this.isReady = true;
      
      // 处理队列中的消息
      this.processMessageQueue();
      
      if (this.options.debug) {
        console.log(`[${this.appId}] 通信器初始化成功`);
      }
      
      return true;
    } catch (error) {
      console.error(`[${this.appId}] 通信器初始化失败:`, error);
      return false;
    }
  }

  /**
   * 发送消息到目标应用
   * @param {string} target - 目标应用ID
   * @param {string} type - 消息类型
   * @param {Object} data - 消息数据
   * @returns {Promise<Object>} 包含消息ID和状态的对象
   */
  sendMessage(target, type, data = {}) {
    const messageId = this.generateMessageId(type);
    const timestamp = Date.now();
    
    // 构建标准消息结构
    const message = {
      id: messageId,
      source: this.appId,
      target,
      type,
      timestamp,
      data: {
        appId: this.appId,
        ...data
      }
    };

    // 如果通信器未准备好，将消息加入队列
    if (!this.isReady) {
      if (this.options.debug) {
        console.log(`[${this.appId}] 通信器未准备好，消息已加入队列:`, message);
      }
      this.messageQueue.push({ target, type, data, messageId });
      return Promise.resolve({ id: messageId, queued: true });
    }

    try {
      // 使用全局通信器发送消息
      if (this.globalCommunicator && typeof this.globalCommunicator.sendMessage === 'function') {
        this.globalCommunicator.sendMessage(target, type, message.data);
      }
      // 降级使用postMessage
      else if (window.parent) {
        window.parent.postMessage(message, '*'); // 实际项目中应设置具体的origin
      }
      
      if (this.options.debug) {
        console.log(`[${this.appId}] 消息发送成功:`, message);
      }
      
      return Promise.resolve({ id: messageId, success: true });
    } catch (error) {
      console.error(`[${this.appId}] 消息发送失败:`, error);
      
      // 尝试重试
      return this.handleSendFailure(target, type, data, messageId);
    }
  }

  /**
   * 处理发送失败的消息，实现重试机制
   */
  async handleSendFailure(target, type, data, messageId) {
    let retryCount = 0;
    
    while (retryCount < this.options.retryCount) {
      retryCount++;
      
      await new Promise(resolve => setTimeout(resolve, 1000 * retryCount));
      
      try {
        if (this.globalCommunicator && typeof this.globalCommunicator.sendMessage === 'function') {
          this.globalCommunicator.sendMessage(target, type, data);
        }
        
        if (this.options.debug) {
          console.log(`[${this.appId}] 消息重试成功 (${retryCount}/${this.options.retryCount}):`, messageId);
        }
        
        return { id: messageId, success: true, retries: retryCount };
      } catch (retryError) {
        if (this.options.debug) {
          console.warn(`[${this.appId}] 消息重试失败 (${retryCount}/${this.options.retryCount}):`, retryError);
        }
      }
    }
    
    return { id: messageId, success: false, maxRetries: true };
  }

  /**
   * 处理接收到的消息
   * @param {Object} message - 接收到的消息
   * @returns {boolean} 是否成功处理
   */
  handleMessage(message) {
    // 防止重复处理消息
    if (message.id && this.receivedMessages.has(message.id)) {
      return false;
    }
    
    // 只处理目标为当前应用的消息
    if (message.target && message.target !== this.appId && message.target !== '*') {
      return false;
    }
    
    // 标记消息已接收
    if (message.id) {
      this.receivedMessages.add(message.id);
    }
    
    // 执行对应的消息处理器
    const handler = this.messageHandlers.get(message.type);
    if (handler) {
      try {
        safeExecute(() => handler(message.data, message), `消息处理 [${message.type}]`);
        return true;
      } catch (error) {
        console.error(`[${this.appId}] 处理消息失败 [${message.type}]:`, error);
      }
    }
    
    return false;
  }

  /**
   * 注册消息处理器
   * @param {string} type - 消息类型
   * @param {Function} handler - 处理函数
   * @returns {Function} 取消注册的函数
   */
  onMessage(type, handler) {
    if (!type || typeof handler !== 'function') {
      return () => {};
    }
    
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    
    const handlers = this.messageHandlers.get(type);
    handlers.push(handler);
    
    // 返回取消注册函数
    return () => {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    };
  }

  /**
   * 处理队列中的消息
   */
  processMessageQueue() {
    const queue = [...this.messageQueue];
    this.messageQueue = [];
    
    queue.forEach(({ target, type, data }) => {
      this.sendMessage(target, type, data);
    });
  }

  /**
   * 生成唯一的消息ID
   */
  generateMessageId(type) {
    return `${this.appId}_${type}_${this.messageIdCounter++}_${Date.now()}`;
  }

  /**
   * 发送应用就绪消息
   */
  sendAppReady(metadata = {}) {
    return this.sendMessage('main', MESSAGE_TYPES.APP_READY, {
      status: 'ready',
      version: metadata.version || '1.0.0',
      features: metadata.features || [],
      timestamp: Date.now(),
      hasErrors: metadata.hasErrors || false
    });
  }

  /**
   * 发送数据加载完成消息
   */
  sendDataLoaded(dataStats = {}) {
    return this.sendMessage('main', MESSAGE_TYPES.DATA_LOADED, {
      dataLoaded: true,
      ...dataStats,
      timestamp: Date.now()
    });
  }

  /**
   * 发送组件卸载消息
   */
  sendUnmounting(cleanupStats = {}) {
    return this.sendMessage('main', MESSAGE_TYPES.COMPONENT_UNMOUNTING, {
      timestamp: Date.now(),
      resourceCleanup: cleanupStats
    });
  }

  /**
   * 发送错误消息
   */
  sendError(error, errorContext = {}) {
    return this.sendMessage('main', MESSAGE_TYPES.MICROAPP_ERROR, {
      errorType: error.name || 'Error',
      errorMessage: error.message,
      stack: error.stack,
      context: errorContext,
      timestamp: Date.now()
    });
  }

  /**
   * 发送性能指标
   */
  sendPerformanceMetric(metricType, value, context = {}) {
    return this.sendMessage('main', MESSAGE_TYPES.PERFORMANCE_METRIC, {
      metricType,
      value,
      context,
      timestamp: Date.now()
    });
  }

  /**
   * 销毁通信器，清理资源
   */
  destroy() {
    this.messageHandlers.clear();
    this.messageQueue = [];
    this.receivedMessages.clear();
    this.isReady = false;
    this.initialized = false;
    
    if (this.options.debug) {
      console.log(`[${this.appId}] 通信器已销毁`);
    }
  }
}

/**
 * 安全执行函数
 */
const safeExecute = (fn, context = 'Unknown operation') => {
  try {
    return fn();
  } catch (error) {
    console.error(`[${context}] 执行失败:`, error);
    return false;
  }
};

// 导出通信器和消息类型
export { MicroAppCommunicator, MESSAGE_TYPES };
export default MicroAppCommunicator;
