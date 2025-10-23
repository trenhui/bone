// 性能监控系统核心实现
export const BONE_PERFORMANCE_VERSION = '1.0.0';

// 性能指标类型定义
export interface PerformanceMetrics {
  navigationStart: number;
  resourceLoadTime: number;
  domContentLoaded: number;
  completeLoadTime: number;
  firstPaint: number;
  firstContentfulPaint: number;
  largestContentfulPaint: number;
  cumulativeLayoutShift: number;
  timeToInteractive: number;
}

// 微应用加载指标
export interface AppLoadMetrics {
  appName: string;
  loadStart: number;
  loadEnd: number;
  loadTime: number;
  mountStart: number;
  mountEnd: number;
  mountTime: number;
  firstRender: number;
  resources: Array<{
    url: string;
    loadTime: number;
    size: number;
  }>;
}

// 性能监控配置
export interface PerformanceMonitorConfig {
  sampleRate?: number; // 采样率 0-1
  reportUrl?: string; // 上报地址
  reportInterval?: number; // 上报间隔(ms)
  enableRealTimeReport?: boolean; // 是否实时上报
}

// 性能监控类
export class PerformanceMonitor {
  private config: PerformanceMonitorConfig;
  private metrics: PerformanceMetrics | null = null;
  private startTime: number;
  private metricsQueue: Array<PerformanceMetrics | AppLoadMetrics> = [];
  private timer: number | null = null;
  private observers: PerformanceObserver[] = [];

  constructor(config: PerformanceMonitorConfig = {}) {
    this.config = {
      sampleRate: config.sampleRate || 0.1,
      reportUrl: config.reportUrl || '',
      reportInterval: config.reportInterval || 5000,
      enableRealTimeReport: config.enableRealTimeReport || false,
    };
    this.startTime = performance.now();
    
    // 检查采样率，决定是否启用监控
    if (Math.random() > this.config.sampleRate) {
      console.debug('PerformanceMonitor: Sampling rate not met, skipping initialization');
      return;
    }
    
    this.init();
  }

  private init(): void {
    this.collectNavigationMetrics();
    this.setupObservers();
    
    // 设置定时上报
    if (this.config.reportUrl && !this.config.enableRealTimeReport) {
      this.timer = window.setInterval(() => {
        if (this.metricsQueue.length > 0) {
          this.reportMetrics(this.metricsQueue);
          this.metricsQueue = [];
        }
      }, this.config.reportInterval);
    }
  }

  private collectNavigationMetrics(): void {
    if (!window.performance || !window.performance.timing) {
      console.warn('PerformanceMonitor: Performance timing API not supported');
      return;
    }

    const timing = window.performance.timing;
    const navigationStart = timing.navigationStart;

    this.metrics = {
      navigationStart,
      resourceLoadTime: timing.responseEnd - navigationStart,
      domContentLoaded: timing.domContentLoadedEventEnd - navigationStart,
      completeLoadTime: timing.loadEventEnd - navigationStart,
      firstPaint: 0,
      firstContentfulPaint: 0,
      largestContentfulPaint: 0,
      cumulativeLayoutShift: 0,
      timeToInteractive: 0
    };

    // 监听页面卸载，确保上报数据
    window.addEventListener('beforeunload', () => {
      if (this.metricsQueue.length > 0) {
        // 使用 sendBeacon API 进行异步上报
        if (navigator.sendBeacon && this.config.reportUrl) {
          navigator.sendBeacon(
            this.config.reportUrl,
            JSON.stringify(this.metricsQueue)
          );
        }
      }
      this.cleanup();
    });
  }

