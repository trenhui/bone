import { getEventBus } from './shared/event-bus';

// 定义指标记录接口
export interface MetricRecord {
  name: string;
  duration: number;
  timestamp: number;
  details?: Record<string, any>;
}

// 定义指标统计接口
interface MetricStats {
  count: number;
  avg: number;
  min: number;
  max: number;
  p95: number;
  p99: number;
  last: number;
  total: number;
  firstRecord?: number;
  lastRecord?: number;
}

// 定义性能指标接口
export interface PerformanceMetrics {
  [key: string]: MetricStats;
}

/**
 * 微应用性能监控器
 * 针对微应用的特定性能监控
 */
export class PerformanceMonitor {
  private appId: string;
  private eventBus = getEventBus();
  private metrics: Map<string, MetricRecord[]> = new Map();
  private operationTimers: Map<string, number> = new Map();
  private startTime: number = Date.now();

  /**
   * 构造函数
   * @param appId 应用ID
   */
  constructor(appId: string) {
    this.appId = appId;
  }

  /**
   * 记录性能指标
   * @param name 指标名称
   * @param duration 持续时间（毫秒）
   * @param details 额外信息
   */
  record(name: string, duration: number, details?: Record<string, any>): void {
    const record: MetricRecord = {
      name,
      duration,
      timestamp: Date.now(),
      details
    };

    if (!this.metrics.has(name)) {
      this.metrics.set(name, []);
    }

    this.metrics.get(name)!.push(record);
    
    // 触发性能指标记录事件
    this.eventBus.emit('performance:record', {
      appId: this.appId,
      record
    });
  }

  /**
   * 开始计时
   * @param operation 操作名称
   */
  startTimer(operation: string): void {
    this.operationTimers.set(operation, performance.now());
  }

  /**
   * 结束计时
   * @param operation 操作名称
   * @param details 附加信息
   * @returns 持续时间
   */
  endTime(operation: string, details?: Record<string, any>): number {
    const startTime = this.operationTimers.get(operation);
    
    if (!startTime) {
      console.warn(`No timer found for operation: ${operation}`);
      return 0;
    }

    this.operationTimers.delete(operation);
    const duration = performance.now() - startTime;
    
    this.record(operation, duration, details);
    return duration;
  }

  /**
   * 报告成功事件
   * @param event 事件名称
   * @param details 额外信息
   */
  reportSuccess(event: string, details?: Record<string, any>): void {
    this.eventBus.emit('performance:success', {
      appId: this.appId,
      event,
      timestamp: Date.now(),
      details
    });
  }

  /**
   * 报告失败事件
   * @param event 事件名称
   * @param error 错误信息
   * @param details 额外信息
   */
  reportFailure(event: string, error: Error, details?: Record<string, any>): void {
    this.eventBus.emit('performance:failure', {
      appId: this.appId,
      event,
      error: {
        name: error.name,
        message: error.message
      },
      timestamp: Date.now(),
      details
    });
  }

  /**
   * 获取性能指标
   */
  getMetrics(): PerformanceMetrics {
    const metrics: PerformanceMetrics = {} as PerformanceMetrics;
    const now = Date.now();
    
    this.metrics.forEach((records, name) => {
      const durations = records.map(r => r.duration);
      const total = durations.reduce((sum, d) => sum + d, 0);
      const count = records.length;
      
      metrics[name] = {
        count,
        avg: total / count,
        min: Math.min(...durations),
        max: Math.max(...durations),
        p95: this.calculatePercentile(durations, 95),
        p99: this.calculatePercentile(durations, 99),
        last: durations[durations.length - 1],
        total,
        firstRecord: records[0]?.timestamp,
        lastRecord: records[records.length - 1]?.timestamp
      };
    });

    // 添加应用运行时间指标
    metrics.appRuntime = {
      count: 1,
      avg: now - this.startTime,
      min: now - this.startTime,
      max: now - this.startTime,
      p95: now - this.startTime,
      p99: now - this.startTime,
      last: now - this.startTime,
      total: now - this.startTime,
      firstRecord: this.startTime,
      lastRecord: now
    };

    return metrics;
  }

  /**
   * 计算百分位数
   * @param values 数值数组
   * @param percentile 百分位（0-100）
   */
  private calculatePercentile(values: number[], percentile: number): number {
    if (values.length === 0) return 0;

    const sorted = [...values].sort((a, b) => a - b);
    const index = Math.ceil((percentile / 100) * sorted.length) - 1;
    return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
  }

  /**
   * 导出指标数据
   */
  exportMetrics(): any {
    return {
      appId: this.appId,
      timestamp: Date.now(),
      metrics: this.getMetrics()
    };
  }

  /**
   * 清空性能指标
   */
  clearMetrics(): void {
    this.metrics.clear();
    this.operationTimers.clear();
    this.startTime = Date.now();
  }

  /**
   * 检查性能指标是否超出阈值
   * @param name 指标名称
   * @param threshold 阈值（毫秒）
   */
  isMetricAboveThreshold(name: string, threshold: number): boolean {
    const metrics = this.getMetrics();
    const metric = metrics[name];
    return !!metric && metric.last > threshold;
  }

  /**
   * 获取所有指标名称
   */
  getMetricNames(): string[] {
    return Array.from(this.metrics.keys());
  }

  /**
   * 获取指定指标的最近记录
   * @param name 指标名称
   * @param limit 限制数量
   */
  getMetricHistory(name: string, limit: number = 10): MetricRecord[] {
    const records = this.metrics.get(name) || [];
    return records.slice(-limit);
  }

  /**
   * 测量函数执行性能
   * @param name 指标名称
   * @param fn 要执行的函数
   * @param details 额外信息
   */
  measureFunction<T>(name: string, fn: () => T, details?: Record<string, any>): T {
    const start = performance.now();
    try {
      return fn();
    } finally {
      const duration = performance.now() - start;
      this.record(name, duration, details);
    }
  }

  /**
   * 测量异步函数执行性能
   * @param name 指标名称
   * @param fn 要执行的异步函数
   * @param details 额外信息
   */
  async measureAsyncFunction<T>(name: string, fn: () => Promise<T>, details?: Record<string, any>): Promise<T> {
    const start = performance.now();
    try {
      return await fn();
    } finally {
      const duration = performance.now() - start;
      this.record(name, duration, details);
    }
  }
}