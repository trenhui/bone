// 定义错误类型和上下文接口
export interface ErrorContext {
  appId: string;
  timestamp?: number;
  phase: string;
  appInfo?: Record<string, any>;
  additionalInfo?: Record<string, any>;
}

// 增强的错误分类接口
export interface ErrorClassification {
  type: 'network' | 'resource' | 'permission' | 'sandbox' | 'lifecycle' | 'syntax' | 'type' | 'reference' | 'timeout' | 'runtime' | 'unknown';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  isExpected?: boolean;
  isFatal?: boolean;
  errorCode?: string;
}

// 错误统计接口
export interface ErrorStatistics {
  total: number;
  byType: Record<string, number>;
  bySeverity: Record<string, number>;
  byPhase: Record<string, number>;
  recentErrorRate: number; // 每分钟错误率
  suppressed: number;
  reported: number;
  lastErrorTime?: number;
  firstErrorTime?: number;
}

export interface ErrorInfo {
  appId: string;
  message: string;
  stack?: string;
  type: string;
  severity: string;
  phase: string;
  timestamp: number;
  environment: string;
  appInfo?: Record<string, any>;
  additionalInfo?: Record<string, any>;
  isExpected?: boolean;
  isFatal?: boolean;
  errorCode?: string;
  fingerprint: string;
}

