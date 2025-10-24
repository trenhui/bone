import { ResourceCacheConfig } from './types';

interface CacheItem {
  data: any;
  timestamp: number;
  size: number;
}

/**
 * 资源缓存管理器
 * 用于缓存和管理微应用的资源，提高加载性能
 */
export class ResourceCache {
  private cache: Map<string, CacheItem> = new Map();
  private totalSize: number = 0;
  private config: ResourceCacheConfig;
  private appId: string;

  /**
   * 构造函数
   * @param appId 应用ID
   * @param config 缓存配置
   */
  constructor(appId: string, config: ResourceCacheConfig = {}) {
    this.appId = appId;
    this.config = {
      enabled: true,
      ttl: 3600000, // 默认1小时
      maxSize: 10 * 1024 * 1024, // 默认10MB
      ...config
    };

    // 初始化时清理过期缓存
    this.cleanupExpired();
    
    // 定期清理过期缓存
    this.scheduleCleanup();
  }

  /**
   * 设置缓存项
   * @param key 缓存键
   * @param data 缓存数据
   * @returns 是否成功设置缓存
   */
  set(key: string, data: any): boolean {
    if (!this.config.enabled) {
      return false;
    }

    try {
      const size = this.calculateSize(data);
      
      // 检查缓存大小限制
      if (this.wouldExceedMaxSize(size)) {
        this.evictOldest(size);
      }

      const existingItem = this.cache.get(key);
      if (existingItem) {
        this.totalSize -= existingItem.size;
      }

      this.cache.set(key, {
        data,
        timestamp: Date.now(),
        size
      });

      this.totalSize += size;
      return true;
    } catch (error) {
      console.error(`Error setting cache for ${this.appId}:`, error);
      return false;
    }
  }

  /**
   * 获取缓存项
   * @param key 缓存键
   * @returns 缓存数据，如果不存在或已过期则返回null
   */
  get(key: string): any | null {
    if (!this.config.enabled) {
      return null;
    }

    const item = this.cache.get(key);
    if (!item) {
      return null;
    }

    // 检查是否过期
    if (this.isExpired(item)) {
      this.delete(key);
      return null;
    }

    return item.data;
  }

  /**
   * 删除缓存项
   * @param key 缓存键
   * @returns 是否成功删除
   */
  delete(key: string): boolean {
    const item = this.cache.get(key);
    if (item) {
      this.totalSize -= item.size;
      return this.cache.delete(key);
    }
    return false;
  }

  /**
   * 清空所有缓存
   */
  clear(): void {
    this.cache.clear();
    this.totalSize = 0;
  }

  /**
   * 获取缓存项数量
   */
  size(): number {
    return this.cache.size;
  }

  /**
   * 获取缓存总大小（字节）
   */
  getTotalSize(): number {
    return this.totalSize;
  }

  /**
   * 检查缓存项是否存在
   * @param key 缓存键
   */
  has(key: string): boolean {
    return this.cache.has(key);
  }

  /**
   * 获取所有缓存键
   */
  keys(): string[] {
    return Array.from(this.cache.keys());
  }

  /**
   * 手动触发缓存清理
   */
  cleanup(): void {
    this.cleanupExpired();
  }

  /**
   * 清理过期的缓存项
   */
  private cleanupExpired(): void {
    const now = Date.now();
    
    // 使用forEach代替entries()迭代，避免TypeScript编译错误
    this.cache.forEach((item, key) => {
      if (now - item.timestamp > this.config.ttl!) {
        this.delete(key);
      }
    });
  }

  /**
   * 计算数据大小（字节）
   * @param data 要计算大小的数据
   */
  private calculateSize(data: any): number {
    try {
      if (data === null || data === undefined) {
        return 0;
      }
      
      if (typeof data === 'string') {
        return new Blob([data]).size;
      }
      
      if (typeof data === 'object') {
        return new Blob([JSON.stringify(data)]).size;
      }
      
      return new Blob([String(data)]).size;
    } catch (error) {
      console.warn('Failed to calculate size, using default:', error);
      return 1024; // 默认1KB
    }
  }

  /**
   * 检查缓存项是否过期
   * @param item 缓存项
   */
  private isExpired(item: CacheItem): boolean {
    return Date.now() - item.timestamp > this.config.ttl!;
  }

  /**
   * 检查添加新项是否会超过最大大小限制
   * @param additionalSize 新增数据的大小
   */
  private wouldExceedMaxSize(additionalSize: number): boolean {
    return this.totalSize + additionalSize > this.config.maxSize!;
  }

  /**
   * 驱逐最旧的缓存项以腾出空间
   * @param requiredSize 需要的空间大小
   */
  private evictOldest(requiredSize: number): void {
    const sortedItems = Array.from(this.cache.entries())
      .sort(([, a], [, b]) => a.timestamp - b.timestamp);

    let spaceNeeded = requiredSize;
    
    for (const [key, item] of sortedItems) {
      if (spaceNeeded <= 0) {
        break;
      }
      
      this.delete(key);
      spaceNeeded -= item.size;
    }
  }

  /**
   * 安排定期清理任务
   */
  private scheduleCleanup(): void {
    // 每5分钟清理一次过期缓存
    setInterval(() => {
      this.cleanupExpired();
    }, 5 * 60 * 1000);
  }

  /**
   * 获取缓存统计信息
   */
  getStats(): {
    itemCount: number;
    totalSize: number;
    totalSizeMB: number;
    maxSizeMB: number;
    enabled: boolean;
  } {
    return {
      itemCount: this.cache.size,
      totalSize: this.totalSize,
      totalSizeMB: this.totalSize / (1024 * 1024),
      maxSizeMB: this.config.maxSize! / (1024 * 1024),
      enabled: this.config.enabled!
    };
  }
}