  private setupObservers(): void {
    // 监听FP和FCP
    try {
      const paintObserver = new PerformanceObserver((entries) => {
        entries.getEntries().forEach((entry: any) => {
          if (this.metrics && entry.startTime) {
            if (entry.name === 'first-paint') {
              this.metrics.firstPaint = entry.startTime;
            } else if (entry.name === 'first-contentful-paint') {
              this.metrics.firstContentfulPaint = entry.startTime;
            }
          }
        });
      });
      paintObserver.observe({ type: 'paint', buffered: true });
      this.observers.push(paintObserver);
    } catch (error) {
      console.warn('PerformanceMonitor: Paint observer setup failed:', error);
    }

    // 监听LCP
    try {
      const lcpObserver = new PerformanceObserver((entries) => {
        entries.getEntries().forEach((entry: any) => {
          if (this.metrics && entry.startTime) {
            this.metrics.largestContentfulPaint = entry.startTime;
          }
        });
      });
      lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true });
      this.observers.push(lcpObserver);
    } catch (error) {
      console.warn('PerformanceMonitor: LCP observer setup failed:', error);
    }

    // 监听CLS
    try {
      const clsObserver = new PerformanceObserver((entries) => {
        if (this.metrics) {
          this.metrics.cumulativeLayoutShift += entries.getEntries()
            .reduce((shift: number, entry: any) => {
              if (!entry.hadRecentInput) {
                return shift + entry.value;
              }
              return shift;
            }, 0);
        }
      });
      clsObserver.observe({ type: 'layout-shift', buffered: true });
      this.observers.push(clsObserver);
    } catch (error) {
      console.warn('PerformanceMonitor: CLS observer setup failed:', error);
    }

    // 估算TTI
    try {
      const longtaskObserver = new PerformanceObserver(() => {
        // 这里使用简化的TTI估算逻辑
        this.metrics = this.metrics || {} as PerformanceMetrics;
        this.metrics.timeToInteractive = Math.max(
          this.metrics.timeToInteractive,
          this.metrics.domContentLoaded + 2000 // 简化估算
        );
      });
      longtaskObserver.observe({ type: 'longtask', buffered: true });
      this.observers.push(longtaskObserver);
    } catch (error) {
      console.warn('PerformanceMonitor: Long task observer setup failed:', error);
    }
  }

  // 记录资源加载时间
  public recordResourceLoad(url: string, loadTime: number, size: number): void {
    const resourceMetric = {
      timestamp: Date.now(),
      type: 'resource',
      data: {
        url,
        loadTime,
        size,
      },
    };
    
    this.queueMetric(resourceMetric);
  }

  // 收集当前指标
  public collectMetrics(): PerformanceMetrics | null {
    return this.metrics;
  }

  // 上报指标
  private reportMetrics(metrics: Array<any>): void {
    if (!this.config.reportUrl) return;

    try {
      fetch(this.config.reportUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          timestamp: Date.now(),
          browser: navigator.userAgent,
          metrics,
        }),
        keepalive: true,
      }).catch((error) => {
        console.error('PerformanceMonitor: Failed to report metrics:', error);
      });
    } catch (error) {
      console.error('PerformanceMonitor: Report metrics error:', error);
    }
  }

  // 将指标加入队列
  private queueMetric(metric: any): void {
    if (this.config.enableRealTimeReport && this.config.reportUrl) {
      this.reportMetrics([metric]);
    } else {
      this.metricsQueue.push(metric);
    }
  }

  // 清理资源
  private cleanup(): void {
    // 清理所有观察者
    this.observers.forEach(observer => {
      try {
        observer.disconnect();
      } catch (error) {
        console.warn('PerformanceMonitor: Error disconnecting observer:', error);
      }
    });
    
    // 清除定时器
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  // 销毁监控实例
  public destroy(): void {
    this.cleanup();
  }
}

// 微应用加载监控类
export class AppLoadMonitor {
  private performanceMonitor: PerformanceMonitor;
  private appLoadMetrics: Map<string, AppLoadMetrics> = new Map();

  constructor(performanceMonitor: PerformanceMonitor) {
    this.performanceMonitor = performanceMonitor;
  }

  // 开始监控应用加载
  public startAppLoad(appName: string): void {
    const now = performance.now();
    this.appLoadMetrics.set(appName, {
      appName,
      loadStart: now,
      loadEnd: 0,
      loadTime: 0,
      mountStart: 0,
      mountEnd: 0,
      mountTime: 0,
      firstRender: 0,
      resources: []
    });
  }

  // 记录应用加载完成
  public endAppLoad(appName: string): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics) {
      metrics.loadEnd = performance.now();
      metrics.loadTime = metrics.loadEnd - metrics.loadStart;
    }
  }

  // 记录应用挂载开始
  public startAppMount(appName: string): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics) {
      metrics.mountStart = performance.now();
    }
  }

  // 记录应用挂载完成
  public endAppMount(appName: string): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics) {
      metrics.mountEnd = performance.now();
      metrics.mountTime = metrics.mountEnd - metrics.mountStart;
      this.reportAppMetrics(appName);
    }
  }

  // 记录首次渲染时间
  public recordFirstRender(appName: string): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics) {
      metrics.firstRender = performance.now();
    }
  }

  // 记录资源加载
  public recordResource(appName: string, url: string, loadTime: number, size: number): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics) {
      metrics.resources.push({ url, loadTime, size });
      this.performanceMonitor.recordResourceLoad(url, loadTime, size);
    }
  }

  // 获取应用加载指标
  public getAppMetrics(appName: string): AppLoadMetrics | undefined {
    return this.appLoadMetrics.get(appName);
  }

  // 上报应用加载指标
  private reportAppMetrics(appName: string): void {
    const metrics = this.appLoadMetrics.get(appName);
    if (metrics && this.performanceMonitor) {
      // 这里可以根据需要上报到性能监控系统
      console.log(`AppLoadMonitor: Metrics for ${appName}`, metrics);
      // 实际项目中可以调用 performanceMonitor 的上报方法
    }
  }

  // 清除指定应用的指标
  public clearAppMetrics(appName: string): void {
    this.appLoadMetrics.delete(appName);
  }

  // 清除所有指标
  public clearAllMetrics(): void {
    this.appLoadMetrics.clear();
  }
}

// 创建单例实例
let performanceMonitorInstance: PerformanceMonitor | null = null;
let appLoadMonitorInstance: AppLoadMonitor | null = null;

// 获取性能监控单例
export const getPerformanceMonitor = (config?: PerformanceMonitorConfig): PerformanceMonitor => {
  if (!performanceMonitorInstance) {
    performanceMonitorInstance = new PerformanceMonitor(config);
  }
  return performanceMonitorInstance;
};

// 获取应用加载监控单例
export const getAppLoadMonitor = (): AppLoadMonitor => {
  if (!appLoadMonitorInstance) {
    appLoadMonitorInstance = new AppLoadMonitor(getPerformanceMonitor());
  }
  return appLoadMonitorInstance;
};