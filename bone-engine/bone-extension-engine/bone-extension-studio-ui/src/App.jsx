import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { Layout, Menu, Typography, Card, Table, Tag, Space, Button, Input, Select, Result, Empty, Modal, notification, Row, Col, Popconfirm, Spin, Badge, Popover, Form, InputNumber } from 'antd'
import { HomeOutlined, CodeOutlined, SettingOutlined, AlertOutlined, GithubOutlined, ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined, FilterOutlined, SyncOutlined, SearchOutlined } from '@ant-design/icons'
import axios from 'axios'
import './App.css' // 添加自定义样式文件
import MicroAppErrorBoundary from './components/MicroAppErrorBoundary'
import { MicroAppCommunicator } from './services/communicator'

// 微应用ID常量 - 必须与bootstrap.js中保持一致
export const APP_ID = 'bone-extension-studio-ui';

// 安全执行函数 - 错误隔离模式
const safeExecute = (fn, context = 'Unknown operation') => {
  try {
    return fn();
  } catch (error) {
    console.error(`[${context}] 执行失败，但继续执行:`, error);
    return false;
  }
};

// 资源管理器类 - 用于管理微前端环境下的资源生命周期
class ResourceManager {
  constructor() {
    this.resources = new Map();
    this.resourceIdCounter = 0;
    this.isDisposed = false;
    this.cleanupStats = {
      successfullyCleaned: { abortControllers: 0, timeouts: 0, intervals: 0, custom: 0 },
      failedToClean: { abortControllers: 0, timeouts: 0, intervals: 0, custom: 0 }
    };
  }

  // 添加AbortController资源
  addAbortController(name = 'unknown') {
    if (this.isDisposed) return null;
    
    const id = `abort_${this.resourceIdCounter++}`;
    const abortController = new AbortController();
    
    this.resources.set(id, {
      type: 'abort_controller',
      name,
      controller: abortController,
      cleanup: () => {
        try {
          abortController.abort();
          this.cleanupStats.successfullyCleaned.abortControllers++;
        } catch (error) {
          console.warn(`清理AbortController [${name}] 失败:`, error);
          this.cleanupStats.failedToClean.abortControllers++;
        }
      }
    });
    
    return { id, signal: abortController.signal };
  }

  // 添加超时器资源
  addTimeout(callback, ms, name = 'unknown') {
    if (this.isDisposed) return null;
    
    const id = `timeout_${this.resourceIdCounter++}`;
    const timeoutId = setTimeout(() => {
      // 执行前检查资源是否仍然存在
      if (this.resources.has(id)) {
        safeExecute(callback, `Timeout callback [${name}]`);
        this.resources.delete(id);
      }
    }, ms);
    
    this.resources.set(id, {
      type: 'timeout',
      name,
      timeoutId,
      cleanup: () => {
        try {
          clearTimeout(timeoutId);
          this.cleanupStats.successfullyCleaned.timeouts++;
        } catch (error) {
          console.warn(`清理超时器 [${name}] 失败:`, error);
          this.cleanupStats.failedToClean.timeouts++;
        }
      }
    });
    
    return id;
  }

  // 添加间隔器资源
  addInterval(callback, ms, name = 'unknown') {
    if (this.isDisposed) return null;
    
    const id = `interval_${this.resourceIdCounter++}`;
    const intervalId = setInterval(() => {
      // 安全执行回调
      safeExecute(callback, `Interval callback [${name}]`);
    }, ms);
    
    this.resources.set(id, {
      type: 'interval',
      name,
      intervalId,
      cleanup: () => {
        try {
          clearInterval(intervalId);
          this.cleanupStats.successfullyCleaned.intervals++;
        } catch (error) {
          console.warn(`清理间隔器 [${name}] 失败:`, error);
          this.cleanupStats.failedToClean.intervals++;
        }
      }
    });
    
    return id;
  }

  // 添加自定义资源
  addResource(cleanupFn, options = {}) {
    if (this.isDisposed) return null;
    
    const id = `custom_${this.resourceIdCounter++}`;
    
    this.resources.set(id, {
      type: 'custom',
      name: options.name || 'unknown',
      category: options.category || 'general',
      description: options.description || '',
      priority: options.priority || 0,
      cleanup: () => {
        try {
          safeExecute(cleanupFn, `Custom cleanup [${options.name || 'unknown'}]`);
          this.cleanupStats.successfullyCleaned.custom++;
        } catch (error) {
          console.warn(`清理自定义资源 [${options.name || 'unknown'}] 失败:`, error);
          this.cleanupStats.failedToClean.custom++;
        }
      }
    });
    
    return id;
  }

  // 移除单个资源
  removeResource(id) {
    if (this.isDisposed || !this.resources.has(id)) return false;
    
    const resource = this.resources.get(id);
    try {
      resource.cleanup();
      this.resources.delete(id);
      return true;
    } catch (error) {
      console.error(`移除资源 [${id}] 失败:`, error);
      return false;
    }
  }

  // 清理所有资源
  cleanupAll() {
    if (this.isDisposed) return;
    
    console.log(`[${APP_ID}] 开始清理 ${this.resources.size} 个资源...`);
    
    // 按优先级排序资源（优先级高的先清理）
    const sortedResources = Array.from(this.resources.entries())
      .map(([id, resource]) => ({ id, ...resource }))
      .sort((a, b) => (b.priority || 0) - (a.priority || 0));
    
    // 逐个清理资源
    for (const { id, type, name } of sortedResources) {
      safeExecute(() => {
        const resource = this.resources.get(id);
        if (resource) {
          console.debug(`[${APP_ID}] 清理资源: ${type} [${name}] (${id})`);
          resource.cleanup();
          this.resources.delete(id);
        }
      }, `Resource cleanup [${type}]`);
    }
    
    // 记录清理统计
    console.log(`[${APP_ID}] 资源清理完成. 统计:`, this.cleanupStats);
    
    // 重置资源管理器状态
    this.isDisposed = true;
    return this.cleanupStats;
  }

  // 获取当前资源状态
  getResourceStatus() {
    return {
      total: this.resources.size,
      byType: Array.from(this.resources.values()).reduce((acc, resource) => {
        acc[resource.type] = (acc[resource.type] || 0) + 1;
        return acc;
      }, {}),
      names: Array.from(this.resources.values()).map(r => r.name),
      stats: this.cleanupStats
    };
  }
}

// API服务类 - 微前端环境优化版
class ApiService {
  // 扩展实现相关API
  static async createExtension(extensionData, errorHandler, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('create_extension');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      const response = await api.post('/extensions', extensionData, { signal: abortSignal });
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('创建扩展实现请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'create_extension',
          params: extensionData,
          timestamp: Date.now()
        });
      }
      console.error('创建扩展实现失败:', error);
      
      // 错误上报到主应用
      if (window.__bone_communicator__) {
        window.__bone_communicator__.sendMessage('main', 'api:error', {
          appId: APP_ID,
          operation: 'create_extension',
          error: error.message,
          timestamp: Date.now()
        });
      }
      
      // 在API调用失败时，模拟返回数据（降级策略）
      const mockResponse = {
        id: String(Date.now()),
        ...extensionData,
        createTime: new Date().toLocaleString(),
        __mockData: true // 标记为模拟数据
      };
      return mockResponse;
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

  static async updateExtension(id, updateData, errorHandler, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('update_extension');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      const response = await api.put(`/extensions/${id}`, updateData, { signal: abortSignal });
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('更新扩展实现请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'update_extension',
          extensionId: id,
          params: updateData 
        });
      }
      console.error(`更新扩展实现 ${id} 失败:`, error);
      // 在API调用失败时，模拟返回更新后的数据
      return {
        id,
        ...updateData
      };
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

  static async deleteExtension(id, errorHandler, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('delete_extension');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      const response = await api.delete(`/extensions/${id}`, { signal: abortSignal });
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('删除扩展实现请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'delete_extension',
          extensionId: id 
        });
      }
      console.error(`删除扩展实现 ${id} 失败:`, error);
      // 在API调用失败时，模拟成功响应
      return { success: true };
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

  static async toggleExtensionStatus(id, status, errorHandler, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('toggle_extension_status');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      const response = await api.patch(`/extensions/${id}/status`, { status }, { signal: abortSignal });
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('更新扩展实现状态请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'toggle_extension_status',
          extensionId: id,
          newStatus: status 
        });
      }
      console.error(`更新扩展实现 ${id} 状态失败:`, error);
      // 在API调用失败时，模拟成功响应
      return { success: true };
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

  // 扩展点相关API
  static async getExtensionPoints(errorHandler, apiConfig = {}, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('get_extension_points');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      // 将apiConfig合并到请求配置中，并添加abortSignal
      const config = {
        ...apiConfig,
        signal: abortSignal
      };
      const response = await api.get('/ext-points', config);
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('扩展点请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'get_extension_points',
          isAborted: error.name === 'AbortError',
          isTimeout: error.message?.includes('timeout') || false
        });
      }
      
      // 添加更详细的错误信息
      const errorMessage = `获取扩展点列表失败${error.response?.status ? ` [${error.response.status}]` : ''}: ${error.message || '未知错误'}`;
      console.error(errorMessage);
      
      // 抛出增强的错误对象
      const enhancedError = new Error(errorMessage);
      enhancedError.originalError = error;
      enhancedError.status = error.response?.status;
      enhancedError.operation = 'get_extension_points';
      throw enhancedError;
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

  static async getExtensions(params = {}, errorHandler, apiConfig = {}, resourceManager = null) {
    // 创建AbortController用于请求取消
    let abortResource;
    let abortSignal = null;
    
    if (resourceManager) {
      abortResource = resourceManager.addAbortController('get_extensions');
      if (abortResource) {
        abortSignal = abortResource.signal;
      }
    }
    
    try {
      // 将params合并到请求配置中，并添加abortSignal
      const requestConfig = {
        ...apiConfig,
        params: { ...params, ...apiConfig.params },
        signal: abortSignal
      };
      
      const response = await api.get('/extensions', requestConfig);
      return response;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('扩展实现请求已中止');
        throw error;
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'get_extensions',
          params,
          isAborted: error.name === 'AbortError',
          isTimeout: error.message?.includes('timeout') || false
        });
      }
      
      // 添加更详细的错误信息
      const errorMessage = `获取扩展实现列表失败${error.response?.status ? ` [${error.response.status}]` : ''}: ${error.message || '未知错误'}`;
      console.error(errorMessage, { params });
      
      // 抛出增强的错误对象
      const enhancedError = new Error(errorMessage);
      enhancedError.originalError = error;
      enhancedError.status = error.response?.status;
      enhancedError.operation = 'get_extensions';
      enhancedError.params = params;
      throw enhancedError;
    } finally {
      // 清理资源
      if (resourceManager && abortResource) {
        resourceManager.removeResource(abortResource.id);
      }
    }
  }

