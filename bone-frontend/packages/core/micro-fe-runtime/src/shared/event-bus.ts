/**
 * 事件总线实现
 * 提供统一的事件发布订阅机制
 */
export class EventBus {
  private events: Record<string, Function[]> = {};
  
  /**
   * 注册事件监听器
   * @param event 事件名称
   * @param handler 处理函数
   */
  on(event: string, handler: Function): void {
    if (!this.events[event]) {
      this.events[event] = [];
    }
    this.events[event].push(handler);
  }
  
  /**
   * 触发事件
   * @param event 事件名称
   * @param data 事件数据
   */
  emit(event: string, data: any): void {
    if (this.events[event]) {
      this.events[event].forEach(handler => handler(data));
    }
  }
  
  /**
   * 移除事件监听器
   * @param event 事件名称
   * @param handler 可选的特定处理函数
   */
  off(event: string, handler?: Function): void {
    if (!this.events[event]) return;
    
    if (handler) {
      this.events[event] = this.events[event].filter(h => h !== handler);
    } else {
      delete this.events[event];
    }
  }
}

// 导出单例实例
const eventBus = new EventBus();
export const getEventBus = () => eventBus;
