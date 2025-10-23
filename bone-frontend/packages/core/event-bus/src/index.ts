// 微前端事件总线类型定义

export interface EventHandler<T> {
  (data: T): void;
}

export interface UnsubscribeFunction {
  (): void;
}

export interface EventMap {
  [eventName: string]: any;
}

// 类型安全的事件总线类
export class TypedEventBus<Events extends EventMap> {
  private eventHandlers: Map<keyof Events, Set<EventHandler<any>>> = new Map();

  // 注册事件监听
  on<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): UnsubscribeFunction {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }

    const handlers = this.eventHandlers.get(event)!;
    handlers.add(handler);

    // 返回取消订阅函数
    return () => {
      handlers.delete(handler);
      if (handlers.size === 0) {
        this.eventHandlers.delete(event);
      }
    };
  }

  // 只监听一次事件
  once<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): UnsubscribeFunction {
    const onceHandler: EventHandler<Events[K]> = (data) => {
      unsubscribe();
      handler(data);
    };

    const unsubscribe = this.on(event, onceHandler);
    return unsubscribe;
  }

  // 触发事件
  emit<K extends keyof Events>(event: K, data: Events[K]): void {
    const handlers = this.eventHandlers.get(event);
    if (!handlers) {
      return;
    }

    // 复制处理函数集合，防止在触发过程中修改导致的问题
    const handlersCopy = new Set(handlers);
    handlersCopy.forEach(handler => {
      try {
        handler(data);
      } catch (error) {
        console.error(`Error in event handler for ${String(event)}:`, error);
      }
    });
  }

  // 移除特定事件的所有监听器
  off<K extends keyof Events>(event: K): void {
    this.eventHandlers.delete(event);
  }

  // 移除所有事件的所有监听器
  clear(): void {
    this.eventHandlers.clear();
  }

  // 获取特定事件的监听器数量
  getListenerCount<K extends keyof Events>(event: K): number {
    const handlers = this.eventHandlers.get(event);
    return handlers ? handlers.size : 0;
  }

  // 获取所有已注册的事件名
  getRegisteredEvents(): Array<keyof Events> {
    return Array.from(this.eventHandlers.keys());
  }
}

// 应用间事件类型定义
export interface AppEvents {
  // 通用事件
  'app:mounted': { appId: string };
  'app:unmounted': { appId: string };
  'app:activated': { appId: string };
  'app:deactivated': { appId: string };
  'app:error': { appId: string; error: Error; phase: string };
  'user:login': { userId: string; token: string };
  'user:logout': void;
  'theme:changed': { theme: 'light' | 'dark' | 'highContrast' };
  
  // 通信事件
  'micro:app:message': MicroAppMessage;
  'micro:app:response': { requestId: string; response: MicroAppMessage };
}

// 微应用消息接口
export interface MicroAppMessage {
  /** 消息类型 */
  type: string;
  /** 消息内容 */
  payload?: any;
  /** 发送者名称 */
  from: string;
  /** 接收者名称（可选） */
  to?: string;
  /** 消息时间戳 */
  timestamp: number;
  /** 消息唯一标识符 */
  messageId?: string;
  /** 是否需要响应 */
  responseExpected?: boolean;
  /** 错误信息 */
  error?: string;
}

// 创建全局事件总线实例
let globalEventBusInstance: TypedEventBus<AppEvents> | null = null;

export function getEventBus(): TypedEventBus<AppEvents> {
  if (!globalEventBusInstance) {
    globalEventBusInstance = new TypedEventBus<AppEvents>();
  }
  return globalEventBusInstance;
}

// 导出全局事件总线实例
export const globalEventBus = getEventBus();

// 向后兼容的导出
export function getGlobalEventBus(): TypedEventBus<AppEvents> {
  return getEventBus();
}