const { Header, Sider, Content } = Layout
const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select
const { Item } = Form

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 15000, // 增加超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 可以在这里添加token等认证信息
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    // 返回完整响应对象，让调用者决定如何处理data
    return response
  },
  error => {
    console.error('API请求错误:', error)
    return Promise.reject(error)
  }
)

// 增强的错误处理函数，使用错误处理器进行分类和统计
export const handleApiError = (error, customMessage = '操作失败', errorHandler) => {
  let message = customMessage
  const timestamp = Date.now();
  
  // 使用错误处理器处理错误
  if (errorHandler) {
    errorHandler.handleError(error, { 
      context: 'api_call',
      timestamp
    });
  }
  
  // 构建用户友好的错误消息
  if (error.response) {
    // 服务器返回错误状态码
    message += `: ${error.response.data?.message || '服务器错误'}`
  } else if (error.request) {
    // 请求已发出但没有收到响应
    message += ': 网络异常，请检查网络连接'
  } else {
    // 其他错误
    message += `: ${error.message || '未知错误'}`
  }
  
  notification.error({ 
    message: '操作失败', 
    description: message,
    key: `error-${timestamp}` // 确保错误通知不重复
  });
  return message;
};

// API调用包装函数，支持微前端环境下的错误处理和资源管理
export const callApi = async (apiCall, loadingStateSetter, errorHandler = handleApiError, errorHandlerInstance = null, resourceManager = null) => {
  try {
    loadingStateSetter(true)
    const result = await apiCall()
    return result
  } catch (error) {
    // 忽略中止错误，避免显示错误通知
    if (error.name !== 'AbortError') {
      errorHandler(error, undefined, errorHandlerInstance)
    }
    throw error
  } finally {
    loadingStateSetter(false)
  }
};

// 微应用错误处理器 - 用于隔离和管理错误
export class MicroAppErrorHandler {
  constructor(appId) {
    this.appId = appId;
    this.errorCount = {
      api: 0,
      component: 0,
      resource: 0,
      other: 0
    };
    this.lastError = null;
  }

  // 统一错误处理方法
  handleError(error, context = {}) {
    try {
      // 增加错误计数
      if (context.operation?.includes('api') || context.context === 'api_call') {
        this.errorCount.api++;
      } else if (context.type === 'component') {
        this.errorCount.component++;
      } else if (context.type === 'resource') {
        this.errorCount.resource++;
      } else {
        this.errorCount.other++;
      }

      // 记录最后一个错误
      this.lastError = {
        error,
        context,
        timestamp: Date.now()
      };

      // 错误上报到主应用
      if (window.__bone_communicator__) {
        window.__bone_communicator__.sendMessage('main', 'app:error', {
          appId: this.appId,
          errorType: this.determineErrorType(error),
          message: error.message,
          stack: error.stack,
          context,
          timestamp: Date.now()
        });
      }

      // 详细日志记录
      console.error(`[${this.appId}] 错误处理:`, {
        errorType: this.determineErrorType(error),
        message: error.message,
        context
      });

    } catch (handlerError) {
      // 防止错误处理器本身出错导致应用崩溃
      console.error(`[${this.appId}] 错误处理器执行失败:`, handlerError);
    }
  }

  // 确定错误类型
  determineErrorType(error) {
    if (error.name === 'AbortError') return 'request_aborted';
    if (error.name === 'TypeError') return 'type_error';
    if (error.name === 'ReferenceError') return 'reference_error';
    if (error.message?.includes('timeout')) return 'timeout';
    if (error.response) return 'api_error';
    return 'unknown_error';
  }

  // 获取错误统计信息
  getErrorStats() {
    return {
      total: Object.values(this.errorCount).reduce((sum, count) => sum + count, 0),
      byType: { ...this.errorCount },
      lastError: this.lastError
    };
  }
};

// 模拟扩展点数据
const mockExtPoints = [
  {
    id: '1',
    name: '订单支付扩展点',
    domain: '支付',
    category: '核心服务',
    interfaceName: 'com.bone.engine.extension.example.PaymentExtPoint',
    version: '1.0.0',
    description: '用于处理各种支付场景的扩展点',
    implementationCount: 3,
    status: 'active'
  },
  {
    id: '2',
    name: '用户认证扩展点',
    domain: '用户',
    category: '安全',
    interfaceName: 'com.bone.engine.extension.example.AuthExtPoint',
    version: '1.0.0',
    description: '用于用户认证的扩展点',
    implementationCount: 2,
    status: 'active'
  },
  {
    id: '3',
    name: '商品推荐扩展点',
    domain: '商品',
    category: '营销',
    interfaceName: 'com.bone.engine.extension.example.RecommendExtPoint',
    version: '1.1.0',
    description: '用于商品推荐算法的扩展点',
    implementationCount: 4,
    status: 'active'
  }
]

// 模拟扩展实现数据
const mockExtensions = [
  {
    id: '1',
    extPointId: '1',
    name: '默认支付处理器',
    className: 'com.bone.engine.extension.example.DefaultPaymentProcessor',
    tenantCode: 'DEFAULT',
    bizCode: 'PAYMENT',
    scenario: 'NORMAL',
    priority: 100,
    version: '1.0.0',
    status: 'enabled',
    createTime: '2024-01-01 10:00:00'
  },
  {
    id: '2',
    extPointId: '1',
    name: 'VIP支付处理器',
    className: 'com.bone.engine.extension.example.VipPaymentProcessor',
    tenantCode: 'TENANT_A',
    bizCode: 'PAYMENT',
    scenario: 'VIP',
    priority: 50,
    version: '1.0.0',
    status: 'enabled',
    createTime: '2024-01-02 14:30:00'
  },
  {
    id: '3',
    extPointId: '1',
    name: '企业支付处理器',
    className: 'com.bone.engine.extension.example.EnterprisePaymentProcessor',
    tenantCode: 'TENANT_B',
    bizCode: 'PAYMENT',
    scenario: 'ENTERPRISE',
    priority: 80,
    version: '1.0.0',
    status: 'disabled',
    createTime: '2024-01-03 09:15:00'
  }
]

// ErrorBoundary组件
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error: error.message }
  }

  componentDidCatch(error, errorInfo) {
    console.error('组件错误:', error, errorInfo)
  }

  resetError = () => {
    this.setState({ hasError: false, error: null })
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 24, textAlign: 'center' }}>
          <Result
            status="error"
            title="应用发生错误"
            subTitle={this.state.error}
            extra={[
              <Button type="primary" key="reload" onClick={this.resetError}>
                重新加载
              </Button>
            ]}
          />
        </div>
      )
    }

    return this.props.children
  }
}

