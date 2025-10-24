// 简单的本地事件总线实现
export class SimpleEventBus {
  private events: Record<string, Function[]> = {};

  on(event: string, handler: Function) {
    if (!this.events[event]) {
      this.events[event] = [];
    }
    this.events[event].push(handler);
  }

  emit(event: string, data: any) {
    if (this.events[event]) {
      this.events[event].forEach(handler => handler(data));
    }
  }

  off(event: string, handler?: Function) {
    if (!this.events[event]) return;
    
    if (handler) {
      this.events[event] = this.events[event].filter(h => h !== handler);
    } else {
      delete this.events[event];
    }
  }
}

// 创建全局事件总线实例
const globalEventBus = new SimpleEventBus();
export function getEventBus(): SimpleEventBus {
  return globalEventBus;
}
export { globalEventBus };

// 消息类型定义
export interface MicroAppMessage {
  id: string;
  source: string;
  from?: string; // 兼容from属性
  target: string;
  to?: string; // 兼容to属性
  type: string;
  payload?: any;
  timestamp: number;
  requestId?: string;
  messageId?: string; // 兼容messageId属性
  responseExpected?: boolean;
}

declare const window: Window & {
  __MICRO_APP_ENVIRONMENT__?: boolean;
  __MICRO_APP_NAME__?: string;
  __MICRO_APP_PUBLIC_PATH__?: string;
  __MICRO_APP_BASE_ROUTE__?: string;
};

/**
 * 微应用消息通信工具类
 * 提供高级消息通信API，简化跨应用通信
 */
export class MicroAppMessenger {
  private eventBus: SimpleEventBus;
  private appName: string;
  private responseHandlers: Map<string, {
    resolve: (response: MicroAppMessage) => void;
    reject: (error: Error) => void;
    timeoutId: ReturnType<typeof setTimeout>;
  }> = new Map();
  private defaultTimeout = 5000;
  private messageQueue: Array<{
    message: MicroAppMessage;
    retries: number;
    maxRetries: number;
    retryDelay: number;
    lastAttempt: number;
  }> = [];
  private queueProcessing = false;

  /**
   * 构造函数
   * @param appName 当前应用名称
   * @param eventBus 事件总线实例，默认为全局事件总线
   */
  constructor(appName: string, eventBus?: SimpleEventBus) {
    if (!appName || typeof appName !== 'string') {
      throw new Error('appName must be a non-empty string');
    }
    this.appName = appName;
    this.eventBus = eventBus || getEventBus();
    this.initialize();
  }

  /**
   * 初始化消息通信工具
   */
  private initialize(): void {
    // 订阅消息事件
    this.eventBus.on('micro:app:message', (message: MicroAppMessage) => {
      this.handleIncomingMessage(message);
    });

    // 订阅响应事件
    this.eventBus.on('micro:app:response', (data: { requestId: string; response: MicroAppMessage }) => {
      this.handleResponse(data);
    });

    // 订阅应用挂载事件
    this.eventBus.on('app:mounted', () => {
      // 应用挂载后，尝试处理消息队列
      this.processMessageQueue();
    });
  }

