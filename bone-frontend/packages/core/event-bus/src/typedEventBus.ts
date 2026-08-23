/**
 * 类型安全的事件总线
 * 替代原有全 any 类型的 SimpleEventBus
 *
 * 用法：
 * ```ts
 * import { typedEventBus } from '@bone/core/event-bus';
 *
 * // 定义事件映射
 * interface MyEvents {
 *   'user:login': { userId: number; timestamp: number };
 *   'user:logout': void;
 *   'theme:change': 'light' | 'dark';
 * }
 *
 * const bus = typedEventBus<MyEvents>();
 *
 * bus.on('user:login', (data) => {
 *   console.log(data.userId); // 类型安全：number
 * });
 *
 * bus.emit('user:login', { userId: 1, timestamp: Date.now() });
 * ```
 */

export class TypedEventBus<TEvents extends Record<string, any>> {
  private handlers: { [K in keyof TEvents]?: Array<(data: TEvents[K]) => void> } = {};

  on<K extends keyof TEvents>(event: K, handler: (data: TEvents[K]) => void): () => void {
    if (!this.handlers[event]) {
      this.handlers[event] = [];
    }
    this.handlers[event]!.push(handler);
    return () => this.off(event, handler);
  }

  off<K extends keyof TEvents>(event: K, handler?: (data: TEvents[K]) => void): void {
    if (!this.handlers[event]) return;
    if (handler) {
      this.handlers[event] = this.handlers[event]!.filter(h => h !== handler);
    } else {
      delete this.handlers[event];
    }
  }

  emit<K extends keyof TEvents>(event: K, data: TEvents[K]): void {
    if (this.handlers[event]) {
      this.handlers[event]!.forEach(h => {
        try {
          h(data);
        } catch (e) {
          console.error(`[TypedEventBus] handler error for "${String(event)}":`, e);
        }
      });
    }
  }

  clear(): void {
    this.handlers = {};
  }
}

/** 创建类型安全的事件总线实例 */
export function typedEventBus<TEvents extends Record<string, any>>(): TypedEventBus<TEvents> {
  return new TypedEventBus<TEvents>();
}