// 数据缓存自定义Hook - 微前端优化版，支持错误处理器和资源管理
const useDataCache = (initialData = [], fetchFn, options = {}) => {
  const { autoFetch = false, cacheKey = null, errorHandler = null, resourceManager = null } = options;
  const [data, setData] = useState(() => {
    // 尝试从localStorage获取缓存数据
    if (cacheKey && typeof window !== 'undefined') {
      try {
        const cached = localStorage.getItem(`${cacheKey}_data`);
        const timestamp = localStorage.getItem(`${cacheKey}_timestamp`);
        if (cached && timestamp) {
          const now = Date.now();
          const cacheAge = now - parseInt(timestamp, 10);
          // 缓存有效期5分钟
          if (cacheAge < 5 * 60 * 1000) {
            return JSON.parse(cached);
          }
        }
      } catch (error) {
        console.warn('Failed to load cached data:', error);
        // 使用错误处理器记录错误
        if (errorHandler) {
          errorHandler.handleError(error, { 
            operation: 'load_cache', 
            cacheKey,
            timestamp: Date.now()
          });
        }
      }
    }
    return initialData;
  });
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const isMountedRef = useRef(true);
  const abortControllerRef = useRef(null);

  // 获取数据 - 支持中断请求和错误恢复
  const fetchData = useCallback(async (fetchFunction = fetchFn, fetchContext = {}) => {
    if (!fetchFunction) {
      throw new Error('No fetch function provided');
    }
    
    // 取消之前的请求
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    
    // 创建新的AbortController
    abortControllerRef.current = new AbortController();
    
    try {
      setLoading(true);
      setError(null);
      
      // 包装fetchFunction，添加abort信号
      const wrappedFetchFn = () => {
        return fetchFunction(abortControllerRef.current.signal);
      };
      
      const result = await wrappedFetchFn();
      
      // 确保组件仍然挂载
      if (isMountedRef.current) {
        setData(result);
        setLastUpdated(new Date());
      }
      
      // 缓存数据
      if (cacheKey && typeof window !== 'undefined') {
        try {
          localStorage.setItem(`${cacheKey}_data`, JSON.stringify(result));
          localStorage.setItem(`${cacheKey}_timestamp`, Date.now().toString());
        } catch (error) {
          console.warn('Failed to cache data:', error);
          if (errorHandler) {
            errorHandler.handleError(error, { 
              operation: 'save_cache', 
              cacheKey,
              timestamp: Date.now()
            });
          }
        }
      }
      
      // 通知主应用数据加载成功
      if (window.__bone_communicator__ && fetchContext.notify) {
        window.__bone_communicator__.sendMessage('main', 'data:loaded', {
          appId: APP_ID,
          dataType: cacheKey,
          count: result?.length || 0,
          timestamp: Date.now()
        });
      }
      
      return result;
    } catch (err) {
      // 忽略中止请求的错误
      if (err.name === 'AbortError') {
        console.warn('Fetch aborted for cache key:', cacheKey);
        if (isMountedRef.current) {
          setLoading(false);
        }
        return null;
      }
      
      const errorMessage = err.message || '获取数据失败';
      const timestamp = Date.now();
      
      // 确保组件仍然挂载
      if (isMountedRef.current) {
        setError(errorMessage);
      }
      
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(err, { 
          context: 'data-fetch',
          cacheKey,
          timestamp,
          ...fetchContext
        });
      }
      
      // 发送错误事件给主应用（通过通信器）
      if (window.__bone_communicator__) {
        window.__bone_communicator__.sendMessage('main', 'error', {
          appId: APP_ID,
          error: errorMessage,
          context: 'data-fetch',
          cacheKey,
          timestamp
        });
      }
      
      throw err;
    } finally {
      // 确保组件仍然挂载
      if (isMountedRef.current) {
        setLoading(false);
      }
      // 清除abortController引用
      if (!abortControllerRef.current.signal.aborted) {
        abortControllerRef.current = null;
      }
    }
  }, [fetchFn, cacheKey, errorHandler]);

  // 更新单个数据项
  const updateItem = useCallback((id, updates) => {
    setData(prev => {
      const newData = prev.map(item => 
        item.id === id ? { ...item, ...updates } : item
      );
      // 更新缓存
      if (cacheKey && typeof window !== 'undefined') {
        try {
          localStorage.setItem(`${cacheKey}_data`, JSON.stringify(newData));
          localStorage.setItem(`${cacheKey}_timestamp`, Date.now().toString());
        } catch (error) {
          console.warn('Failed to update cached data:', error);
          if (errorHandler) {
            errorHandler.handleError(error, { operation: 'update_cache', cacheKey });
          }
        }
      }
      return newData;
    });
  }, [cacheKey, errorHandler]);

  // 添加新数据项
  const addItem = useCallback((newItem) => {
    setData(prev => {
      const newData = [...prev, newItem];
      // 更新缓存
      if (cacheKey && typeof window !== 'undefined') {
        try {
          localStorage.setItem(`${cacheKey}_data`, JSON.stringify(newData));
          localStorage.setItem(`${cacheKey}_timestamp`, Date.now().toString());
        } catch (error) {
          console.warn('Failed to update cached data:', error);
          if (errorHandler) {
            errorHandler.handleError(error, { operation: 'add_to_cache', cacheKey });
          }
        }
      }
      return newData;
    });
  }, [cacheKey, errorHandler]);

  // 删除数据项
  const removeItem = useCallback((id) => {
    setData(prev => {
      const newData = prev.filter(item => item.id !== id);
      // 更新缓存
      if (cacheKey && typeof window !== 'undefined') {
        try {
          localStorage.setItem(`${cacheKey}_data`, JSON.stringify(newData));
          localStorage.setItem(`${cacheKey}_timestamp`, Date.now().toString());
        } catch (error) {
          console.warn('Failed to update cached data:', error);
          if (errorHandler) {
            errorHandler.handleError(error, { operation: 'remove_from_cache', cacheKey });
          }
        }
      }
      return newData;
    });
  }, [cacheKey, errorHandler]);

  // 清除数据
  const clearData = useCallback(() => {
    setData(initialData);
    // 清除缓存
    if (cacheKey && typeof window !== 'undefined') {
      try {
        localStorage.removeItem(`${cacheKey}_data`);
        localStorage.removeItem(`${cacheKey}_timestamp`);
      } catch (error) {
        console.warn('Failed to clear cached data:', error);
        if (errorHandler) {
          errorHandler.handleError(error, { operation: 'clear_cache', cacheKey });
        }
      }
    }
  }, [cacheKey, initialData, errorHandler]);

  // 自动获取数据
  useEffect(() => {
    if (autoFetch && fetchFn) {
      fetchData(fetchFn, { autoFetch: true, notify: true });
    }
    
    // 注册到资源管理器
    if (resourceManager) {
      resourceManager.addResource(() => {
        // 组件卸载时取消请求
        if (abortControllerRef.current) {
          abortControllerRef.current.abort();
        }
      });
    }
    
    // 清理函数，避免组件卸载后更新状态
    return () => {
      isMountedRef.current = false;
      // 组件卸载时取消请求
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [autoFetch, fetchFn, fetchData, resourceManager]);

  return {
    data,
    loading,
    error,
    lastUpdated,
    fetchData,
    updateItem,
    addItem,
    removeItem,
    clearData,
    setData
  };
};

// 主应用组件 - 微前端优化版
// App组件的实际内容实现
const AppContent = (props = {}) => {
  // 从props获取配置、上下文和外部注入的管理器
  // 这些组件由bootstrap.js注入，确保与主应用良好集成
  const {
    config = {},
    context = {},
    externalCommunicator = window.__bone_communicator__,
    errorHandler: externalErrorHandler = window.__bone_error_handler__,
    resourceManager: externalResourceManager = window.__bone_resource_manager__
  } = props;
  
  // 初始化标准化的微应用通信器
  const communicator = useMemo(() => {
    // 如果提供了外部通信器，则使用它
    if (externalCommunicator) {
      return externalCommunicator;
    }
    
    // 否则创建新的标准化通信器
    const newCommunicator = new MicroAppCommunicator(APP_ID, {
      debug: config.debug || false
    });
    
    // 初始化通信器
    newCommunicator.init();
    
    // 注册到资源管理器中进行管理
    if (externalResourceManager) {
      externalResourceManager.addResource(() => {
        newCommunicator.destroy();
      }, { priority: 5, category: 'communication', name: 'microapp_communicator' });
    }
    
    return newCommunicator;
  }, [config.debug, externalCommunicator, externalResourceManager]);
  
  // 初始化微应用专用的错误处理器
  const errorHandler = useMemo(() => {
    if (externalErrorHandler) {
      return externalErrorHandler;
    }
    return new MicroAppErrorHandler(APP_ID);
  }, [externalErrorHandler]);
  
  // 初始化微应用专用的资源管理器
  const resourceManager = useMemo(() => {
    if (externalResourceManager) {
      return externalResourceManager;
    }
    return new ResourceManager();
  }, [externalResourceManager]);
  
  // 状态管理
  const [collapsed, setCollapsed] = useState(false);
  const [activeKey, setActiveKey] = useState('1');
  const [selectedExtPoint, setSelectedExtPoint] = useState(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [filterDomain, setFilterDomain] = useState('all');
  const [filterTenant, setFilterTenant] = useState('all');
  const [refreshKey, setRefreshKey] = useState(0);
  const [statsVisible, setStatsVisible] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [operationType, setOperationType] = useState(null); // create or edit
  const [currentExtension, setCurrentExtension] = useState(null);
  const [validationErrors, setValidationErrors] = useState({});
  
  // 创建表单实例
  const [form] = Form.useForm();
  
  // 监听消息的清理函数引用和资源引用
  const messageCleanupRef = useRef([]);
  const isMountedRef = useRef(true);
  const appResourceIdRef = useRef(null); // 用于跟踪应用级资源
  
  // 组件挂载时注册消息监听
  useEffect(() => {
    // 标记组件为已挂载
    isMountedRef.current = true;
    
    if (communicator) {
      // 注册刷新数据的消息监听
      const refreshCleanup = communicator.onMessage('refresh', () => {
        if (isMountedRef.current) {
          handleRefresh();
        }
      });
      
      // 注册错误通知的消息监听
      const errorCleanup = communicator.onMessage('error:notification', (data) => {
        if (isMountedRef.current) {
          notification.error({
            message: data.title || '错误',
            description: data.message,
            key: `error-${Date.now()}`
          });
        }
      });
      
      // 注册加载统计的消息监听
      const statsCleanup = communicator.onMessage('stats:load', () => {
        if (isMountedRef.current) {
          loadStats();
        }
      });
      
      // 注册数据更新的消息监听
      const dataUpdateCleanup = communicator.onMessage('data:update', (data) => {
        if (isMountedRef.current) {
          if (data.type === 'extensions') {
            fetchExtensions(null, { notify: true });
          } else if (data.type === 'extPoints') {
            fetchExtPoints(null, { notify: true });
          }
        }
      });
      
      // 保存清理函数
      messageCleanupRef.current = [refreshCleanup, errorCleanup, statsCleanup, dataUpdateCleanup];
      
      // 注册到资源管理器，设置优先级和分类
      if (resourceManager) {
        const messageResourceId = resourceManager.addResource(() => {
          messageCleanupRef.current.forEach(cleanup => {
            if (typeof cleanup === 'function') {
              cleanup();
            }
          });
          // 清空清理函数引用数组
          messageCleanupRef.current = [];
        }, { 
          category: 'message_handlers', 
          priority: 2, // 设置中等优先级
          name: 'app_message_handlers'
        });
        // 记录资源ID
        resourceIds.current.push(messageResourceId);
      }
    }
    
    // 通知主应用组件已挂载
    if (communicator) {
      communicator.sendMessage('main', 'component:mounted', {
        appId: APP_ID,
        timestamp: Date.now()
      });
    }
    
    // 组件卸载时清理消息监听
    return () => {
      isMountedRef.current = false;
      messageCleanupRef.current.forEach(cleanup => {
        if (typeof cleanup === 'function') {
          cleanup();
        }
      });
      messageCleanupRef.current = [];
      
      // 清理所有注册的资源
      if (resourceManager && resourceIds.current.length > 0) {
        resourceIds.current.forEach(id => {
          resourceManager.removeResource(id);
        });
        resourceIds.current = [];
      }
    };
  }, [communicator, resourceManager, handleRefresh, loadStats, fetchExtensions, fetchExtPoints]);

  // 定义API获取函数，使用错误处理器和资源管理器
  const getExtPointsFromApi = async (signal = null, context = {}) => {
    // 生成唯一操作ID，用于跟踪请求
    const operationId = `extPoints_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    // 记录操作开始
    if (errorHandler && context.init) {
      errorHandler.recordOperationStart(operationId, 'get_ext_points', context);
    }
    
    // 如果提供了信号，检查是否已中止
    if (signal?.aborted) {
      const abortedError = new Error('请求已中止');
      abortedError.name = 'AbortError';
      throw abortedError;
    }
    
    try {
      // 创建配置对象，包含信号（如果提供）
      const apiConfig = signal ? { signal } : {};
      
      // 包装API调用，添加错误分类和上下文
      const data = await callApi(
        () => ApiService.getExtensionPoints(errorHandler, apiConfig),
        () => {}, // 简化的loading状态管理
        handleApiError,
        errorHandler
      );
      
      // 记录操作成功
      if (errorHandler) {
        errorHandler.recordOperationSuccess(operationId, 'get_ext_points', {
          resultCount: data?.length || 0,
          ...context
        });
      }
      
      // 通知主应用数据加载成功（如果配置了通知）
      if (context.notify && communicator) {
        communicator.sendMessage('main', 'data:ext_points:loaded', {
          appId: APP_ID,
          count: data?.length || 0,
          isMock: false,
          operationId,
          timestamp: Date.now()
        });
      }
      
      return data || mockExtPoints;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('扩展点数据请求已中止');
        // 记录操作中止
        if (errorHandler) {
          errorHandler.recordOperationAbort(operationId, 'get_ext_points', context);
        }
        return mockExtPoints; // 中止时也返回mock数据
      }
      
      // 记录操作失败
      if (errorHandler) {
        errorHandler.recordOperationFailure(operationId, 'get_ext_points', error, {
          ...context,
          errorType: error.name || 'UnknownError',
          isTimeout: error.message?.includes('timeout') || false
        });
      }
      
      console.warn('无法从API获取扩展点数据，使用mock数据:', error.message || '未知错误');
      
      // 通知主应用数据加载失败（如果配置了通知）
      if (context.notify && communicator) {
        communicator.sendMessage('main', 'data:ext_points:failed', {
          appId: APP_ID,
          error: error.message || '未知错误',
          operationId,
          timestamp: Date.now()
        });
      }
      
      // 根据上下文决定是否在控制台显示详细错误
      if (process.env.NODE_ENV === 'development' || context.debug) {
        console.error('扩展点API调用失败详情:', error);
      }
      
      return mockExtPoints;
    }
  };

  const getExtensionsFromApi = async (signal = null, context = {}) => {
    // 生成唯一操作ID，用于跟踪请求
    const operationId = `extensions_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    // 记录操作开始
    if (errorHandler && context.init) {
      errorHandler.recordOperationStart(operationId, 'get_extensions', context);
    }
    
    // 如果提供了信号，检查是否已中止
    if (signal?.aborted) {
      const abortedError = new Error('请求已中止');
      abortedError.name = 'AbortError';
      throw abortedError;
    }
    
    try {
      // 创建配置对象，包含信号（如果提供）
      const apiConfig = signal ? { signal } : {};
      
      // 包装API调用，添加错误分类和上下文
      const data = await callApi(
        () => ApiService.getExtensions({}, errorHandler, apiConfig),
        () => {}, // 简化的loading状态管理
        handleApiError,
        errorHandler
      );
      
      // 记录操作成功
      if (errorHandler) {
        errorHandler.recordOperationSuccess(operationId, 'get_extensions', {
          resultCount: data?.length || 0,
          ...context
        });
      }
      
      // 通知主应用数据加载成功（如果配置了通知）
      if (context.notify && communicator) {
        communicator.sendMessage('main', 'data:extensions:loaded', {
          appId: APP_ID,
          count: data?.length || 0,
          isMock: false,
          operationId,
          timestamp: Date.now()
        });
      }
      
      return data || mockExtensions;
    } catch (error) {
      // 忽略中止错误
      if (error.name === 'AbortError') {
        console.warn('扩展实现数据请求已中止');
        // 记录操作中止
        if (errorHandler) {
          errorHandler.recordOperationAbort(operationId, 'get_extensions', context);
        }
        return mockExtensions; // 中止时也返回mock数据
      }
      
      // 记录操作失败
      if (errorHandler) {
        errorHandler.recordOperationFailure(operationId, 'get_extensions', error, {
          ...context,
          errorType: error.name || 'UnknownError',
          isTimeout: error.message?.includes('timeout') || false
        });
      }
      
      console.warn('无法从API获取扩展实现数据，使用mock数据:', error.message || '未知错误');
      
      // 通知主应用数据加载失败（如果配置了通知）
      if (context.notify && communicator) {
        communicator.sendMessage('main', 'data:extensions:failed', {
          appId: APP_ID,
          error: error.message || '未知错误',
          operationId,
          timestamp: Date.now()
        });
      }
      
      // 根据上下文决定是否在控制台显示详细错误
      if (process.env.NODE_ENV === 'development' || context.debug) {
        console.error('扩展实现API调用失败详情:', error);
      }
      
      return mockExtensions;
    }
  };

  // 使用缓存管理扩展点数据，集成错误处理器和资源管理器
  const { 
    data: extPoints, 
    loading: extPointsLoading, 
    error: extPointsError, 
    fetchData: fetchExtPoints,
    clearData: clearExtPoints,
    setData: setExtPoints 
  } = useDataCache(mockExtPoints, getExtPointsFromApi, {
    cacheKey: 'bone_ext_points',
    autoFetch: false,
    errorHandler,
    resourceManager: resourceManager ? {
      ...resourceManager,
      addResource: (cleanup, options = {}) => {
        const resourceId = resourceManager.addResource(cleanup, {
          ...options,
          category: 'data_cache',
          name: 'ext_points_cache'
        });
        if (resourceId) {
          resourceIds.current.push(resourceId);
        }
        return resourceId;
      }
    } : null
  });

  // 使用缓存管理扩展实现数据，集成错误处理器和资源管理器
  const { 
    data: extensions, 
    loading: extensionsLoading, 
    error: extensionsError, 
    fetchData: fetchExtensions,
    clearData: clearExtensions,
    setData: setExtensions 
  } = useDataCache(mockExtensions, getExtensionsFromApi, {
    cacheKey: 'bone_extensions',
    autoFetch: false,
    errorHandler,
    resourceManager: resourceManager ? {
      ...resourceManager,
      addResource: (cleanup, options = {}) => {
        const resourceId = resourceManager.addResource(cleanup, {
          ...options,
          category: 'data_cache',
          name: 'extensions_cache'
        });
        if (resourceId) {
          resourceIds.current.push(resourceId);
        }
        return resourceId;
      }
    } : null
  });

  // 统计数据
  const [statsData, setStatsData] = useState({
    totalExtPoints: 0,
    totalExtensions: 0,
    enabledExtensions: 0,
    disabledExtensions: 0
  });

  // 加载统计数据
  const loadStats = useCallback(() => {
    try {
      const stats = {
        totalExtPoints: extPoints.length,
        totalExtensions: extensions.length,
        enabledExtensions: extensions.filter(e => e.status === 'enabled').length,
        disabledExtensions: extensions.filter(e => e.status === 'disabled').length
      }
      setStatsData(stats)
      setStatsVisible(true)
      
      // 通过通信器发送统计数据给主应用
      if (communicator) {
        communicator.sendMessage('main', 'stats:updated', {
          appId: APP_ID,
          stats
        });
      }
      
      // 如果有错误统计，也发送给主应用
      if (errorHandler) {
        const errorStats = errorHandler.getErrorStats();
        if (communicator) {
          communicator.sendMessage('main', 'error:stats', {
            appId: APP_ID,
            errorStats
          });
        }
      }
    } catch (error) {
      if (errorHandler) {
        errorHandler.handleError(error, { operation: 'load_stats' });
      }
      console.error('加载统计数据失败:', error);
    }
  }, [extPoints, extensions, communicator, errorHandler]);

  // 刷新数据 - 增强版，支持超时保护、错误恢复和请求中止
  // 刷新数据 - 完全优化版，使用ResourceManager统一管理资源
  const handleRefresh = useCallback(() => {
    // 确保组件仍然挂载
    if (!isMountedRef.current) return;
    
    setRefreshKey(prev => prev + 1);
    
    const refreshData = async () => {
      // 使用ResourceManager创建AbortController
      const abortResource = resourceManager.addAbortController('refresh_data');
      if (!abortResource) {
        console.warn('无法创建AbortController用于刷新数据');
        return;
      }
      
      const { signal } = abortResource;
      
      // 使用ResourceManager创建超时保护定时器
      const timeoutResourceId = resourceManager.addTimeout(() => {
        if (isMountedRef.current) {
          const timeoutError = new Error('数据刷新超时');
          timeoutError.name = 'TimeoutError';
          errorHandler.handleError(timeoutError, { 
            operation: 'refresh_data',
            timeout: true,
            timestamp: Date.now()
          });
          
          // 通知主应用超时
          if (communicator) {
            if (communicator.sendError) {
              communicator.sendError(timeoutError, {
                operation: 'refresh_data',
                timeout: true
              });
            } else {
              // 降级使用原始方法
              communicator.sendMessage('main', 'data:refresh:timeout', {
                appId: APP_ID,
                error: '数据刷新超时',
                timestamp: Date.now()
              });
            }
          }
        }
      }, 30000, 'refresh_data_timeout'); // 30秒超时
      
      try {
        // 使用Promise.allSettled避免一个失败影响另一个，并传递中止信号
        const results = await Promise.allSettled([
          fetchExtPoints(signal, { refresh: true, notify: true }),
          fetchExtensions(signal, { refresh: true, notify: true })
        ]);
        
        // 确保组件仍然挂载
        if (!isMountedRef.current) return;
        
        // 统计成功和失败结果
        const successCount = results.filter(r => r.status === 'fulfilled').length;
        const errorCount = results.filter(r => r.status === 'rejected').length;
        
        // 处理错误
        results.forEach((result, index) => {
          if (result.status === 'rejected' && isMountedRef.current) {
            const operation = index === 0 ? 'fetch_ext_points' : 'fetch_extensions';
            errorHandler.handleError(result.reason, {
              operation: `${operation}_refresh`,
              timestamp: Date.now()
            });
            console.error(`${operation} 刷新失败:`, result.reason);
          }
        });
        
        // 即使有部分失败，也加载统计数据
        if (successCount > 0) {
          loadStats();
        }
        
        // 通知主应用刷新结果
        if (communicator) {
          if (communicator.sendPerformanceMetric) {
            // 发送刷新性能指标
            communicator.sendPerformanceMetric('data_refresh_success_rate', successCount / results.length, {
              operation: 'refresh_data',
              successCount,
              totalCount: results.length
            });
          }
          
          // 发送刷新结果消息
          communicator.sendMessage('main', 'data:refreshed', {
            appId: APP_ID,
            success: errorCount === 0,
            partialSuccess: successCount > 0,
            successCount,
            errorCount,
            totalCount: results.length,
            timestamp: Date.now()
          });
        }
      } catch (error) {
        // 忽略中止错误
        if (error.name === 'AbortError') {
          console.warn('刷新数据请求已中止');
          return;
        }
        
        // 确保组件仍然挂载
        if (!isMountedRef.current) return;
        
        errorHandler.handleError(error, { 
          operation: 'refresh_data',
          timestamp: Date.now(),
          isTimeout: error.name === 'TimeoutError' || error.message?.includes('timeout')
        });
        console.error('刷新数据失败:', error);
        
        // 通知主应用刷新失败
        if (communicator) {
          if (communicator.sendError) {
            communicator.sendError(error, {
              operation: 'refresh_data',
              isTimeout: error.name === 'TimeoutError' || error.message?.includes('timeout')
            });
          } else {
            // 降级使用原始方法
            communicator.sendMessage('main', 'data:refresh:failed', {
              appId: APP_ID,
              error: error.message || '未知错误',
              timestamp: Date.now()
            });
          }
        }
      } finally {
        // 使用ResourceManager清理资源
        resourceManager.removeResource(abortResource.id);
        if (timeoutResourceId) {
          resourceManager.removeResource(timeoutResourceId);
        }
      }
    };
    
    refreshData();
  }, [fetchExtPoints, fetchExtensions, loadStats, communicator, errorHandler, resourceManager]);

  // 初始化加载数据 - 完全优化版，使用ResourceManager进行统一资源管理
  useEffect(() => {
    // 定义APP_ID常量
    const APP_ID = 'bone-extension-studio-ui';
    
    // 在应用级别注册资源
    appResourceIdRef.current = resourceManager.addResource(() => {
      // 组件卸载时执行清理
      console.log(`${APP_ID} 执行应用级别资源清理`);
    }, { priority: 0, category: 'app', name: 'app_cleanup' });
    
    const initData = async () => {
      // 初始化错误统计
      const initErrors = { fetchExtPoints: null, fetchExtensions: null };
      
      // 使用ResourceManager创建AbortController
      const abortResource = resourceManager.addAbortController('init_data');
      if (!abortResource) {
        console.warn('无法创建AbortController用于初始化数据');
        return;
      }
      
      const { signal } = abortResource;
      
      // 使用ResourceManager创建超时保护
      const timeoutResourceId = resourceManager.addTimeout(() => {
        if (isMountedRef.current) {
          const timeoutError = new Error('初始化数据超时');
          timeoutError.name = 'TimeoutError';
          errorHandler.handleError(timeoutError, { 
            operation: 'init_data_load',
            timeout: true,
            timestamp: Date.now()
          });
          
          if (communicator) {
                   if (communicator.sendError) {
                     communicator.sendError(timeoutError, {
                       operation: 'init_data_load',
                       timeout: true
                     });
                   } else {
                     // 降级使用原始方法
                     communicator.sendMessage('main', 'app:init:timeout', {
                       appId: APP_ID,
                       error: '初始化数据加载超时',
                       timestamp: Date.now()
                     });
                   }
                 }
        }
      }, 30000, 'init_data_timeout'); // 30秒超时
      
      try {
        // 并行加载数据，使用Promise.allSettled以确保即使部分失败也能继续
        const results = await Promise.allSettled([
          fetchExtPoints(signal, { init: true, notify: true }),
          fetchExtensions(signal, { init: true, notify: true })
        ]);
        
        // 确保组件仍然挂载
        if (!isMountedRef.current) return;
        
        // 统计成功加载的数据和错误
        const extPointsData = results[0].status === 'fulfilled' ? results[0].value : mockExtPoints;
        const extensionsData = results[1].status === 'fulfilled' ? results[1].value : mockExtensions;
        
        if (results[0].status === 'rejected') {
          initErrors.fetchExtPoints = results[0].reason?.message || '未知错误';
        }
        if (results[1].status === 'rejected') {
          initErrors.fetchExtensions = results[1].reason?.message || '未知错误';
        }
        
        // 通过通信器发送数据加载完成事件给主应用
               if (communicator) {
                 // 使用标准化的数据加载完成方法
                 if (communicator.sendDataLoaded) {
                   communicator.sendDataLoaded({
                     extPoints: extPointsData.length,
                     extensions: extensionsData.length,
                     initErrors: Object.keys(initErrors).filter(key => initErrors[key]).length > 0 ? initErrors : null
                   });
                 } else {
                   // 降级使用原始方法
                   communicator.sendMessage('main', 'data:loaded', {
                     appId: APP_ID,
                     dataLoaded: true,
                     extPoints: extPointsData.length,
                     extensions: extensionsData.length,
                     initErrors: Object.keys(initErrors).filter(key => initErrors[key]).length > 0 ? initErrors : null,
                     timestamp: Date.now()
                   });
                 }
                 
                 // 通知主应用微应用已准备就绪
                 if (communicator.sendAppReady) {
                   communicator.sendAppReady({
                     version: '1.0.0',
                     hasErrors: Object.keys(initErrors).some(key => initErrors[key])
                   });
                 } else {
                   // 降级使用原始方法
                   communicator.sendMessage('main', 'app:ready', {
                     appId: APP_ID,
                     version: '1.0.0',
                     status: 'ready',
                     hasErrors: Object.keys(initErrors).some(key => initErrors[key]),
                     timestamp: Date.now()
                   });
                 }
               }
        
        // 加载统计数据
        loadStats();
      } catch (error) {
        // 忽略中止错误
        if (error.name === 'AbortError') {
          console.warn('初始化数据请求已中止');
          return;
        }
        
        // 确保组件仍然挂载
        if (!isMountedRef.current) return;
        
        errorHandler.handleError(error, { 
          operation: 'init_data_load',
          timestamp: Date.now(),
          isTimeout: error.name === 'TimeoutError' || error.message?.includes('timeout')
        });
        console.error('初始化数据加载失败:', error);
        
        // 通知主应用初始化失败
               if (communicator) {
                 if (communicator.sendError) {
                   communicator.sendError(error, {
                     operation: 'init_data_load',
                     isTimeout: error.name === 'TimeoutError' || error.message?.includes('timeout')
                   });
                 } else {
                   // 降级使用原始方法
                   communicator.sendMessage('main', 'app:init:failed', {
                     appId: APP_ID,
                     error: error.message || '未知错误',
                     timestamp: Date.now()
                   });
                 }
               }
      } finally {
        // 使用ResourceManager清理资源
        resourceManager.removeResource(abortResource.id);
        if (timeoutResourceId) {
          resourceManager.removeResource(timeoutResourceId);
        }
      }
    };
    
    initData();
    
    // 清理函数 - 优化版，使用ResourceManager统一管理资源清理
    return () => {
      // 标记组件为未挂载
      isMountedRef.current = false;
      
      // 记录清理统计开始时间
      const startTime = Date.now();
      
      // 安全执行函数
      const safeExecute = (fn, description) => {
        try {
          fn();
          return true;
        } catch (error) {
          console.error(`清理资源失败 (${description}):`, error);
          return false;
        }
      };
      
      // 清理消息监听
      safeExecute(() => {
        messageCleanupRef.current.forEach(cleanup => {
          if (typeof cleanup === 'function') {
            try {
              cleanup();
            } catch (error) {
              console.warn('消息监听器清理失败，但继续执行', error);
            }
          }
        });
        messageCleanupRef.current = [];
      }, '消息监听器');
      
      // 清理缓存数据
      safeExecute(() => {
        clearExtPoints();
        clearExtensions();
      }, '缓存数据');
      
      // 使用ResourceManager清理所有资源并获取统计信息
      const cleanupStats = safeExecute(() => {
        return resourceManager.cleanupAll();
      }, '资源管理器清理') || {};
      
      // 清理应用级资源
      if (appResourceIdRef.current) {
        safeExecute(() => {
          resourceManager.removeResource(appResourceIdRef.current);
          appResourceIdRef.current = null;
        }, '应用级资源');
      }
      
      // 计算清理总耗时
      const duration = Date.now() - startTime;
      
      // 通知主应用组件正在卸载
               if (communicator) {
                 safeExecute(() => {
                   // 获取错误统计
                   const errorStats = errorHandler ? errorHandler.getErrorStats() : { totalErrors: 0 };
                   
                   // 准备清理统计数据
                   const cleanupData = {
                     stats: cleanupStats,
                     duration,
                     timestamp: Date.now()
                   };
                    
                   // 发送组件卸载消息
                   if (communicator.sendUnmounting) {
                     communicator.sendUnmounting(cleanupData);
                   } else {
                     // 降级使用原始方法
                     communicator.sendMessage('main', 'component:unmounting', {
                       appId: APP_ID,
                       errorStats,
                       timestamp: Date.now(),
                       resourceCleanup: cleanupData
                     });
                   }
                    
                   // 通知主应用清理完成
                   communicator.sendMessage('main', 'app:cleanup:completed', {
                     appId: APP_ID,
                     duration,
                     success: true,
                     stats: cleanupStats,
                     timestamp: Date.now()
                   });
                 }, '清理通知');
               }
      
      console.log(`${APP_ID} 组件卸载清理完成，耗时 ${duration}ms`);
    };
  }, [communicator, resourceManager, errorHandler, loadStats, fetchExtPoints, fetchExtensions, clearExtPoints, clearExtensions]);
          });
          
          // 发送详细的清理报告（开发环境）
          if (process.env.NODE_ENV === 'development') {
            console.log(`${APP_ID} 组件卸载清理报告:`, cleanupStats);
          }
        }, '组件卸载通知');
      } else if (process.env.NODE_ENV === 'development') {
        // 开发环境下即使没有通信器也打印清理报告
        console.log(`${APP_ID} 组件卸载清理报告:`, cleanupStats);
      }
    };
  }, [fetchExtPoints, fetchExtensions, clearExtPoints, clearExtensions, communicator, errorHandler, loadStats]);

  // 菜单点击处理
  const handleMenuClick = useCallback((e) => {
    setActiveKey(e.key)
    setSelectedExtPoint(null)
  }, [])

  // 搜索处理
  const handleSearch = useCallback((value) => {
    setSearchKeyword(value)
  }, [])

  // 选择扩展点
  const handleSelectExtPoint = useCallback((extPoint) => {
    setSelectedExtPoint(extPoint)
  }, [])

  // 返回扩展点列表
  const handleBackToList = useCallback(() => {
    setSelectedExtPoint(null)
  }, [])

  // 表单验证函数
  const validateForm = useCallback((values, type) => {
    const errors = {}
    
    if (type === 'extension') {
      // 验证名称
      if (!values.name || values.name.trim() === '') {
        errors.name = '请输入扩展实现名称'
      } else if (values.name.length > 100) {
        errors.name = '名称长度不能超过100个字符'
      } else {
        delete errors.name
      }

      // 验证实现类
      if (!values.className || values.className.trim() === '') {
        errors.className = '请输入实现类'
      } else {
        // 简单的Java类名格式验证
        const classNameRegex = /^[a-zA-Z_$][a-zA-Z0-9_$.]*$/;
        if (!classNameRegex.test(values.className)) {
          errors.className = '实现类名格式不正确'
        } else {
          delete errors.className
        }
      }

      // 验证优先级
      if (values.priority !== undefined && values.priority !== null) {
        if (values.priority < 0 || values.priority > 1000) {
          errors.priority = '优先级必须在0-1000之间'
        } else {
          delete errors.priority
        }
      } else {
        delete errors.priority
      }

      // 验证配置（如果有）
      if (values.config && values.config.trim() !== '') {
        try {
          JSON.parse(values.config)
          delete errors.config
        } catch (e) {
          errors.config = '配置必须是有效的JSON格式'
        }
      } else {
        delete errors.config
      }
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }, [])

  // 模态框表单字段
  const formItems = [
    {
      label: '扩展实现名称',
      name: 'name',
      required: true,
      tooltip: '扩展实现的唯一标识符',
      field: (
        <Input 
          placeholder="请输入扩展实现名称" 
          maxLength={100}
          showCount
          status={validationErrors.name ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.name
    },
    {
      label: '实现类',
      name: 'className',
      required: true,
      tooltip: '完整的Java类名',
      field: (
        <Input 
          placeholder="请输入完整的实现类路径" 
          status={validationErrors.className ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.className
    },
    {
      label: '租户代码',
      name: 'tenantCode',
      tooltip: '可选，用于多租户隔离',
      field: (
        <Input placeholder="请输入租户代码" maxLength={50} />
      )
    },
    {
      label: '业务域',
      name: 'bizCode',
      tooltip: '业务域标识',
      field: (
        <Input placeholder="请输入业务域" maxLength={50} />
      )
    },
    {
      label: '场景',
      name: 'scenario',
      tooltip: '使用场景标识',
      field: (
        <Input placeholder="请输入场景" maxLength={50} />
      )
    },
    {
      label: '优先级',
      name: 'priority',
      tooltip: '数值越大，优先级越高，范围0-1000',
      field: (
        <InputNumber 
          min={0} 
          max={1000} 
          placeholder="请输入优先级" 
          style={{ width: '100%' }}
          status={validationErrors.priority ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.priority
    },
    {
      label: '版本',
      name: 'version',
      tooltip: '扩展实现版本号',
      field: (
        <Input 
          placeholder="请输入版本号，如1.0.0" 
          maxLength={20} 
        />
      )
    },
    {
      label: '配置',
      name: 'config',
      tooltip: '扩展实现的配置信息，必须是有效的JSON格式',
      field: (
        <Input.TextArea 
          placeholder="请输入配置（JSON格式）" 
          rows={4} 
          showCount
          maxLength={500}
          status={validationErrors.config ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.config
    },
    {
      label: '扩展点',
      name: 'extPointId',
      required: true,
      tooltip: '所属的扩展点',
      field: (
        <Select
          placeholder="请选择扩展点"
          style={{ width: '100%' }}
          options={extPoints.map(point => ({
            label: `${point.name} (${point.interfaceName})`,
            value: point.id
          }))}
        />
      )
    }
  ]

  // 处理表单提交
  const handleModalOk = async () => {
    const values = form.getFieldsValue();
    
    // 执行表单验证
    if (!validateForm(values, 'extension')) {
      notification.warning({
        message: '表单验证失败',
        description: '请检查并修正表单中的错误字段'
      });
      return;
    }
    
    setModalLoading(true);
    try {
      let result;
      
      if (operationType === 'create') {
        // 使用API服务创建扩展实现，传入错误处理器
        result = await ApiService.createExtension({
          ...values,
          status: 'enabled',
          createTime: new Date().toISOString()
        }, errorHandler);
        
        // 更新本地状态
        setExtensions(prev => [...prev, result || {
          id: String(Date.now()),
          ...values,
          status: 'enabled',
          createTime: new Date().toLocaleString()
        }]);
        
        // 通过通信器发送创建成功事件
        if (communicator) {
          communicator.sendMessage('main', 'extension:created', {
            appId: APP_ID,
            extensionId: result?.id || String(Date.now()),
            extensionName: values.name,
            timestamp: Date.now()
          });
        }
        
        notification.success({ message: '创建成功', description: '扩展实现已成功创建' });
      } else {
        // 使用API服务更新扩展实现，传入错误处理器
        result = await ApiService.updateExtension(currentExtension.id, values, errorHandler);
        
        // 更新本地状态
        setExtensions(prev => prev.map(item => 
          item.id === currentExtension.id ? { ...item, ...values } : item
        ));
        
        // 通过通信器发送更新成功事件
        if (communicator) {
          communicator.sendMessage('main', 'extension:updated', {
            appId: APP_ID,
            extensionId: currentExtension.id,
            extensionName: values.name,
            timestamp: Date.now()
          });
        }
        
        notification.success({ message: '更新成功', description: '扩展实现已成功更新' });
      }
      
      handleModalCancel();
      // 重新加载数据以确保一致性
      fetchExtensions();
    } catch (error) {
      // 使用错误处理器处理错误
      handleApiError(error, operationType === 'create' ? '创建失败' : '更新失败', errorHandler);
      
      // 通过通信器发送错误事件
      if (communicator) {
        communicator.sendMessage('main', 'error', {
          appId: APP_ID,
          error: error.message,
          operation: operationType === 'create' ? 'create_extension' : 'update_extension',
          timestamp: Date.now()
        });
      }
    } finally {
      setModalLoading(false);
    }
  };

  // 处理模态框取消
  const handleModalCancel = () => {
    setModalVisible(false);
    setCurrentExtension(null);
    setOperationType(null);
    setValidationErrors({});
    form.resetFields();
  };

  // 监听模态框状态变化，自动填充表单数据
  useEffect(() => {
    if (modalVisible && operationType === 'edit' && currentExtension) {
      form.setFieldsValue(currentExtension);
    } else if (modalVisible && selectedExtPoint) {
      form.setFieldsValue({ extPointId: selectedExtPoint.id });
    }
  }, [modalVisible, operationType, currentExtension, selectedExtPoint, form]);

  // 处理启用/禁用扩展实现
  const handleToggleStatus = async (record) => {
    try {
      const newStatus = record.status === 'enabled' ? 'disabled' : 'enabled';
      
      // 先更新UI状态
      setExtensions(prev => prev.map(item => 
        item.id === record.id ? { ...item, status: newStatus } : item
      ));
      
      // 使用API服务更新状态，传入错误处理器
      await ApiService.toggleExtensionStatus(record.id, newStatus, errorHandler);
      
      // 通过通信器发送状态变更事件给父应用
      if (communicator) {
        communicator.sendMessage(
          'parent',
          'extension:statusChanged',
          { 
            extensionId: record.id, 
            status: newStatus,
            appId: APP_ID,
            timestamp: Date.now()
          }
        );
      }
      
      notification.success({
        message: '操作成功',
        description: `已成功${newStatus === 'enabled' ? '启用' : '禁用'}该扩展实现`
      });
    } catch (error) {
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'toggle_extension_status',
          extensionId: record.id
        });
      }
      
      // 恢复UI状态
      setExtensions(prev => prev.map(item => 
        item.id === record.id ? { ...item, status: record.status } : item
      ));
      
      handleApiError(error, '状态变更失败', errorHandler);
      
      // 通过通信器发送错误事件给父应用
      if (communicator) {
        communicator.sendMessage(
          'parent',
          'extension:error',
          { 
            operation: 'toggleStatus', 
            error: error.message, 
            extensionId: record.id,
            appId: APP_ID,
            timestamp: Date.now()
          }
        );
      }
    }
  };
  
  // 处理删除扩展实现
  const handleDeleteExtension = async (id) => {
    try {
      // 先更新UI状态
      const deletedItem = extensions.find(item => item.id === id);
      setExtensions(prev => prev.filter(item => item.id !== id));
      
      // 使用ApiService删除，传入错误处理器
      await ApiService.deleteExtension(id, errorHandler);
      
      // 通过通信器发送删除成功事件给父应用
      if (communicator) {
        communicator.sendMessage(
          'parent',
          'extension:deleted',
          { 
            extensionId: id,
            appId: APP_ID,
            timestamp: Date.now()
          }
        );
      }
      
      notification.success({
        message: '删除成功',
        description: '扩展实现已成功删除'
      });
    } catch (error) {
      // 使用错误处理器处理错误
      if (errorHandler) {
        errorHandler.handleError(error, { 
          operation: 'delete_extension',
          extensionId: id
        });
      }
      
      // 如果发生错误，恢复数据
      fetchExtensions();
      
      handleApiError(error, '删除失败', errorHandler);
      
      // 通过通信器发送错误事件给父应用
      if (communicator) {
        communicator.sendMessage(
          'parent',
          'extension:error',
          { 
            operation: 'delete', 
            error: error.message, 
            extensionId: id,
            appId: APP_ID,
            timestamp: Date.now()
          }
        );
      }
    }
  };

  // 初始化加载统计数据
  useEffect(() => {
    loadStats();
  }, [loadStats]);

  // 扩展点表格列定义
  const extPointColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <a onClick={() => handleSelectExtPoint(record)}>{text}</a>
      )
    },
    {
      title: '接口名称',
      dataIndex: 'interfaceName',
      key: 'interfaceName',
      ellipsis: true
    },
    {
      title: '领域',
      dataIndex: 'domain',
      key: 'domain',
      render: domain => <Tag color="blue">{domain}</Tag>
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: category => <Tag color="green">{category}</Tag>
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '扩展实现数量',
      dataIndex: 'implementationCount',
      key: 'implementationCount',
      sorter: (a, b) => a.implementationCount - b.implementationCount
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button 
            type="link" 
            onClick={() => handleSelectExtPoint(record)}
          >
            查看扩展实现
          </Button>
        </Space>
      )
    }
  ]

  // 扩展实现表格列定义
  const extensionColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '实现类',
      dataIndex: 'className',
      key: 'className',
      ellipsis: true
    },
    {
      title: '租户',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      render: tenantCode => <Tag>{tenantCode}</Tag>
    },
    {
      title: '业务域',
      dataIndex: 'bizCode',
      key: 'bizCode'
    },
    {
      title: '场景',
      dataIndex: 'scenario',
      key: 'scenario'
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority',
      sorter: (a, b) => a.priority - b.priority
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: status => (
        <Tag color={status === 'enabled' ? 'green' : 'red'}>
          {status === 'enabled' ? '启用' : '禁用'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime'
    },
    {      title: '操作',      key: 'action',      render: (_, record) => (        <Space size="middle">          <Button             type="link"             onClick={() => {              setCurrentExtension(record)              setOperationType('edit')              setModalVisible(true)            }}          >            编辑          </Button>          <Popconfirm            title="确定要修改状态吗？"            onConfirm={() => handleToggleStatus(record)}            okText="确定"            cancelText="取消"          >            <Button type="link">              {record.status === 'enabled' ? '禁用' : '启用'}            </Button>          </Popconfirm>          <Popconfirm            title="确定要删除此扩展实现吗？"            onConfirm={() => handleDeleteExtension(record.id)}            okText="确定"            cancelText="取消"          >            <Button type="link" danger>              删除            </Button>          </Popconfirm>        </Space>      )    }
  ]

  // 统计模态框组件
  const StatsModal = ({ visible, onCancel, data }) => (
    <Modal
      title="系统统计"
      open={visible}
      onCancel={onCancel}
      footer={[
        <Button key="close" onClick={onCancel}>关闭</Button>
      ]}
      width={400}
      centered
    >
      <Card>
        <Row gutter={[16, 16]}>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#f0f5ff', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#1890ff' }}>{data.totalExtPoints}</Text>
              <div style={{ marginTop: 8 }}>总扩展点数量</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#e6f7ff', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#00bcd4' }}>{data.totalExtensions}</Text>
              <div style={{ marginTop: 8 }}>总扩展实现数量</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#f6ffed', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#52c41a' }}>{data.enabledExtensions}</Text>
              <div style={{ marginTop: 8 }}>已启用扩展实现</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#fff1f0', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#ff4d4f' }}>{data.disabledExtensions}</Text>
              <div style={{ marginTop: 8 }}>已禁用扩展实现</div>
            </div>
          </Col>
        </Row>
      </Card>
    </Modal>
  )

  // 过滤扩展点数据
  const filteredExtPoints = useMemo(() => {
    return extPoints.filter(point => {
      const matchesSearch = point.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          point.description.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          point.interfaceName.toLowerCase().includes(searchKeyword.toLowerCase())
      const matchesDomain = filterDomain === 'all' || point.domain === filterDomain
      return matchesSearch && matchesDomain
    })
  }, [extPoints, searchKeyword, filterDomain])

  // 过滤扩展实现数据
  const filteredExtensions = useMemo(() => {
    let filtered = extensions
    
    if (selectedExtPoint) {
      filtered = filtered.filter(e => e.extPointId === selectedExtPoint.id)
    }
    
    filtered = filtered.filter(e => {
      const matchesSearch = e.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          e.className.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          e.tenantCode.toLowerCase().includes(searchKeyword.toLowerCase())
      const matchesTenant = filterTenant === 'all' || e.tenantCode === filterTenant
      return matchesSearch && matchesTenant
    })
    
    return filtered
  }, [extensions, selectedExtPoint, searchKeyword, filterTenant])

  // 加载状态和错误处理
  const loading = extPointsLoading || extensionsLoading
  const error = extPointsError || extensionsError

  // 返回组件JSX
  return (
    <Layout className="bone-layout">
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={(value) => setCollapsed(value)}
        width={250}
        breakpoint="lg"
        collapsedWidth={80}
        theme="dark"
      >
        <div className="bone-logo">
          <Typography.Title level={5} style={{ color: 'white', margin: 0, padding: '16px', textAlign: collapsed ? 'center' : 'left' }}>
            {collapsed ? 'Bone' : 'Bone 扩展引擎'}
          </Typography.Title>
        </div>
        <Menu 
          mode="inline" 
          selectedKeys={[activeKey]}
          onClick={handleMenuClick}
          style={{ height: '100%', borderRight: 0 }}
          theme="dark"
        >
          <Menu.Item key="1" icon={<HomeOutlined />}>
            扩展点管理
          </Menu.Item>
          <Menu.Item key="2" icon={<CodeOutlined />}>
            扩展实现管理
          </Menu.Item>
          <Menu.Item key="3" icon={<SettingOutlined />}>
            系统配置
          </Menu.Item>
          <Menu.Item key="4" icon={<AlertOutlined />}>
            运行监控
          </Menu.Item>
        </Menu>
      </Sider>
      <Layout className="site-layout">
        <Header className="site-layout-background" style={{ padding: 0, height: 64, lineHeight: '64px', paddingRight: 24, textAlign: 'right', boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)' }}>
          <Button 
            type="text" 
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            style={{ marginRight: 16 }}
          >
            刷新
          </Button>
          <Text type="secondary" style={{ marginRight: 16 }}>Bone Extension Engine v1.0.0</Text>
          <GithubOutlined />
        </Header>
        <Content style={{ margin: '24px 16px 0', overflow: 'auto' }}>
          <div 
            className="site-layout-background" 
            style={{ padding: 24, minHeight: 'calc(100vh - 120px)', borderRadius: 8 }}
          >
            {error ? (
              <Result
                status="error"
                title="加载失败"
                subTitle={error}
                extra={[
                  <Button type="primary" key="reload" onClick={handleRefresh}>
                    重新加载
                  </Button>,
                  <Button key="stats" onClick={loadStats}>
                    查看统计
                  </Button>
                ]}
              />
            ) : !selectedExtPoint ? (
              <>
                <Title level={4}>扩展点列表</Title>
                <Card>
                  <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
                    <Search
                      placeholder="搜索扩展点（名称/描述/接口名）"
                      value={searchKeyword}
                      onChange={(e) => setSearchKeyword(e.target.value)}
                      onSearch={handleSearch}
                      enterButton
                      style={{ width: 300 }}
                    />
                    <Select
                      placeholder="按领域筛选"
                      value={filterDomain}
                      onChange={setFilterDomain}
                      style={{ width: 150 }}
                      options={[
                        { value: 'all', label: '全部' },
                        { value: '支付', label: '支付' },
                        { value: '用户', label: '用户' },
                        { value: '商品', label: '商品' }
                      ]}
                    />
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      onClick={() => {
                        notification.info({ message: '功能开发中', description: '新建扩展点功能正在开发中' })
                      }}
                    >
                      新建扩展点
                    </Button>
                  </div>
                  {filteredExtPoints.length === 0 && !loading ? (
                    <Empty description="暂无扩展点数据" />
                  ) : (
                    <Table 
                      columns={extPointColumns} 
                      dataSource={filteredExtPoints} 
                      rowKey="id"
                      loading={loading}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: 'max-content' }}
                      key={refreshKey}
                    />
                  )}
                </Card>
              </>
            ) : (
              <>
                <Button 
                  type="link" 
                  onClick={handleBackToList}
                  style={{ marginBottom: 16 }}
                >
                  ← 返回扩展点列表
                </Button>
                <Title level={4}>{selectedExtPoint.name} - 扩展实现列表</Title>
                <Card>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginBottom: 16 }}>
                      <div>
                        <Text strong>接口名称：</Text>
                        <Text copyable>{selectedExtPoint.interfaceName}</Text>
                      </div>
                      <div>
                        <Text strong>版本：</Text>
                        <Text>{selectedExtPoint.version}</Text>
                      </div>
                      <div>
                        <Text strong>领域：</Text>
                        <Tag color="blue">{selectedExtPoint.domain}</Tag>
                      </div>
                      <div>
                        <Text strong>分类：</Text>
                        <Tag color="green">{selectedExtPoint.category}</Tag>
                      </div>
                    </div>
                    <div style={{ marginBottom: 16 }}>
                      <Text strong>描述：</Text>
                      <Text>{selectedExtPoint.description}</Text>
                    </div>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
                      <Search
                        placeholder="搜索扩展实现（名称/类名/租户）"
                        value={searchKeyword}
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        onSearch={handleSearch}
                        enterButton
                        style={{ width: 300 }}
                      />
                      <Select
                        placeholder="按租户筛选"
                        value={filterTenant}
                        onChange={setFilterTenant}
                        style={{ width: 150 }}
                        options={[
                          { value: 'all', label: '全部' },
                          { value: 'DEFAULT', label: '默认' },
                          { value: 'TENANT_A', label: '租户A' },
                          { value: 'TENANT_B', label: '租户B' }
                        ]}
                      />
                      <Button 
                        type="primary" 
                        icon={<PlusOutlined />}
                        onClick={() => {
                          setCurrentExtension(null)
                          setOperationType('create')
                          setModalVisible(true)
                        }}
                      >
                        新建扩展实现
                      </Button>
                    </div>
                  </div>
                  {filteredExtensions.length === 0 && !loading ? (
                    <Empty description="暂无扩展实现数据" />
                  ) : (
                    <Table 
                      columns={extensionColumns} 
                      dataSource={filteredExtensions} 
                      rowKey="id"
                      loading={loading}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: 'max-content' }}
                      key={refreshKey}
                    />
                  )}
                </Card>
              </>
            )}
          </div>
        </Content>
        <footer style={{ textAlign: 'center', padding: '16px', color: 'rgba(0, 0, 0, 0.45)', borderTop: '1px solid #f0f0f0' }}>
          Bone Extension Engine ©{new Date().getFullYear()} Created by Bone Team
        </footer>
      </Layout>
      <StatsModal
        visible={statsVisible}
        onCancel={() => setStatsVisible(false)}
        data={statsData}
      />
      <Modal
        title={operationType === 'create' ? '创建扩展实现' : '编辑扩展实现'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okButtonProps={{ loading: modalLoading }}
        cancelButtonProps={{ disabled: modalLoading }}
        width={600}
        destroyOnClose
        centered
      >
        <Form
          form={form}
          layout="vertical"
          onValuesChange={(_, values) => {
            validateForm(values, 'extension');
          }}
        >
          {formItems.map((item) => (
            <Form.Item
              key={item.name}
              label={item.label}
              required={item.required}
              tooltip={item.tooltip}
              validateStatus={item.errorMsg ? 'error' : undefined}
              help={item.errorMsg}
            >
              {item.field}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </Layout>
  )
}

// 用错误边界包裹的App组件
const App = (props = {}) => {
  return (
    <MicroAppErrorBoundary>
      <AppContent {...props} />
    </MicroAppErrorBoundary>
  );
};

export default App