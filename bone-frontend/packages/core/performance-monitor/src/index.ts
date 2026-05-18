// 性能监控服务

interface PerformanceMetric {
  name: string;
  value: number;
  unit: string;
  timestamp: number;
}

interface PerformanceEntry {
  metric: PerformanceMetric;
  context?: Record<string, any>;
}

class PerformanceMonitor {
  private entries: PerformanceEntry[] = [];
  private listeners: ((entry: PerformanceEntry) => void)[] = [];
  private isInitialized = false;

  init() {
    if (this.isInitialized) return;

    // 监听导航性能
    this.monitorNavigation();

    // 监听资源性能
    this.monitorResources();

    // 监听用户交互
    this.monitorUserInteractions();

    this.isInitialized = true;
  }

  private monitorNavigation() {
    if ('performance' in window) {
      window.addEventListener('load', () => {
        const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
        if (navigation) {
          this.addEntry({
            name: 'navigation.loadTime',
            value: navigation.loadEventEnd - navigation.startTime,
            unit: 'ms',
            timestamp: Date.now(),
          });

          this.addEntry({
            name: 'navigation.domContentLoaded',
            value: navigation.domContentLoadedEventEnd - navigation.startTime,
            unit: 'ms',
            timestamp: Date.now(),
          });
        }
      });
    }
  }

  private monitorResources() {
    if ('performance' in window && 'addEventListener' in window.performance) {
      window.performance.addEventListener('resource', (event: PerformanceEntryEvent) => {
        const resource = event.entry as PerformanceResourceTiming;
        this.addEntry({
          name: `resource.${resource.name}`,
          value: resource.duration,
          unit: 'ms',
          timestamp: Date.now(),
        }, {
          type: resource.initiatorType,
          name: resource.name,
        });
      });
    }
  }

  private monitorUserInteractions() {
    const startTimeMap = new Map<string, number>();

    // 监听点击事件
    document.addEventListener('click', (event) => {
      const target = event.target as HTMLElement;
      const id = `${target.tagName}-${Math.random().toString(36).substr(2, 9)}`;
      startTimeMap.set(id, performance.now());

      // 模拟处理时间
      setTimeout(() => {
        const endTime = performance.now();
        const startTime = startTimeMap.get(id);
        if (startTime) {
          this.addEntry({
            name: 'interaction.click',
            value: endTime - startTime,
            unit: 'ms',
            timestamp: Date.now(),
          }, {
            target: target.tagName,
            id: target.id,
            className: target.className,
          });
          startTimeMap.delete(id);
        }
      }, 0);
    });
  }

  private addEntry(metric: PerformanceMetric, context?: Record<string, any>) {
    const entry: PerformanceEntry = { metric, context };
    this.entries.push(entry);
    this.notifyListeners(entry);

    // 限制条目数量
    if (this.entries.length > 1000) {
      this.entries.shift();
    }
  }

  private notifyListeners(entry: PerformanceEntry) {
    this.listeners.forEach(listener => {
      try {
        listener(entry);
      } catch (error) {
        console.error('Error in performance listener:', error);
      }
    });
  }

  onEntry(listener: (entry: PerformanceEntry) => void) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  getEntries(): PerformanceEntry[] {
    return [...this.entries];
  }

  getMetrics(): PerformanceMetric[] {
    return this.entries.map(entry => entry.metric);
  }

  clear() {
    this.entries = [];
  }
}

// 导出单例
export const performanceMonitor = new PerformanceMonitor();
export default performanceMonitor;