  /**
   * 生成唯一消息ID
   */
  private generateMessageId(): string {
    return `${this.appName}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * 处理接收到的消息
   * @param message 接收到的消息
   */
  private handleIncomingMessage(message: MicroAppMessage): void {
    if (!message) return;
    
    // 检查消息是否目标是当前应用
    const target = message.target || message.to;
    if (target === this.appName || target === '*') {
      // 触发特定类型的消息事件
      if (message.responseExpected) {
        this.eventBus.emit('micro:app:message', message);
      } else {
        this.eventBus.emit(`message:${message.type || 'unknown'}`, message);
      }
    }
  }

  /**
   * 处理响应消息
   * @param data 响应数据
   */
  private handleResponse(data: { requestId: string; response: MicroAppMessage }): void {
    if (!data || !data.requestId) {
      console.warn('Invalid response data: missing requestId');
      return;
    }
    
    const handler = this.responseHandlers.get(data.requestId);
    
    if (handler) {
      try {
        // 清除超时
        clearTimeout(handler.timeoutId);
        
        // 调用处理器
        handler.resolve(data.response);
      } catch (error) {
        console.error('Error handling response:', error);
      } finally {
        // 移除处理器
        this.responseHandlers.delete(data.requestId);
      }
    }
  }



  /**
   * 发送消息给目标应用
   * @param targetApp 目标应用名称
   * @param type 消息类型
   * @param payload 消息内容
   */
  send(targetApp: string, type: string, payload?: Record<string, unknown> | Array<unknown> | string | number | boolean | null): void {
    if (!targetApp || typeof targetApp !== 'string' || !type || typeof type !== 'string' || !this.appName) {
      console.error('Invalid parameters for send: targetApp, type and appName are required');
      return;
    }
    
    const message: MicroAppMessage = {
      id: this.generateMessageId(),
      source: this.appName,
      target: targetApp,
      to: targetApp,
      type,
      payload,
      timestamp: Date.now()
    };

    // 使用类型断言发送消息
    this.eventBus.emit('micro:app:message', message);
  }



  /**
   * 发送消息并等待响应
   * @param targetApp 目标应用名称
   * @param type 消息类型
   * @param payload 消息内容
   * @param timeout 超时时间（毫秒）
   * @returns Promise<any> 响应结果
   */
  async sendWithResponse(targetApp: string, type: string, payload?: Record<string, unknown> | Array<unknown> | string | number | boolean | null, timeout: number = this.defaultTimeout): Promise<MicroAppMessage> {
    if (!targetApp || typeof targetApp !== 'string' || !type || typeof type !== 'string') {
      throw new Error('Invalid parameters: targetApp and type are required');
    }

    if (typeof timeout !== 'number' || timeout <= 0) {
      timeout = this.defaultTimeout;
    }

    const messageId = this.generateMessageId();
    const message: MicroAppMessage = {
      id: this.generateMessageId(),
      source: this.appName,
      target: targetApp,
      to: targetApp,
      type,
      payload,
      timestamp: Date.now(),
      responseExpected: true
    };

    return new Promise((resolve, reject) => {
      // 设置超时
      const timeoutId = setTimeout(() => {
        this.responseHandlers.delete(messageId);
        reject(new Error(`Message response timed out after ${timeout}ms`));
      }, timeout);

      // 保存响应处理程序
      this.responseHandlers.set(messageId, {
        resolve,
        reject,
        timeoutId
      });

      // 发送消息
      try {
        this.eventBus.emit('micro:app:message', message);
      } catch (error) {
        this.responseHandlers.delete(messageId);
        clearTimeout(timeoutId);
        reject(error instanceof Error ? error : new Error('Failed to send message'));
      }
    });
  }

  /**
   * 回复消息
   * @param message 原始消息
   * @param payload 回复内容
   */
  reply(message: MicroAppMessage, payload?: Record<string, unknown> | Array<unknown> | string | number | boolean | null): void {
    if (!message || !message.from || !message.messageId) {
      console.error('Invalid message: missing required fields');
      return;
    }

    const response: MicroAppMessage = {
      id: this.generateMessageId(),
      source: this.appName,
      target: message.from,
      to: message.from,
      type: `response:${message.type || 'unknown'}`,
      payload,
      timestamp: Date.now()
    };

    // 发送响应消息到正确的事件通道
    this.eventBus.emit('micro:app:response', {
      requestId: message.messageId,
      response
    });
  }

  /**
   * 监听特定类型的消息
   * @param type 消息类型
   * @param handler 消息处理函数
   * @returns 取消订阅函数
   */
  on(type: string, handler: (message: MicroAppMessage) => void): () => void {
    if (!type || typeof handler !== 'function') {
      console.error('Invalid parameters: type is required and handler must be a function');
      return () => {};
    }

    const eventType = `message:${type}`;
    const wrappedHandler = (message: MicroAppMessage) => {
      // 确保消息目标是当前应用
      // 兼容target和to属性
      const target = message.target || message.to;
      if (target === this.appName || target === '*') {
        try {
          handler(message);
        } catch (error) {
          console.error(`Error handling message of type ${type}:`, error);
        }
      }
    };

    this.eventBus.on(eventType, wrappedHandler);
    return () => this.eventBus.off(eventType, wrappedHandler);
  }

  /**
   * 处理消息队列
   */
  private async processMessageQueue(): Promise<void> {
    // 如果队列正在处理中或者队列为空，则退出
    if (this.queueProcessing || !this.messageQueue || this.messageQueue.length === 0) {
      return;
    }

    this.queueProcessing = true;

    try {
      while (this.messageQueue.length > 0) {
        const queueItem = this.messageQueue[0];
        
        if (!queueItem || !queueItem.message) {
          this.messageQueue.shift();
          continue;
        }
        
        const now = Date.now();
        // 检查是否可以重试
        if (now - queueItem.lastAttempt >= queueItem.retryDelay) {
          try {
            // 确保消息的to字段不为undefined
            const targetApp = queueItem.message.target || queueItem.message.to;
            if (!targetApp) {
              console.error('Invalid message in queue: missing target');
              this.messageQueue.shift();
              continue;
            }
            
            // 发送消息
            this.eventBus.emit('micro:app:message', queueItem.message);
            // 消息发送成功，从队列中移除
            this.messageQueue.shift();
          } catch (error) {
            console.error('Error sending message from queue:', error);
            queueItem.retries++;
            queueItem.lastAttempt = now;
            queueItem.retryDelay = (queueItem.retryDelay || 1000) * 2;
            
            // 如果重试次数超过最大限制，则移除消息
            if (queueItem.retries >= queueItem.maxRetries) {
              const targetApp = queueItem.message.target || queueItem.message.to || 'unknown';
              console.warn(`Message to ${targetApp} failed after ${queueItem.maxRetries} attempts, removing from queue`);
              this.messageQueue.shift();
            }
          }
        } else {
          // 还未到重试时间，等待剩余时间
          await new Promise(resolve => setTimeout(resolve, queueItem.retryDelay - (now - queueItem.lastAttempt)));
        }
      }
    } catch (queueError) {
      console.error('Error processing message queue:', queueError);
    } finally {
      this.queueProcessing = false;
    }
  }



  /**
   * 获取消息队列长度
   * @returns 队列中的消息数量
   */
  getQueueLength(): number {
    return this.messageQueue.length;
  }

  /**
   * 清空消息队列
   */
  private clearQueue(): void {
    // 使用splice(0)代替赋值新数组，更安全的清空方式
    this.messageQueue.splice(0);
  }

  /**
   * 销毁消息通信工具，清理资源
   */
  destroy(): void {
    try {
      // 清除所有等待的响应
      this.responseHandlers.forEach(handler => {
        try {
          clearTimeout(handler.timeoutId);
          handler.reject(new Error('Messenger destroyed'));
        } catch (error) {
          console.error('Error cleaning up response handler:', error);
        }
      });

      this.responseHandlers.clear();

      // 清空消息队列
      this.clearQueue();
      
      // 重置处理标志
      this.queueProcessing = false;
    } catch (error) {
      console.error('Error during messenger destruction:', error);
    }
  }
}

// 全局就绪状态
let isReady = false;

/**
 * 创建微应用消息通信工具实例
 * @param appName 应用名称
 * @returns 消息通信工具实例
 */
export function createMicroAppMessenger(appName: string): MicroAppMessenger {
  if (!isReady) {
    console.warn('MicroAppMessenger may not be fully ready yet');
  }
  return new MicroAppMessenger(appName);
}

/**
 * 销毁所有消息通信资源
 */
export function dispose(): void {
  // 清理全局事件监听器
  if (window && window.removeEventListener) {
    window.removeEventListener('message', handleGlobalMessage);
  }
}

/**
 * 获取消息通道是否准备就绪
 * @returns 是否准备就绪
 */
export function getIsReady(): boolean {
  return isReady;
}

// 全局消息处理函数
interface MicroAppMessageEventData {
  type: string;
  data: MicroAppMessage;
}

interface GlobalMessenger {
  handleIncomingMessage: (message: MicroAppMessage) => void;
}

function handleGlobalMessage(event: MessageEvent): void {
  if (!event || !event.data) return;
  
  const messageData = event.data as MicroAppMessageEventData | null;
  
  if (messageData && typeof messageData === 'object' && messageData.type === 'micro-app-message' && messageData.data) {
    const originalMessage = messageData.data as MicroAppMessage;
    // 确保消息格式正确并进行属性兼容处理
    if (originalMessage) {
      // 创建兼容的消息对象，确保所有必要属性都存在
      const compatibleMessage: MicroAppMessage = {
        id: originalMessage.id || originalMessage.messageId || '',
        source: originalMessage.source || originalMessage.from || '',
        target: originalMessage.target || originalMessage.to || '',
        to: originalMessage.to || originalMessage.target || '',
        from: originalMessage.from || originalMessage.source || '',
        type: originalMessage.type || 'unknown',
        payload: originalMessage.payload,
        timestamp: originalMessage.timestamp || Date.now(),
        requestId: originalMessage.requestId,
        messageId: originalMessage.messageId || originalMessage.id,
        responseExpected: originalMessage.responseExpected
      };
      
      const globalMessenger = (window as Window & { __MICRO_APP_MESSENGER__?: unknown }).__MICRO_APP_MESSENGER__;
      if (globalMessenger && typeof globalMessenger === 'object' && 'handleIncomingMessage' in globalMessenger) {
        try {
          const messenger = globalMessenger as unknown as GlobalMessenger;
          if (typeof messenger.handleIncomingMessage === 'function') {
            messenger.handleIncomingMessage(compatibleMessage);
          }
        } catch (error) {
          console.error('Error handling global message:', error);
        }
      }
    }
  }
}

// 设置就绪状态
export function setIsReady(ready: boolean): void {
  isReady = ready;
}

export default MicroAppMessenger;
