/**
 * 资源管理器
 * 用于统一管理和清理各类资源，特别是网络请求的取消控制
 * 遵循最佳实践：
 * - 资源生命周期管理
 * - 请求取消控制
 * - 内存泄漏防护
 * - 资源使用情况监控
 */
class ResourceManager {
  /**
   * 资源分类常量
   */
  static RESOURCE_CATEGORIES = {
    BOOTSTRAP: 'bootstrap',
    DOM: 'dom',
    EVENT: 'event',
    REQUEST: 'request',
    TIMER: 'timer',
    OTHER: 'other'
  };

  /**
   * 优先级常量
   */
  static PRIORITY_LEVELS = {
    HIGH: 'high',
    MEDIUM: 'medium',
    LOW: 'low'
  };
  constructor(options = {}) {
    this.options = {
      enableLogging: process.env.NODE_ENV === 'development',
      maxResourceLifetime: 300000, // 资源最大生命周期，默认5分钟
      ...options
    };
    
    this.resources = new Map(); // 存储所有资源
    this.abortControllers = new Map(); // 存储所有AbortController
    this.resourceStats = {
      total: 0,
      active: 0,
      byType: {},
      peakCount: 0
    };
    
    // 定期清理过期资源的定时器
    this.cleanupTimer = null;
    this._startCleanupTimer();
  }
  
  /**
   * 创建并添加AbortController
   * @param {string} operation - 操作名称
   * @returns {Object} 包含id和signal的AbortController包装对象
   */
  addAbortController(operation) {
    try {
      const controller = new AbortController();
      const resourceId = this._generateResourceId('abort', operation);
      
      const resource = {
        id: resourceId,
        type: 'abort_controller',
        operation,
        controller,
        signal: controller.signal,
        createdAt: Date.now(),
        usedBy: new Set()
      };
      
      this.resources.set(resourceId, resource);
      this.abortControllers.set(resourceId, resource);
      this._updateResourceStats('add', resource);
      
      if (this.options.enableLogging) {
        console.debug(`[ResourceManager] 添加AbortController: ${resourceId} (${operation})`);
      }
      
      return resource;
    } catch (error) {
      console.error('[ResourceManager] 创建AbortController失败:', error);
      return null;
    }
  }
  
  /**
   * 添加通用资源
   * @param {*} resourceOrType - 资源对象或类型字符串（兼容模式）
   * @param {*} metadataOrResource - 元数据对象或资源对象（兼容模式）
   * @param {Object} metadata - 资源元数据（仅当有3个参数时使用）
   * @returns {string} 资源ID
   */
  addResource(resourceOrType, metadataOrResource, metadata = {}) {
    let type, resource, actualMetadata;
    
    // 兼容两种调用方式：
    // 方式1: addResource(cleanupFunction, metadata)
    // 方式2: addResource(type, resource, metadata)
    if (arguments.length === 2 && typeof resourceOrType === 'function') {
      // 方式1
      type = metadataOrResource.category || 'resource';
      resource = resourceOrType;
      actualMetadata = metadataOrResource;
    } else {
      // 方式2
      type = resourceOrType;
      resource = metadataOrResource;
      actualMetadata = metadata;
    }
    
    const resourceId = this._generateResourceId(type);
    
    const resourceWrapper = {
      id: resourceId,
      type,
      resource,
      createdAt: Date.now(),
      ...actualMetadata
    };
    
    this.resources.set(resourceId, resourceWrapper);
    this._updateResourceStats('add', resourceWrapper);
    
    // 如果资源有destroy或dispose方法，记录它
    if (resource && (typeof resource.destroy === 'function' || typeof resource.dispose === 'function')) {
      resourceWrapper.cleanupMethod = typeof resource.destroy === 'function' ? 'destroy' : 'dispose';
    }
    
    if (this.options.enableLogging) {
      console.debug(`[ResourceManager] 添加资源: ${resourceId} (${type})`);
    }
    
    return resourceId;
  }
  
  /**
   * 取消特定的AbortController
   * @param {string} controllerId - AbortController的资源ID
   * @param {string} reason - 取消原因
   * @returns {boolean} 是否成功取消
   */
  abortController(controllerId, reason = 'user_cancelled') {
    const controllerInfo = this.abortControllers.get(controllerId);
    
    if (!controllerInfo) {
      if (this.options.enableLogging) {
        console.warn(`[ResourceManager] 尝试取消不存在的AbortController: ${controllerId}`);
      }
      return false;
    }
    
    try {
      controllerInfo.reason = reason;
      controllerInfo.controller.abort(reason);
      
      if (this.options.enableLogging) {
        console.debug(`[ResourceManager] 取消AbortController: ${controllerId} (${reason})`);
      }
      
      return true;
    } catch (error) {
      console.error(`[ResourceManager] 取消AbortController失败: ${controllerId}`, error);
      return false;
    }
  }
  