export interface ErrorHandlerOptions {
  appId: string;
  reportUrl?: string;
  reportInterval?: number;
  throttlingInterval?: number;
  batchReportSize?: number;
  maxHistorySize?: number;
}

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
  private reportUrl?: string;
  private reportInterval: number = 5000; // 限制错误上报频率
  private throttlingInterval: number = 1000; // 同类错误节流间隔
  private batchReportSize: number = 5; // 批量上报大小
  private maxHistorySize: number = 100; // 最大历史记录数
  private errorHistory: Array<{
    error: Error;
    context: ErrorContext;
    classification: ErrorClassification;
    timestamp: number;
  }> = [];
  private errorThrottling: Map<string, { count: number; lastTime: number }> = new Map(); // 增强的错误节流
  private suppressedErrors: Set<string> = new Set(); // 被抑制的错误键集合
  private pendingReports: Array<{error: Error, context: ErrorContext, classification: ErrorClassification}> = [];
  private errorStats: Map<string, number> = new Map();
  private errorTimestamps: number[] = []; // 用于计算错误率的时间戳数组
  private isDestroyed: boolean = false; // 销毁状态标记
  private reportProcessing: boolean = false;
  private lastReportTime: number = 0;
  private errorRateWarningIssued: number = 0;
  private errorRateMonitorTimer: number | undefined;
  private maxErrorsPerMinute = 60; // 每分钟最大错误数限制
  private errorStatistics: ErrorStatistics = {
    total: 0,
    byType: {},
    bySeverity: {},
    byPhase: {},
    recentErrorRate: 0,
    suppressed: 0,
    reported: 0
  };
  private globalListeners: Array<[string, EventListener]> = [];

  /**
   * 构造函数
   * @param appId 应用ID
   */
  constructor(appId: string) {
    if (!appId) {
      throw new Error('App ID is required');
    }
    this.appId = appId;
    this.initializeErrorTypes();
    this.setupGlobalListeners();
    this.startErrorRateMonitor();
    this.errorRateWarningIssued = 0;
    this.batchReportSize = 10;
    this.maxHistorySize = 100;
  }
  
  /**
   * 初始化错误类型统计
   */
  private initializeErrorTypes(): void {
    const types: ErrorClassification['type'][] = [
      'network', 'resource', 'permission', 'sandbox', 'lifecycle', 
      'syntax', 'type', 'reference', 'timeout', 'runtime', 'unknown'
    ];
    
    types.forEach(type => {
      this.errorStatistics.byType[type] = 0;
    });
    
    const severities: ErrorClassification['severity'][] = ['low', 'medium', 'high', 'critical'];
    severities.forEach(severity => {
      this.errorStatistics.bySeverity[severity] = 0;
    });
  }
  
  /**
   * 设置全局错误监听器
   */
  private setupGlobalListeners(): void {
    // 确保只在浏览器环境中运行
    if (typeof window !== 'undefined') {
      // 保存原始监听器，便于清理
      this.globalListeners = [];
      
      // 监听未处理的Promise拒绝
      const rejectionHandler = (event: PromiseRejectionEvent) => {
        // 阻止默认行为以避免控制台警告重复
        event.preventDefault?.();
        
        const reason = event.reason || new Error('Unhandled promise rejection');
        this.handle(reason as Error, {
          phase: 'promise',
          additionalInfo: { 
            promiseRejection: true,
            // 尝试获取Promise链中的更多信息
            promiseDetails: event.promise ? 'Promise object received' : 'Promise object not available'
          }
        });
      };
      
      window.addEventListener('unhandledrejection', rejectionHandler);
      this.globalListeners.push(['unhandledrejection', rejectionHandler]);
      
      // 监听全局错误事件
      const errorHandler = (event: ErrorEvent) => {
        // 检查是否是资源加载错误
        const isResourceError = event.target instanceof HTMLScriptElement || 
                              event.target instanceof HTMLLinkElement || 
                              event.target instanceof HTMLImageElement;
        
        const context: ErrorContext = {
          phase: isResourceError ? 'resource_load' : 'global',
          additionalInfo: { 
            target: event.target instanceof Element ? event.target.tagName : String(event.target),
            filename: event.filename,
            lineno: event.lineno,
            colno: event.colno,
            isResourceError
          }
        };
        
        if (event.error) {
          this.handle(event.error, context);
        } else if (isResourceError) {
          // 为资源加载错误创建更有意义的错误对象
          const resourceType = event.target instanceof HTMLScriptElement ? 'script' :
                             event.target instanceof HTMLLinkElement ? 'style' : 'image';
          const resourceUrl = (event.target as any).src || (event.target as any).href || event.filename;
          const resourceError = new Error(`Failed to load ${resourceType}: ${resourceUrl}`);
          resourceError.name = 'ResourceLoadError';
          this.handle(resourceError, context);
        }
      };
      
      if (!window.__boneErrorHandlerRegistered) {
        window.__boneErrorHandlerRegistered = true;
        window.addEventListener('error', errorHandler);
        this.globalListeners.push(['error', errorHandler]);
      }
      
      // 增强的资源加载监控
      this.setupResourceErrorMonitoring();
    }
  }
  
  /**
   * 启动错误率监控定时器
   */
  private startErrorRateMonitor(): void {
    // 每分钟更新一次错误率统计
    this.errorRateMonitorTimer = setInterval(() => {
      if (this.isDestroyed) {
        clearInterval(this.errorRateMonitorTimer);
        return;
      }
      
      // 更新错误率统计
      this.getStatistics();
      
      // 清理过期的错误信息
      this.cleanupOldErrors();
    }, 60000); // 每分钟执行一次
  }
  
  /**
   * 清理过期的错误信息
   */
  private cleanupOldErrors(): void {
    const now = Date.now();
    const oneHourAgo = now - 3600000; // 一小时前
    
    // 清理错误历史中超过一小时的记录
    this.errorHistory = this.errorHistory.filter(item => item.timestamp > oneHourAgo);
    
    // 清理过期的节流信息
    this.cleanupThrottleInfo();
  }
  
  /**
   * 手动触发错误上报
   */
  triggerReport(): Promise<void> {
    if (this.isDestroyed || this.pendingReports.length === 0) {
      return Promise.resolve();
    }
    
    return this.processReportQueue();
  }
  
  /**
   * 获取错误历史记录
   * @param maxCount 最大返回数量
   */
  getErrorHistory(maxCount: number = 50): Array<{error: Error, context: ErrorContext, classification: ErrorClassification, timestamp: number}> {
    return this.errorHistory.slice(-maxCount);
  }
  
  /**
   * 手动抑制特定错误
   * @param error 错误对象
   * @param duration 抑制持续时间（毫秒）
   */
  suppressError(error: Error, duration: number = 60000): void {
    const errorKey = this.generateErrorKey(error);
    this.suppressedErrors.add(errorKey);
    
    // 设置定时器在指定时间后解除抑制
    setTimeout(() => {
      this.suppressedErrors.delete(errorKey);
    }, duration);
  }
  
  /**
   * 设置资源加载错误监控
   */
  private setupResourceErrorMonitoring(): void {
    if (typeof window === 'undefined') return;
    
    // 监控fetch API
    const originalFetch = window.fetch;
    if (originalFetch) {
      window.fetch = async (...args) => {
        try {
          const response = await originalFetch(...args);
          // 检查HTTP错误状态
          if (!response.ok) {
            this.handle(new Error(`Fetch failed: ${response.status} ${response.statusText}`), {
              phase: 'network',
              additionalInfo: {
                url: args[0] instanceof Request ? args[0].url : String(args[0]),
                method: args[0] instanceof Request ? args[0].method : 'GET',
                status: response.status
              }
            });
          }
          return response;
        } catch (error) {
          this.handle(error instanceof Error ? error : new Error(String(error)), {
            phase: 'network',
            additionalInfo: {
              url: args[0] instanceof Request ? args[0].url : String(args[0]),
              method: args[0] instanceof Request ? args[0].method : 'GET',
              fetchError: true
            }
          });
          throw error;
        }
      };
    }
    
    // 监控XMLHttpRequest
    if (typeof XMLHttpRequest !== 'undefined') {
      const originalXHROpen = XMLHttpRequest.prototype.open;
      const originalXHRSend = XMLHttpRequest.prototype.send;
      
      // 保存对当前ErrorHandler实例的引用，以便在XHR回调中使用
      const errorHandler = this;
      
      XMLHttpRequest.prototype.open = function(...args: any[]) {
        this._url = args[1];
        this._method = args[0];
        return originalXHROpen.apply(this, args);
      };
      
      XMLHttpRequest.prototype.send = function() {
        this.addEventListener('error', function() {
          if (this._url) {
            errorHandler.handle(new Error(`XHR failed: ${this.status} ${this.statusText}`), {
              phase: 'network',
              additionalInfo: {
                url: this._url,
                method: this._method,
                status: this.status,
                xhrError: true
              }
            });
          }
        });
        
        return originalXHRSend.apply(this, arguments);
      };
    }
  }
  
  /**
   * 启动错误率监控
   */
  private startErrorRateMonitor(): void {
    // 每分钟清理一次过期的错误时间戳并更新错误率
    this.errorRateMonitorTimer = setInterval(() => {
      if (this.isDestroyed) {
        clearInterval(this.errorRateMonitorTimer);
        return;
      }
      this.updateErrorRate();
    }, 60000);
  }
  
  /**
   * 更新错误率统计
   */
  private updateErrorRate(): void {
    const now = Date.now();
    const oneMinuteAgo = now - 60000;
    
    // 清理一分钟前的错误时间戳
    this.errorTimestamps = this.errorTimestamps.filter(timestamp => timestamp > oneMinuteAgo);
    
    // 更新错误率
    this.errorStatistics.recentErrorRate = this.errorTimestamps.length;
    
    // 检查是否超出错误率限制
    if (this.errorTimestamps.length > this.maxErrorsPerMinute) {
      console.warn(`[Bone Error] High error rate detected: ${this.errorTimestamps.length} errors per minute for app ${this.appId}`);
    }
  }

  /**
   * 处理错误
   * @param error 错误对象
   * @param context 错误上下文
   */
  handle(error: Error, context?: ErrorContext): void {
    if (!error || this.isDestroyed) return;
    
    // 创建错误唯一标识
    const errorKey = this.generateErrorKey(error, context);
    
    // 检查是否被抑制
    if (this.suppressedErrors.has(errorKey)) {
      this.errorStatistics.suppressed++;
      return;
    }
    
    try {
      // 构建完整的错误上下文
      const timestamp = Date.now();
      const fullContext: ErrorContext = {
        appId: this.appId,
        timestamp,
        phase: context?.phase || 'unknown',
        appInfo: context?.appInfo,
        additionalInfo: context?.additionalInfo
      };

      // 错误分类
      const classification = this.classifyError(error, fullContext);
      
      // 应用错误节流
      if (this.shouldThrottleError(errorKey, classification)) {
        this.errorStatistics.suppressed++;
        return;
      }

      // 更新错误统计
      this.updateStatistics(classification, fullContext, timestamp);

      // 记录错误到历史
      this.recordError(error, fullContext, classification);

      // 记录错误到控制台
      this.logError(error, fullContext, classification);

      // 触发错误事件
      this.emitErrorEvent(error, fullContext, classification);

      // 上报错误（异步队列处理）
      this.enqueueReport(error, fullContext, classification);
      
    } catch (handlerError) {
      // 防止错误处理器本身出错导致的死循环
      console.error('[Bone Error] Error handler internal error:', handlerError);
      this.suppressError(errorKey);
    }
  }
  
  /**
   * 生成错误唯一标识
   */
  private generateErrorKey(error: Error, context?: ErrorContext): string {
    // 基于错误消息和堆栈生成唯一标识
    const message = error.message || '';
    const stackHash = this.hashString(error.stack || '');
    const phase = context?.phase || '';
    
    return `${message.substring(0, 100)}:${stackHash}:${phase}`;
  }
  
  /**
   * 简单的字符串哈希函数
   */
  private hashString(str: string): string {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // 转换为32位整数
    }
    return Math.abs(hash).toString(16);
  }
  
  /**
   * 更新错误率统计
   */
  private updateErrorRate(): void {
    const now = Date.now();
    
    // 移除过期的错误时间戳（超过1分钟）
    this.errorTimestamps = this.errorTimestamps.filter(timestamp => now - timestamp < 60000);
    
    // 计算每分钟错误率
    this.errorStatistics.recentErrorRate = this.errorTimestamps.length;
  }
  
  /**
   * 抑制错误（防止重复处理）
   */
  private suppressError(errorKey: string): void {
    this.suppressedErrors.add(errorKey);
    
    // 限制抑制集合大小
    if (this.suppressedErrors.size > 1000) {
      // 移除最旧的抑制项
      const oldestKey = this.suppressedErrors.values().next().value;
      this.suppressedErrors.delete(oldestKey);
    }
  }
  
  /**
   * 更新错误统计
   */
  private updateStatistics(classification: ErrorClassification, context: ErrorContext, timestamp: number): void {
    // 更新总错误数
    this.errorStatistics.total++;
    
    // 更新按类型统计
    this.errorStatistics.byType[classification.type] = 
      (this.errorStatistics.byType[classification.type] || 0) + 1;
    
    // 更新按严重程度统计
    this.errorStatistics.bySeverity[classification.severity] = 
      (this.errorStatistics.bySeverity[classification.severity] || 0) + 1;
    
    // 更新按阶段统计
    const phase = context.phase || 'unknown';
    this.errorStatistics.byPhase[phase] = 
      (this.errorStatistics.byPhase[phase] || 0) + 1;
    
    // 更新时间戳
    this.errorTimestamps.push(timestamp);
    this.errorStatistics.lastErrorTime = timestamp;
    
    if (!this.errorStatistics.firstErrorTime) {
      this.errorStatistics.firstErrorTime = timestamp;
    }
    
    // 立即更新错误率
    this.updateErrorRate();
    
    // 检查错误率阈值，超过阈值时发出警告
    this.checkErrorRateThreshold();
  }
  
  /**
   * 检查错误率阈值
   */
  private checkErrorRateThreshold(): void {
    const stats = this.getStatistics();
    const criticalThreshold = 10; // 每分钟10个错误为严重阈值
    
    if (stats.recentErrorRate > criticalThreshold) {
      if (!this.errorRateWarningIssued || Date.now() - this.errorRateWarningIssued > 60000) {
        console.warn(`[Bone ERROR RATE WARNING] App ${this.appId} has high error rate: ${stats.recentErrorRate} errors per minute`);
        this.errorRateWarningIssued = Date.now();
        
        // 触发错误率过高事件
        this.emitErrorEvent(new Error('High error rate detected'), {
          appId: this.appId,
          timestamp: Date.now(),
          phase: 'error_monitoring',
          additionalInfo: { errorRate: stats.recentErrorRate }
        }, {
          type: 'runtime',
          severity: 'high',
          message: `High error rate: ${stats.recentErrorRate} errors per minute`,
          isExpected: false,
          isFatal: false
        });
      }
    }
  }
  
  /**
   * 分类错误
   * @param error 错误对象
   * @param context 错误上下文
   * @returns 错误分类
   */
  private classifyError(error: Error, context: ErrorContext): ErrorClassification {
    const message = error.message?.toLowerCase() || '';
    let type: ErrorClassification['type'] = 'unknown';
    let severity: ErrorClassification['severity'] = 'low';
    let isExpected = false;
    let isFatal = false;
    let errorCode = '';
    
    // 基于错误类型和消息进行智能分类
    if (error instanceof SyntaxError) {
      type = 'syntax';
      severity = 'critical';
      isExpected = false;
      isFatal = true;
    } else if (error instanceof TypeError) {
      // 进一步细分类TypeError
      if (message.includes('network') || message.includes('fetch') || message.includes('http')) {
        type = 'network';
        severity = 'medium';
        isExpected = true;
      } else if (message.includes('null') || message.includes('undefined') || message.includes('cannot read')) {
        type = 'reference';
        severity = 'high';
        isExpected = false;
        isFatal = true;
      } else {
        type = 'type';
        severity = 'medium';
        isExpected = false;
      }
    } else if (error instanceof ReferenceError) {
      type = 'reference';
      severity = 'high';
      isExpected = false;
      isFatal = true;
    } else if (error instanceof SecurityError || this.isPermissionError(error)) {
      type = 'permission';
      severity = 'high';
      isExpected = false;
    } else if (this.isNetworkError(error)) {
      type = 'network';
      severity = 'medium';
      isExpected = true;
    } else if (this.isResourceError(error)) {
      type = 'resource';
      severity = 'medium';
      isExpected = true;
    } else if (message.includes('timeout') || message.includes('超时')) {
      type = 'timeout';
      severity = 'medium';
      isExpected = true;
    }
    
    // 基于上下文进一步分类
    if (context.phase === 'sandbox' || message.includes('sandbox')) {
      type = 'sandbox';
      severity = 'high';
      isFatal = true;
    } else if (['bootstrap', 'mount', 'unmount', 'destroy'].includes(context.phase || '')) {
      type = 'lifecycle';
      severity = 'high';
      isFatal = true;
    }
    
    // 提取错误码（如果有）
    const errorCodeMatch = message.match(/error\s*code[:\s]*([A-Z0-9_-]+)/i);
    if (errorCodeMatch) {
      errorCode = errorCodeMatch[1];
    }
    
    // 致命错误判断
    if (type === 'syntax' || 
        type === 'sandbox' || 
        type === 'lifecycle' ||
        (type === 'reference' && message.includes('cannot read')) ||
        (context.phase === 'bootstrap' || context.phase === 'load')) {
      isFatal = true;
      severity = 'critical';
    }
    
    // 资源加载错误在特定阶段可能更严重
    if (type === 'resource' && (context.phase === 'bootstrap' || context.phase === 'load')) {
      severity = 'high';
      isFatal = true;
    }
    
    return {
      type,
      severity,
      message: error.message || 'Unknown error',
      isExpected,
      isFatal,
      errorCode: errorCode || undefined
    };
  }
  
  /**
   * 检查是否应该节流错误
   * @param errorKey 错误唯一标识
   * @param classification 错误分类
   * @returns 是否节流
   */
  private shouldThrottleError(errorKey: string, classification: ErrorClassification): boolean {
    const now = Date.now();
    const throttleInfo = this.errorThrottling.get(errorKey);
    
    // 关键错误不节流
    if (classification.severity === 'critical') {
      return false;
    }
    
    // 智能节流策略
    if (throttleInfo) {
      // 根据错误类型和严重程度动态调整节流间隔
      let interval = this.throttlingInterval;
      if (classification.severity === 'low') {
        interval = this.throttlingInterval * 5; // 低优先级错误节流更严格
      } else if (classification.severity === 'high') {
        interval = this.throttlingInterval / 2; // 高优先级错误节流更宽松
      }
      
      // 根据错误频率动态调整
      if (throttleInfo.count > 10) {
        interval *= 2; // 频繁错误增加节流间隔
      }
      
      if (now - throttleInfo.lastTime < interval) {
        // 增加计数但不处理
        throttleInfo.count++;
        throttleInfo.lastTime = now;
        return true;
      }
      
      // 重置计数
      throttleInfo.count = 1;
      throttleInfo.lastTime = now;
    } else {
      // 首次出现的错误
      this.errorThrottling.set(errorKey, { count: 1, lastTime: now });
    }
    
    // 清理过期的节流信息（每100个新错误清理一次）
    if (this.errorThrottling.size % 100 === 0) {
      this.cleanupThrottleInfo();
    }
    
    return false;
  }
  
  /**
   * 清理过期的节流信息
   */
  private cleanupThrottleInfo(): void {
    const now = Date.now();
    const expiredThreshold = now - (this.throttlingInterval * 10);
    
    for (const [key, info] of this.errorThrottling.entries()) {
      if (info.lastTime < expiredThreshold) {
        this.errorThrottling.delete(key);
      }
    }
  }
  
  /**
   * 将错误加入上报队列
   * @param error 错误对象
   * @param context 错误上下文
   * @param classification 错误分类
   */
  private enqueueReport(error: Error, context: ErrorContext, classification: ErrorClassification): void {
    // 只有关键错误和非预期错误才上报
    if (classification.severity === 'low' && classification.isExpected) {
      return;
    }
    
    this.pendingReports.push({ error, context, classification });
    
    // 立即开始处理上报队列
    if (!this.reportProcessing) {
      void this.processReportQueue();
    }
  }
  
  /**
   * 处理上报队列
   */
  private async processReportQueue(): Promise<void> {
    if (this.reportProcessing || this.isDestroyed) return;
    
    this.reportProcessing = true;
    
    try {
      while (this.pendingReports.length > 0) {
        const now = Date.now();
        
        // 限制上报频率
        if (now - this.lastReportTime < this.reportInterval) {
          await new Promise(resolve => setTimeout(resolve, this.reportInterval - (now - this.lastReportTime)));
        }
        
        // 批量处理错误上报
        const batchSize = Math.min(this.batchReportSize, this.pendingReports.length);
        const batch = this.pendingReports.splice(0, batchSize);
        
        try {
          await this.reportBatchErrors(batch);
          this.errorStatistics.reported += batch.length;
          this.lastReportTime = now;
        } catch (batchError) {
          console.error('Failed to report error batch:', batchError);
          // 失败时将关键错误放回队列，非关键错误丢弃
          const criticalErrors = batch.filter(item => item.classification.severity === 'critical');
          this.pendingReports = [...criticalErrors, ...this.pendingReports];
          
          // 指数退避重试
          await new Promise(resolve => setTimeout(resolve, this.reportInterval * 2));
        }
      }
    } catch (error) {
      console.error('Error processing error report queue:', error);
    } finally {
      this.reportProcessing = false;
    }
  }
  
  /**
   * 批量上报错误
   */
  private async reportBatchErrors(batch: Array<{error: Error, context: ErrorContext, classification: ErrorClassification}>): Promise<void> {
    if (batch.length === 0) return;
    
    const batchErrorInfo = batch.map(({ error, context, classification }) => 
      this.prepareErrorInfo(error, context, classification)
    );
    
    // 使用reportUrl批量上报
    if (this.reportUrl) {
      await this.fetchBatchReport(this.reportUrl, batchErrorInfo);
    }
    
    // 检查是否有全局错误上报函数
    if (typeof window !== 'undefined' && (window as any).__boneErrorReporter) {
      (window as any).__boneErrorReporter.reportBatch(batchErrorInfo);
    }
    
    console.log(`[Error Report] Batch reported ${batch.length} errors for app ${this.appId}`);
  }

  /**
   * 记录错误到历史
   * @param error 错误对象
   * @param context 错误上下文
   * @param classification 错误分类
   */
  private recordError(error: Error, context: ErrorContext, classification: ErrorClassification): void {
    // 克隆错误对象以避免外部修改
    const errorCopy = Object.create(Object.getPrototypeOf(error));
    Object.assign(errorCopy, error);
    
    // 对于大型堆栈，只保留关键部分
    if (errorCopy.stack) {
      const stackLines = errorCopy.stack.split('\n');
      // 只保留前10行堆栈信息，避免内存占用过大
      if (stackLines.length > 10) {
        errorCopy.stack = stackLines.slice(0, 10).join('\n') + '\n... more stack frames omitted ...';
      }
    }
    
    this.errorHistory.push({
      error: errorCopy,
      context: this.sanitizeContext({ ...context }),
      classification,
      timestamp: Date.now()
    });

    // 限制历史记录数量
    if (this.errorHistory.length > this.maxHistorySize) {
      this.errorHistory.shift();
    }
  }

  /**
   * 记录错误到控制台
   * @param error 错误对象
   * @param context 错误上下文
   * @param classification 错误分类
   */
  private logError(error: Error, context: ErrorContext, classification: ErrorClassification): void {
    // 开发环境才输出详细日志
    const isDev = process.env.NODE_ENV === 'development' || !process.env.NODE_ENV;
    
    // 根据严重程度使用不同的日志级别
    if (classification.severity === 'critical') {
      console.error(
        `[Bone CRITICAL] App: ${this.appId}, Type: ${classification.type}, Phase: ${context.phase}`,
        error
      );
      if (isDev) {
        console.groupCollapsed('Error Details:');
        console.log('Context:', context);
        console.log('Classification:', classification);
        if (error.stack) {
          console.log('Stack:', error.stack);
        }
        console.groupEnd();
      }
    } else if (classification.severity === 'high') {
      console.error(
        `[Bone ERROR] App: ${this.appId}, Type: ${classification.type}, Phase: ${context.phase}`,
        error.message
      );
    } else if (classification.severity === 'medium') {
      console.warn(
        `[Bone WARNING] App: ${this.appId}, Type: ${classification.type}, Phase: ${context.phase}`,
        error.message
      );
    } else if (isDev) {
      console.info(
        `[Bone INFO] App: ${this.appId}, Type: ${classification.type}, Phase: ${context.phase}`,
        error.message
      );
    }
  }

  /**
   * 触发错误事件
   */
  private emitErrorEvent(error: Error, context: ErrorContext, classification: ErrorClassification): void {
    if (typeof window === 'undefined' || this.isDestroyed) return;
    
    try {
      const event = new CustomEvent('bone-error', {
        detail: {
          error: this.sanitizeErrorForEvent(error),
          context: this.sanitizeContext(context),
          classification,
          appId: this.appId
        },
        bubbles: true,
        cancelable: true
      });
      
      window.dispatchEvent(event);
    } catch (eventError) {
      console.error('[Bone Error] Failed to emit error event:', eventError);
    }
  }
  
  /**
   * 清理错误对象，用于事件传递
   */
  private sanitizeErrorForEvent(error: Error): {message: string, name?: string, stack?: string} {
    return {
      message: error.message || 'Unknown error',
      name: error.name,
      stack: this.sanitizeStack(error.stack)
    };
  }

  /**
   * 上报错误
   * @param error 错误对象
   * @param context 错误上下文
   * @param classification 错误分类
   */
  private async reportError(error: Error, context: ErrorContext, classification: ErrorClassification): Promise<void> {
    try {
      const errorInfo = this.prepareErrorInfo(error, context, classification);
      
      console.error(`[Error Report] ${this.formatErrorMessage(error, context)}`);
      
      // 使用reportUrl上报
      if (this.reportUrl) {
        await this.fetchReport(this.reportUrl, errorInfo);
      }
      
      // 检查是否有全局错误上报函数
      if (typeof window !== 'undefined' && (window as any).__boneErrorReporter) {
        (window as any).__boneErrorReporter.report(errorInfo);
      }
    } catch (reportError) {
      console.warn('Failed to report error:', reportError);
    }
  }
  
  /**
   * 准备错误信息
   * @param error 错误对象
   * @param context 错误上下文
   * @param classification 错误分类
   */
  private prepareErrorInfo(error: Error, context: ErrorContext, classification: ErrorClassification): any {
    // 清理上下文以避免敏感信息泄露
    const sanitizedContext = this.sanitizeContext(context);
    
    return {
      error,
      context: sanitizedContext,
      classification,
      timestamp: Date.now(),
      userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
      url: typeof window !== 'undefined' ? window.location.href : '',
      // 生成错误指纹用于去重
      fingerprint: this.generateErrorFingerprint(error, classification)
    };
  }
  
  /**
   * 生成错误指纹用于去重
   */
  private generateErrorFingerprint(error: Error, classification: ErrorClassification): string {
    // 基于错误类型、消息和堆栈的简化部分生成指纹
    const message = error.message || '';
    let stack = error.stack || '';
    
    // 提取堆栈中的关键帧
    if (stack) {
      const stackLines = stack.split('\n').filter(line => line.includes('at '));
      stack = stackLines.slice(0, 2).join(''); // 只取前两帧
    }
    
    // 生成简单的哈希
    let hash = 0;
    const content = `${classification.type}:${message}:${stack}`;
    for (let i = 0; i < content.length; i++) {
      const char = content.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // 转换为32位整数
    }
    
    return Math.abs(hash).toString(16);
  }
  
  /**
   * 清理堆栈信息
   */
  private sanitizeStack(stack: string | undefined): string | undefined {
    if (!stack) return undefined;
    
    // 移除可能包含敏感路径的行
    const sanitizedStack = stack.split('\n').map(line => {
      // 移除文件系统路径，只保留相对路径
      return line.replace(/at\s+(\w+)\s+\([^)]*\/(\w+\.\w+)[^)]*\)/g, 'at $1 ($2)')
                .replace(/at\s+[^)]*\/(\w+\.\w+)[^)]*/g, 'at $1');
    }).join('\n');
    
    // 限制堆栈长度
    if (sanitizedStack.length > 2000) {
      return sanitizedStack.substring(0, 2000) + '... (truncated)';
    }
    
    return sanitizedStack;
  }
  
  /**
   * 清理对象，移除敏感信息
   */
  private sanitizeObject(obj: Record<string, any>): Record<string, any> {
    const sensitiveKeys = ['password', 'token', 'secret', 'key', 'auth', 'credentials', 'cookie', 'authorization'];
    const result: Record<string, any> = {};
    
    for (const [key, value] of Object.entries(obj)) {
      const lowercaseKey = key.toLowerCase();
      
      // 跳过敏感字段
      if (sensitiveKeys.some(sensitive => lowercaseKey.includes(sensitive))) {
        result[key] = '[REDACTED]';
        continue;
      }
      
      // 限制嵌套对象深度
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        // 只保留一层嵌套
        result[key] = this.sanitizeObjectShallow(value);
      } else if (Array.isArray(value)) {
        // 数组只保留长度信息
        result[key] = `Array[${value.length}]`;
      } else if (typeof value === 'string' && value.length > 500) {
        // 长字符串截断
        result[key] = value.substring(0, 500) + '...';
      } else {
        result[key] = value;
      }
    }
    
    return result;
  }
  
  /**
   * 浅度清理对象
   */
  private sanitizeObjectShallow(obj: Record<string, any>): Record<string, any> {
    const result: Record<string, any> = {};
    
    for (const [key, value] of Object.entries(obj)) {
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' || value === null) {
        result[key] = value;
      } else {
        result[key] = `[${typeof value}]`;
      }
    }
    
    return result;
  }
  
  /**
   * 清理上下文对象，移除可能的敏感信息
   * @param context 错误上下文
   * @returns 清理后的上下文
   */
  private sanitizeContext(context: ErrorContext): ErrorContext {
    const sanitized: ErrorContext = {
      appId: context.appId,
      timestamp: context.timestamp,
      phase: context.phase
    };
    
    // 只包含必要的信息，移除可能的敏感数据
    if (context.appInfo && typeof context.appInfo === 'object' && context.appInfo !== null) {
      sanitized.appInfo = this.sanitizeObject(context.appInfo);
    }
    
    if (context.additionalInfo && typeof context.additionalInfo === 'object' && context.additionalInfo !== null) {
      sanitized.additionalInfo = this.sanitizeObject(context.additionalInfo);
    }
    
    return sanitized;
  }
  
  /**
   * 发送批量错误上报请求
   * @param url 上报URL
   * @param data 上报数据
   */
  private async fetchBatchReport(url: string, data: any[]): Promise<void> {
    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          batch: data,
          appId: this.appId,
          timestamp: Date.now(),
          environment: process.env.NODE_ENV || 'unknown',
          errorCount: data.length
        }),
        credentials: 'include',
        // 添加keepalive以支持页面关闭时也能上报
        keepalive: data.length <= 5 // 限制keepalive只能用于小批量
      });
      
      if (!response.ok) {
        throw new Error(`Report server error: ${response.status} ${response.statusText}`);
      }
    } catch (error) {
      throw new Error(`Failed to fetch error report: ${error instanceof Error ? error.message : 'Unknown error'}`);
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
    classification: ErrorClassification;
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
   * 获取错误统计
   */
  getErrorStats(): Record<string, number> {
    const stats: Record<string, number> = {};
    this.errorStats.forEach((count, type) => {
      stats[type] = count;
    });
    return stats;
  }
  
  /**
   * 获取错误统计信息
   */
  getStatistics(): ErrorStatistics {
    // 计算最近错误率（每分钟）
    const now = Date.now();
    if (this.errorStatistics.lastErrorTime && this.errorStatistics.firstErrorTime) {
      const durationMinutes = Math.max(1, (now - this.errorStatistics.firstErrorTime) / 60000);
      this.errorStatistics.recentErrorRate = Math.round((this.errorStatistics.total / durationMinutes) * 100) / 100;
    }
    
    return { ...this.errorStatistics };
  }
  
  /**
   * 设置错误上报URL
   * @param url 上报URL
   */
  setReportUrl(url: string): void {
    this.reportUrl = url;
  }
  
  /**
   * 设置错误上报间隔
   * @param interval 上报间隔（毫秒）
   */
  setReportInterval(interval: number): void {
    if (interval > 0) {
      this.reportInterval = interval;
    }
  }
  
  /**
   * 设置错误节流间隔
   * @param interval 节流间隔（毫秒）
   */
  setThrottlingInterval(interval: number): void {
    if (interval > 0) {
      this.throttlingInterval = interval;
    }
  }
  
  /**
   * 清空错误统计
   */
  clearErrorStats(): void {
    this.errorStats.clear();
  }
  
  /**
   * 销毁错误处理器
   */
  destroy(): void {
    if (this.isDestroyed) return;
    
    this.isDestroyed = true;
    
    // 清理资源
    this.errorHistory = [];
    this.errorThrottling.clear();
    this.suppressedErrors.clear();
    this.errorStats.clear();
    this.pendingReports = [];
    
    // 清理全局监听器
    if (typeof window !== 'undefined' && this.globalListeners) {
      for (const [eventType, handler] of this.globalListeners) {
        window.removeEventListener(eventType as string, handler as EventListener);
      }
      this.globalListeners = [];
      
      if (window.__boneErrorHandlerRegistered) {
        delete window.__boneErrorHandlerRegistered;
      }
    }
    
    // 停止错误率监控定时器
    if (this.errorRateMonitorTimer) {
      clearInterval(this.errorRateMonitorTimer);
      this.errorRateMonitorTimer = undefined;
    }
    
    console.log(`[Bone ErrorHandler] Destroyed for app ${this.appId}`);
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
   * 格式化错误信息
   * @param error 错误对象
   */
  formatError(error: Error): string {
    return `${error.name}: ${error.message}\n${error.stack || 'No stack trace available'}`;
  }

  /**
   * 判断是否为网络错误
   */
  private isNetworkError(error: Error): boolean {
    const message = error.message.toLowerCase() || '';
    const networkKeywords = ['network', 'fetch', 'http', 'https', 'timeout', 'connection', 'socket', 'request', 'xhr'];
    return networkKeywords.some(keyword => message.includes(keyword));
  }

  /**
   * 判断是否为资源错误
   */
  private isResourceError(error: Error): boolean {
    const message = error.message.toLowerCase() || '';
    const resourceKeywords = ['failed to load resource', '无法加载资源', 'script error', 'stylesheet', 'image failed'];
    return resourceKeywords.some(keyword => message.includes(keyword));
  }

  /**
   * 判断是否为权限错误
   */
  private isPermissionError(error: Error): boolean {
    const message = error.message.toLowerCase() || '';
    const permissionKeywords = ['permission', '权限', 'security', 'access denied', 'unauthorized'];
    return permissionKeywords.some(keyword => message.includes(keyword)) || 
           error instanceof SecurityError;
  }
}