import { TypedEventBus, AppEvents, MicroAppMessage, getEventBus } from './index';

/**
 * 微应用消息通信工具类
 * 提供高级消息通信API，简化跨应用通信
 */
export class MicroAppMessenger {
  private eventBus: TypedEventBus<AppEvents>;
  private appName: string;
  private responseHandlers: Map<string, {
    resolve: (response: MicroAppMessage) => void;
    reject: (error: Error) => void;
    timeoutId: ReturnType<typeof setTimeout>;
  }> = new Map();
  private messageQueue: Array<{
    message: MicroAppMessage;
    retries: number;
    maxRetries: number;
    retryDelay: number;
    lastAttempt: number;
  }> = [];
  private queueProcessing = false;
  private maxRetries = 3;
  private defaultTimeout = 5000;

  /**
   * 构造函数
   * @param appName 当前应用名称
   * @param eventBus 事件总线实例，默认为全局事件总线
   */
  constructor(appName: string, eventBus?: TypedEventBus<AppEvents>) {
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
    this.eventBus.on('micro:app:response', (data) => {
      this.handleResponse(data);
    });

    // 订阅应用挂载事件
    this.eventBus.on('app:mounted', ({ appId }) => {
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
    // 检查消息是否目标是当前应用
    if (message.to !== this.appName && message.to !== '*') {
      return;
    }

    // 触发特定类型的消息事件
    this.eventBus.emit(`message:${message.type}` as any, message);
  }

  /**
   * 处理接收到的响应
   * @param data 响应数据
   */
  private handleResponse(data: { requestId: string; response: MicroAppMessage }): void {
    const handler = this.responseHandlers.get(data.requestId);
    if (handler) {
      // 清除超时
      clearTimeout(handler.timeoutId);
      
      // 调用处理器
      handler.resolve(data.response);
      
      // 移除处理器
      this.responseHandlers.delete(data.requestId);
    }
  }

  /**
   * 检查应用是否活跃
   * @param appName 应用名称
   * @returns 是否活跃
   */
  private isAppActive(appName: string): boolean {
    try {
      // 动态导入应用注册表以避免循环依赖
      const { getApplicationRegistry } = require('@bone/core/micro-fe-runtime');
      const registry = getApplicationRegistry();
      return registry.isAppActive(appName);
    } catch (error) {
      console.warn('Failed to check app status, assuming active:', error);
      return true;
    }
  }

  /**
   * 发送消息到指定应用
   * @param targetApp 目标应用名称
   * @param type 消息类型
   * @param payload 消息内容
   */
  send(targetApp: string, type: string, payload?: any): void {
    const message: MicroAppMessage = {
      messageId: this.generateMessageId(),
      from: this.appName,
      to: targetApp,
      type,
      payload,
      timestamp: Date.now()
    };

    // 检查目标应用是否活跃
    if (this.isAppActive(targetApp)) {
      this.eventBus.emit('micro:app:message', message);
    } else {
      // 否则将消息加入队列
      this.enqueueMessage(message);
    }
  }

  /**
   * 将消息加入队列
   * @param message 要发送的消息
   */
  private enqueueMessage(message: MicroAppMessage): void {
    this.messageQueue.push({
      message,
      retries: 0,
      maxRetries: this.maxRetries,
      retryDelay: 1000,
      lastAttempt: Date.now()
    });

    // 开始处理队列
    if (!this.queueProcessing) {
      this.processMessageQueue();
    }
  }

  /**
   * 发送消息并等待响应
   * 实现请求-响应模式
   * @param appName 目标微应用名称
   * @param type 消息类型
   * @param payload 消息内容
   * @param timeout 超时时间（毫秒），默认5000
   * @returns Promise，解析为响应消息
   */
  /**
   * 发送消息并等待响应
   * @param targetApp 目标应用名称
   * @param type 消息类型
   * @param payload 消息内容
   * @param timeout 超时时间（毫秒）
   * @returns 响应消息
   */
  async sendWithResponse(targetApp: string, type: string, payload?: any, timeout: number = this.defaultTimeout): Promise<MicroAppMessage> {
    return new Promise((resolve, reject) => {
      const messageId = this.generateMessageId();

      // 设置超时
      const timeoutId = setTimeout(() => {
        this.responseHandlers.delete(messageId);
        reject(new Error(`等待 ${targetApp} 响应超时`));
      }, timeout);

      // 保存响应处理器
      this.responseHandlers.set(messageId, {
        resolve,
        reject,
        timeoutId
      });

      // 发送请求消息
      this.send(targetApp, type, {
        ...payload,
        messageId,
        responseExpected: true
      });
    });
  }

  /**
   * 回复消息
   * @param message 原始消息
   * @param payload 回复内容
   */
  reply(message: MicroAppMessage, payload?: any): void {
    if (!message.responseExpected) {
      console.warn('Cannot reply to a message that did not request a response');
      return;
    }

    const response: MicroAppMessage = {
      messageId: this.generateMessageId(),
      from: this.appName,
      to: message.from,
      type: `${message.type}:response`,
      payload,
      timestamp: Date.now()
    };

    this.eventBus.emit('micro:app:response', {
      requestId: message.messageId,
      response
    });
  }

  /**
   * 订阅消息
   * @param type 消息类型
   * @param handler 消息处理函数
   * @returns 取消订阅函数
   */
  on(type: string, handler: (message: MicroAppMessage) => void): () => void {
    return this.eventBus.on(`message:${type}` as any, handler);
  }

  /**
   * 处理消息队列
   */
  private async processMessageQueue(): Promise<void> {
    this.queueProcessing = true;

    while (this.messageQueue.length > 0) {
      const queueItem = this.messageQueue[0];
      const now = Date.now();

      // 检查是否可以重试
      if (now - queueItem.lastAttempt >= queueItem.retryDelay) {
        if (this.isAppActive(queueItem.message.to)) {
          // 应用已活跃，发送消息
          try {
            this.eventBus.emit('micro:app:message', queueItem.message);
            // 消息发送成功，从队列中移除
            this.messageQueue.shift();
          } catch (error) {
            console.error('Error sending queued message:', error);
            // 增加重试计数
            queueItem.retries++;
            queueItem.lastAttempt = now;
            queueItem.retryDelay *= 2; // 指数退避

            // 达到最大重试次数，放弃并从队列中移除
            if (queueItem.retries >= queueItem.maxRetries) {
              console.warn(`Message to ${queueItem.message.to} failed after ${queueItem.maxRetries} retries`);
              this.messageQueue.shift();
            }
          }
        } else {
          // 应用仍未活跃，等待一段时间后重试
          await new Promise(resolve => setTimeout(resolve, 500));
        }
      } else {
        // 还未到重试时间，等待剩余时间
        await new Promise(resolve => setTimeout(resolve, queueItem.retryDelay - (now - queueItem.lastAttempt)));
      }
    }

    this.queueProcessing = false;
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
  clearQueue(): void {
    this.messageQueue = [];
  }

  /**
   * 销毁消息通信工具，清理资源
   */
  destroy(): void {
    // 清除所有等待的响应
    this.responseHandlers.forEach(handler => {
      clearTimeout(handler.timeoutId);
      handler.reject(new Error('Messenger destroyed'));
    });
    this.responseHandlers.clear();

    // 清空消息队列
    this.clearQueue();
  }
}

// 创建默认的消息通信工具实例
export function createMicroAppMessenger(appName: string): MicroAppMessenger {
  return new MicroAppMessenger(appName);
}

// 向后兼容的导出
export function dispose(): void {
  // 此方法用于向后兼容，实际上不需要做任何事情
  console.warn('dispose() is deprecated, please use instance.destroy() instead');
}

// 向后兼容的导出
export function getIsReady(): boolean {
  console.warn('getIsReady() is deprecated, communication is always ready');
  return true;
}

export default MicroAppMessenger;