  /**
   * 取消所有与特定操作相关的AbortController
   * @param {string} operation - 操作名称
   * @param {string} reason - 取消原因
   * @returns {number} 取消的控制器数量
   */
  abortControllersByOperation(operation, reason = 'operation_cancelled') {
    let count = 0;
    
    this.abortControllers.forEach((controllerInfo, controllerId) => {
      if (controllerInfo.operation === operation) {
        if (this.abortController(controllerId, reason)) {
          count++;
        }
      }
    });
    
    if (this.options.enableLogging && count > 0) {
      console.debug(`[ResourceManager] 取消 ${count} 个与操作 ${operation} 相关的AbortController`);
    }
    
    return count;
  }
  
  /**
   * 移除资源
   * @param {string} resourceId - 资源ID
   * @returns {boolean} 是否成功移除
   */
  removeResource(resourceId) {
    const resource = this.resources.get(resourceId);
    
    if (!resource) {
      if (this.options.enableLogging) {
        console.warn(`[ResourceManager] 尝试移除不存在的资源: ${resourceId}`);
      }
      return false;
    }
    
    try {
      // 清理资源
      this._cleanupResource(resource);
      
      // 从映射中删除
      this.resources.delete(resourceId);
      
      // 如果是AbortController，也从专门的映射中删除
      if (resource.type === 'abort_controller') {
        this.abortControllers.delete(resourceId);
      }
      
      this._updateResourceStats('remove', resource);
      
      if (this.options.enableLogging) {
        console.debug(`[ResourceManager] 移除资源: ${resourceId} (${resource.type})`);
      }
      
      return true;
    } catch (error) {
      console.error(`[ResourceManager] 移除资源失败: ${resourceId}`, error);
      return false;
    }
  }
  
  /**
   * 移除特定类型的所有资源
   * @param {string} type - 资源类型
   * @returns {number} 移除的资源数量
   */
  removeResourcesByType(type) {
    let count = 0;
    const resourcesToRemove = [];
    
    // 收集需要移除的资源ID
    this.resources.forEach((resource, resourceId) => {
      if (resource.type === type) {
        resourcesToRemove.push(resourceId);
      }
    });
    
    // 移除所有匹配的资源
    resourcesToRemove.forEach(resourceId => {
      if (this.removeResource(resourceId)) {
        count++;
      }
    });
    
    if (this.options.enableLogging && count > 0) {
      console.debug(`[ResourceManager] 移除 ${count} 个类型为 ${type} 的资源`);
    }
    
    return count;
  }
  
  /**
   * 移除所有与特定操作相关的资源
   * @param {string} operation - 操作名称
   * @returns {number} 移除的资源数量
   */
  removeResourcesByOperation(operation) {
    let count = 0;
    const resourcesToRemove = [];
    
    // 收集需要移除的资源ID
    this.resources.forEach((resource, resourceId) => {
      if (resource.operation === operation) {
        resourcesToRemove.push(resourceId);
      }
    });
    
    // 移除所有匹配的资源
    resourcesToRemove.forEach(resourceId => {
      if (this.removeResource(resourceId)) {
        count++;
      }
    });
    
    if (this.options.enableLogging && count > 0) {
      console.debug(`[ResourceManager] 移除 ${count} 个与操作 ${operation} 相关的资源`);
    }
    
    return count;
  }
  
  /**
   * 获取资源使用情况统计（别名）
   * @returns {Object} 资源统计信息
   */
  getResourceStatistics() {
    return { ...this.resourceStats };
  }

  /**
   * 获取资源使用情况统计
   * @returns {Object} 资源统计信息
   */
  getResourceStats() {
    return this.getResourceStatistics();
  }

  /**
   * 清理资源
   * @param {Object} options - 清理选项
   * @returns {Object} 清理结果
   */
  clearResources(options = {}) {
    const result = {
      clearedCount: 0,
      errorCount: 0
    };

    try {
      if (options.category) {
        // 清理特定分类的资源
        const resourcesToRemove = [];
        this.resources.forEach((resource, resourceId) => {
          if (resource.category === options.category) {
            resourcesToRemove.push(resourceId);
          }
        });
        resourcesToRemove.forEach(resourceId => {
          if (this.removeResource(resourceId)) {
            result.clearedCount++;
          } else {
            result.errorCount++;
          }
        });
      } else {
        // 清理所有资源
        result.clearedCount = this.cleanupAll();
      }
    } catch (error) {
      console.error('[ResourceManager] 清理资源失败:', error);
      result.errorCount++;
    }

    return result;
  }
  
  /**
   * 清理所有资源
   * @returns {number} 清理的资源数量
   */
  cleanupAll() {
    const resourcesCount = this.resources.size;
    
    // 先清理所有资源内容
    this.resources.forEach(resource => {
      try {
        this._cleanupResource(resource);
      } catch (error) {
        console.error(`[ResourceManager] 清理资源时出错: ${resource.id}`, error);
      }
    });
    
    // 清空所有映射
    this.resources.clear();
    this.abortControllers.clear();
    
    // 重置统计信息
    this.resourceStats = {
      total: 0,
      active: 0,
      byType: {},
      peakCount: this.resourceStats.peakCount // 保留峰值计数
    };
    
    if (this.options.enableLogging) {
      console.debug(`[ResourceManager] 清理所有资源，共 ${resourcesCount} 个`);
    }
    
    return resourcesCount;
  }
  
