import { ErrorContext } from './types';

// 本地事件总线实现（替代外部依赖）
class LocalEventBus {
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
    if (handler) {
      this.events[event] = this.events[event]?.filter(h => h !== handler) || [];
    } else {
      delete this.events[event];
    }
  }
}

const localEventBus = new LocalEventBus();
const getEventBus = () => localEventBus;

/**
 * 错误处理器
 * 提供统一的错误捕获、处理和上报功能
 */
export class ErrorHandler {
  private appId: string;
  private eventBus = getEventBus();
  private errorHistory: Array<{
    error: Error;
    context: ErrorContext;
    timestamp: number;
  }> = [];

  /**
   * 构造函数
   * @param appId 应用ID
   */
  constructor(appId: string) {
    this.appId = appId;
  }

  /**
   * 处理错误
   * @param error 错误对象
   * @param context 错误上下文
   */
  handle(error: Error, context?: ErrorContext): void {
    // 构建完整的错误上下文
    const fullContext: ErrorContext = {
      appId: this.appId,
      timestamp: Date.now(),
      phase: context?.phase || 'unknown',
      appInfo: context?.appInfo,
      additionalInfo: context?.additionalInfo
    };

    // 记录错误到历史
    this.recordError(error, fullContext);

    // 记录错误到控制台
    this.logError(error, fullContext);

    // 触发错误事件
    this.emitErrorEvent(error, fullContext);

    // 上报错误
    this.reportError(error, fullContext);
  }

  /**
   * 记录错误到历史
   * @param error 错误对象
   * @param context 错误上下文
   */
  private recordError(error: Error, context: ErrorContext): void {
    this.errorHistory.push({
      error,
      context,
      timestamp: Date.now()
    });

    // 限制历史记录数量
    if (this.errorHistory.length > 100) {
      this.errorHistory.shift();
    }
  }

  /**
   * 记录错误到控制台
   * @param error 错误对象
   * @param context 错误上下文
   */
  private logError(error: Error, context: ErrorContext): void {
    console.error(
      `[Bone Error] App: ${this.appId}, Phase: ${context.phase}`,
      error,
      context
    );
  }

  /**
   * 触发错误事件
   * @param error 错误对象
   * @param context 错误上下文
   */
  private emitErrorEvent(error: Error, context: ErrorContext): void {
    this.eventBus.emit('app:error', {
      error,
      context
    });
  }

  /**
   * 上报错误
   * @param error 错误对象
   * @param context 错误上下文
   */
  private reportError(error: Error, context: ErrorContext): void {
    try {
      // 这里实现错误上报逻辑
      // 可以上报到监控系统或日志服务
      console.error(`[Error Report] ${this.formatErrorMessage(error, context)}`);
      
      // 检查是否有全局错误上报函数
      if (typeof window !== 'undefined' && (window as any).__boneErrorReporter) {
        (window as any).__boneErrorReporter.report({
          error,
          context
        });
      }
    } catch (reportError) {
      console.warn('Failed to report error:', reportError);
    }
  }

  /**
   * 格式化错误消息
   * @param error 错误对象
   * @param context 错误上下文
   */
  private formatErrorMessage(error: Error, context: ErrorContext): string {
    return `${context.appId || this.appId} - ${context.phase} - ${error.message}`;
  }

  /**
   * 获取错误历史
   * @param limit 限制返回的错误数量
   */
  getErrorHistory(limit?: number): Array<{
    error: Error;
    context: ErrorContext;
    timestamp: number;
  }> {
    if (limit) {
      return this.errorHistory.slice(-limit);
    }
    return [...this.errorHistory];
  }

  /**
   * 清空错误历史
   */
  clearErrorHistory(): void {
    this.errorHistory = [];
  }

  /**
   * 监听应用生命周期错误
   * @param lifecycle 生命周期实例
   */
  listenAppLifecycleErrors(lifecycle: any) {
    if (lifecycle && typeof lifecycle.on === 'function') {
      lifecycle.on('error', (error: Error, phase: string) => {
        this.handle(error, {
          phase,
          appInfo: lifecycle.appConfig
        });
      });
    }
  }

  /**
   * 静态工厂方法创建错误处理器
   * @param appId 应用ID
   */
  static create(appId: string): ErrorHandler {
    return new ErrorHandler(appId);
  }

  /**
   * 创建错误包装器，自动附加上下文信息
   * @param context 错误上下文
   */
  createErrorWrapper(context: Partial<ErrorContext>) {
    return (error: Error) => {
      this.handle(error, {
        ...context,
        appId: this.appId,
        timestamp: Date.now()
      } as ErrorContext);
      return Promise.reject(error);
    };
  }

  /**
   * 捕获Promise错误
   * @param promise Promise对象
   * @param context 错误上下文
   */
  capturePromise<T>(promise: Promise<T>, context: Partial<ErrorContext>): Promise<T> {
    return promise.catch((error: Error) => {
      this.handle(error, {
        ...context,
        appId: this.appId,
        timestamp: Date.now()
      } as ErrorContext);
      return Promise.reject(error);
    });
  }

  /**
   * 捕获函数执行错误
   * @param fn 要执行的函数
   * @param context 错误上下文
   */
  captureFunction<T extends (...args: any[]) => any>(
    fn: T,
    context: Partial<ErrorContext>
  ): (...args: Parameters<T>) => ReturnType<T> {
    return (...args: Parameters<T>): ReturnType<T> => {
      try {
        return fn(...args);
      } catch (error) {
        this.handle(error as Error, {
          ...context,
          appId: this.appId,
          timestamp: Date.now()
        } as ErrorContext);
        throw error;
      }
    };
  }

  /**
   * 获取错误历史
   * @param limit 限制返回的错误数量
   */
  getErrorHistory(limit?: number): Array<{
    error: Error;
    context: ErrorContext;
    timestamp: number;
  }> {
    if (limit) {
      return this.errorHistory.slice(-limit);
    }
    return [...this.errorHistory];
  }

  /**
   * 清空错误历史
   */
  clearErrorHistory(): void {
    this.errorHistory = [];
  }

  /**
   * 格式化错误信息
   * @param error 错误对象
   */
  formatError(error: Error): string {
    return `${error.name}: ${error.message}\n${error.stack || 'No stack trace available'}`;
  }

  /**
   * 检查错误是否为网络错误
   * @param error 错误对象
   */
  isNetworkError(error: Error): boolean {
    const message = error.message.toLowerCase();
    return message.includes('network') || 
           message.includes('fetch') || 
           message.includes('timeout') ||
           message.includes('connection') ||
           message.includes('http');
  }

  /**
   * 检查错误是否为资源加载错误
   * @param error 错误对象
   */
  isResourceError(error: Error): boolean {
    const message = error.message.toLowerCase();
    return message.includes('load') || 
           message.includes('resource') || 
           message.includes('script') ||
           message.includes('style') ||
           message.includes('image');
  }

  /**
   * 检查错误是否为权限错误
   * @param error 错误对象
   */
  isPermissionError(error: Error): boolean {
    const message = error.message.toLowerCase();
    return message.includes('permission') || 
           message.includes('denied') || 
           error instanceof SecurityError;
  }
}