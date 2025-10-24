/**
 * 性能管理工具类
 * 提供统一的性能指标收集和分析功能
 */

/**
 * 性能指标记录
 */
export interface MetricRecord {
  name: string;
  duration: number;
  timestamp: number;
  details?: Record<string, any>;
}

/**
 * 指标统计数据
 */
export interface MetricStats {
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

/**
 * 性能指标集合
 */
export interface PerformanceMetrics {
  [key: string]: MetricStats;
}

/**
 * 性能管理器
 * 集中管理性能监控相关功能
 */
export class PerformanceManager {
  private metrics: Map<string, MetricRecord[]> = new Map();
  private operationTimers: Map<string, number> = new Map();
  private static instance: PerformanceManager | null = null;

  /**
   * 获取单例实例
   */
  public static getInstance(): PerformanceManager {
    if (!PerformanceManager.instance) {
      PerformanceManager.instance = new PerformanceManager();
    }
    return PerformanceManager.instance;
  }

  /**
   * 记录性能指标
   * @param name 指标名称
   * @param duration 持续时间（毫秒）
   * @param details 附加信息
   */
  public record(name: string, duration: number, details?: Record<string, any>): void {
    if (!this.metrics.has(name)) {
      this.metrics.set(name, []);
    }

    const record: MetricRecord = {
      name,
      duration,
      timestamp: Date.now(),
      details
    };

    this.metrics.get(name)!.push(record);
  }

  /**
   * 开始计时
   * @param operation 操作名称
   */
  public startTimer(operation: string): void {
    this.operationTimers.set(operation, Date.now());
  }

  /**
   * 结束计时并记录
   * @param operation 操作名称
   * @param details 附加信息
   * @returns 持续时间
   */
  public endTime(operation: string, details?: Record<string, any>): number {
    const startTime = this.operationTimers.get(operation);
    if (!startTime) {
      console.warn(`Timer not found for operation: ${operation}`);
      return 0;
    }

    this.operationTimers.delete(operation);
    const duration = Date.now() - startTime;
    this.record(operation, duration, details);
    return duration;
  }

  /**
   * 报告成功事件
   * @param event 事件名称
   * @param details 附加信息
   */
  public reportSuccess(event: string, details?: Record<string, any>): void {
    this.record(`${event}-success`, 0, details);
  }

  /**
   * 报告失败事件
   * @param event 事件名称
   * @param error 错误对象
   * @param details 附加信息
   */
  public reportFailure(event: string, error: Error, details?: Record<string, any>): void {
    this.record(`${event}-failure`, 0, {
      ...details,
      errorMessage: error.message,
      errorName: error.name
    });
  }

  /**
   * 获取所有指标统计
   */
  public getMetrics(): PerformanceMetrics {
    const result: PerformanceMetrics = {};

    this.metrics.forEach((records, name) => {
      const durations = records.map(r => r.duration).sort((a, b) => a - b);
      const sum = durations.reduce((acc, val) => acc + val, 0);
      const count = durations.length;
      const avg = count > 0 ? sum / count : 0;
      
      result[name] = {
        count,
        avg,
        min: count > 0 ? durations[0] : 0,
        max: count > 0 ? durations[durations.length - 1] : 0,
        p95: this.calculatePercentile(durations, 95),
        p99: this.calculatePercentile(durations, 99),
        last: count > 0 ? durations[durations.length - 1] : 0,
        total: sum,
        firstRecord: count > 0 ? records[0].timestamp : undefined,
        lastRecord: count > 0 ? records[records.length - 1].timestamp : undefined
      };
    });

    return result;
  }

  /**
   * 计算百分位数
   * @param values 排序后的数值数组
   * @param percentile 百分位（如95、99）
   */
  private calculatePercentile(values: number[], percentile: number): number {
    if (values.length === 0) return 0;
    const index = Math.ceil(values.length * percentile / 100) - 1;
    return values[Math.max(0, Math.min(index, values.length - 1))];
  }

  /**
   * 导出指标数据
   */
  public exportMetrics(): any {
    return this.getMetrics();
  }

  /**
   * 清空所有指标
   */
  public clearMetrics(): void {
    this.metrics.clear();
  }

  /**
   * 检查指标是否超过阈值
   * @param name 指标名称
   * @param threshold 阈值
   */
  public isMetricAboveThreshold(name: string, threshold: number): boolean {
    const metrics = this.getMetrics();
    const metric = metrics[name];
    return metric ? metric.avg > threshold : false;
  }

  /**
   * 获取所有指标名称
   */
  public getMetricNames(): string[] {
    return Array.from(this.metrics.keys());
  }

  /**
   * 获取指标历史记录
   * @param name 指标名称
   * @param limit 限制数量
   */
  public getMetricHistory(name: string, limit: number = 10): MetricRecord[] {
    const records = this.metrics.get(name) || [];
    return records.slice(-limit);
  }

  /**
   * 测量同步函数执行时间
   * @param name 指标名称
   * @param fn 要测量的函数
   * @param details 附加信息
   */
  public measureFunction<T>(name: string, fn: () => T, details?: Record<string, any>): T {
    const startTime = Date.now();
    try {
      return fn();
    } finally {
      const duration = Date.now() - startTime;
      this.record(name, duration, details);
    }
  }

  /**
   * 测量异步函数执行时间
   * @param name 指标名称
   * @param fn 要测量的异步函数
   * @param details 附加信息
   */
  public async measureAsyncFunction<T>(name: string, fn: () => Promise<T>, details?: Record<string, any>): Promise<T> {
    const startTime = Date.now();
    try {
      return await fn();
    } finally {
      const duration = Date.now() - startTime;
      this.record(name, duration, details);
    }
  }
}