  /**
   * 标记资源为正在使用
   * @param {string} resourceId - 资源ID
   * @param {string} consumer - 资源消费者标识
   */
  markResourceInUse(resourceId, consumer) {
    const resource = this.resources.get(resourceId);
    
    if (resource && resource.usedBy) {
      resource.usedBy.add(consumer);
      
      if (this.options.enableLogging) {
        console.debug(`[ResourceManager] 资源 ${resourceId} 被 ${consumer} 使用`);
      }
    }
  }
  
  /**
   * 标记资源不再使用
   * @param {string} resourceId - 资源ID
   * @param {string} consumer - 资源消费者标识
   */
  markResourceNotInUse(resourceId, consumer) {
    const resource = this.resources.get(resourceId);
    
    if (resource && resource.usedBy) {
      resource.usedBy.delete(consumer);
      
      // 如果资源不再被任何消费者使用，且已中止，可以考虑清理
      if (resource.usedBy.size === 0 && 
          resource.type === 'abort_controller' && 
          resource.signal.aborted) {
        this.removeResource(resourceId);
      }
      
      if (this.options.enableLogging) {
        console.debug(`[ResourceManager] 资源 ${resourceId} 不再被 ${consumer} 使用`);
      }
    }
  }
  
  /**
   * 手动触发过期资源清理
   * @returns {number} 清理的资源数量
   */
  cleanupExpired() {
    const now = Date.now();
    const expiredResources = [];
    
    this.resources.forEach((resource, resourceId) => {
      // 检查资源是否已过期
      if (now - resource.createdAt > this.options.maxResourceLifetime) {
        // 对于AbortController，确保它已被中止
        if (resource.type === 'abort_controller' && !resource.signal.aborted) {
          this.abortController(resourceId, 'expired');
        }
        
        expiredResources.push(resourceId);
      }
    });
    
    // 移除所有过期资源
    let count = 0;
    expiredResources.forEach(resourceId => {
      if (this.removeResource(resourceId)) {
        count++;
      }
    });
    
    if (this.options.enableLogging && count > 0) {
      console.debug(`[ResourceManager] 清理 ${count} 个过期资源`);
    }
    
    return count;
  }
  
  /**
   * 销毁资源管理器
   */
  destroy() {
    // 停止清理定时器
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = null;
    }
    
    // 清理所有资源
    this.cleanupAll();
    
    if (this.options.enableLogging) {
      console.debug('[ResourceManager] 资源管理器已销毁');
    }
  }
  
  // 私有方法
  
  /**
   * 生成资源ID
   * @private
   */
  _generateResourceId(type, operation = '') {
    const timestamp = Date.now();
    const random = Math.random().toString(36).substr(2, 9);
    const opPart = operation ? `_${operation}` : '';
    return `${type}_${timestamp}_${random}${opPart}`;
  }
  
  /**
   * 更新资源统计信息
   * @private
   */
  _updateResourceStats(action, resource) {
    if (action === 'add') {
      this.resourceStats.total++;
      this.resourceStats.active++;
      
      // 更新按类型统计
      if (!this.resourceStats.byType[resource.type]) {
        this.resourceStats.byType[resource.type] = 0;
      }
      this.resourceStats.byType[resource.type]++;
      
      // 更新峰值计数
      if (this.resourceStats.active > this.resourceStats.peakCount) {
        this.resourceStats.peakCount = this.resourceStats.active;
      }
    } else if (action === 'remove') {
      this.resourceStats.active = Math.max(0, this.resourceStats.active - 1);
      
      // 更新按类型统计
      if (this.resourceStats.byType[resource.type]) {
        this.resourceStats.byType[resource.type] = Math.max(0, this.resourceStats.byType[resource.type] - 1);
      }
    }
  }
  
  /**
   * 清理单个资源
   * @private
   */
  _cleanupResource(resource) {
    try {
      // 对于AbortController，如果还未中止，可以考虑中止
      if (resource.type === 'abort_controller' && !resource.signal.aborted) {
        resource.controller.abort('cleanup');
      }
      
      // 如果资源是函数，直接调用它
      if (typeof resource.resource === 'function') {
        resource.resource();
      }
      // 如果资源有清理方法，调用它
      else if (resource.cleanupMethod && resource.resource) {
        resource.resource[resource.cleanupMethod]();
      }
      
      // 清空引用，帮助垃圾回收
      for (const key in resource) {
        if (Object.prototype.hasOwnProperty.call(resource, key)) {
          if (key !== 'id' && key !== 'type' && key !== 'createdAt') {
            resource[key] = null;
          }
        }
      }
    } catch (error) {
      console.error(`[ResourceManager] 清理资源内容时出错: ${resource.id}`, error);
    }
  }
  
  /**
   * 启动定期清理定时器
   * @private
   */
  _startCleanupTimer() {
    // 每30秒执行一次清理
    this.cleanupTimer = setInterval(() => {
      this.cleanupExpired();
    }, 30000);
  }
}

// 创建默认实例
const defaultResourceManager = new ResourceManager();

// 导出类和默认实例
export { ResourceManager };
export default defaultResourceManager;
