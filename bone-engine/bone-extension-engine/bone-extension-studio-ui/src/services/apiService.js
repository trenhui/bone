import axios from 'axios';
import { notification } from 'antd';

// 创建axios实例
const apiClient = axios.create({
  baseURL: '/api', // API基础路径
  timeout: 30000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
});

/**
 * 统一的请求拦截器
 * 添加认证信息、请求日志等
 */
apiClient.interceptors.request.use(
  config => {
    // 从本地存储获取token并添加到请求头
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    
    // 开发环境下记录请求日志
    if (process.env.NODE_ENV === 'development') {
      console.log('API Request:', {
        url: config.url,
        method: config.method,
        params: config.params,
        data: config.data
      });
    }
    
    return config;
  },
  error => {
    console.error('Request error:', error);
    return Promise.reject(error);
  }
);

/**
 * 统一的响应拦截器
 * 处理响应数据格式、错误处理等
 */
apiClient.interceptors.response.use(
  response => {
    // 开发环境下记录响应日志
    if (process.env.NODE_ENV === 'development') {
      console.log('API Response:', {
        url: response.config.url,
        status: response.status,
        data: response.data
      });
    }
    
    // 处理统一的API响应格式
    const { data } = response;
    
    // 如果响应中包含ApiResponse格式的数据
    if (data && typeof data === 'object') {
      if (data.success === false) {
        // 显示错误通知
        notification.error({
          message: '操作失败',
          description: data.message || '未知错误'
        });
        return Promise.reject(new Error(data.message || '请求失败'));
      }
      return data.data; // 直接返回data字段的数据
    }
    
    return data;
  },
  error => {
    // 开发环境下记录错误日志
    if (process.env.NODE_ENV === 'development') {
      console.error('API Error:', error);
    }
    
    // 统一错误处理
    const errorMessage = getErrorMessage(error);
    
    // 显示错误通知
    notification.error({
      message: '请求错误',
      description: errorMessage
    });
    
    return Promise.reject(error);
  }
);

/**
 * 获取友好的错误消息
 * @param {Error} error - 错误对象
 * @returns {string} 友好的错误消息
 */
const getErrorMessage = (error) => {
  if (error.response) {
    // 服务器返回错误状态码
    const { status, data } = error.response;
    
    switch (status) {
      case 400:
        return data.message || '请求参数错误';
      case 401:
        return '未授权，请重新登录';
      case 403:
        return '没有权限执行此操作';
      case 404:
        return '请求的资源不存在';
      case 500:
        return '服务器内部错误';
      default:
        return data.message || `请求失败，状态码: ${status}`;
    }
  } else if (error.request) {
    // 请求已发出但没有收到响应
    return '网络错误，请检查您的网络连接';
  } else {
    // 请求配置出错
    return error.message || '请求失败';
  }
};

/**
   * 扩展实现相关API
   */
export const extensionApi = {
  /**
   * 获取扩展实现列表（分页）
   * @param {Object} params - 查询参数
   * @returns {Promise} 查询结果
   */
  getExtensions: (params) => apiClient.get('/extensions', { params }),
  
  /**
   * 根据ID获取扩展实现详情
   * @param {number} id - 扩展实现ID
   * @returns {Promise} 扩展实现详情
   */
  getExtensionById: (id) => apiClient.get(`/extensions/${id}`),
  
  /**
   * 创建扩展实现
   * @param {Object} extension - 扩展实现数据
   * @returns {Promise} 创建结果
   */
  createExtension: (extension) => apiClient.post('/extensions', extension),
  
  /**
   * 更新扩展实现
   * @param {number} id - 扩展实现ID
   * @param {Object} extension - 扩展实现数据
   * @returns {Promise} 更新结果
   */
  updateExtension: (id, extension) => apiClient.put(`/extensions/${id}`, extension),
  
  /**
   * 删除扩展实现
   * @param {number} id - 扩展实现ID
   * @returns {Promise} 删除结果
   */
  deleteExtension: (id) => apiClient.delete(`/extensions/${id}`),
  
  /**
   * 启用/禁用扩展实现
   * @param {number} id - 扩展实现ID
   * @param {boolean} enabled - 是否启用
   * @returns {Promise} 更新结果
   */
  enableExtension: (id, enabled) => apiClient.patch(`/extensions/${id}/status`, { status: enabled ? 'enabled' : 'disabled' }),
  
  /**
   * 更新扩展实现优先级
   * @param {number} id - 扩展实现ID
   * @param {number} priority - 优先级
   * @returns {Promise} 更新结果
   */
  updateExtensionPriority: (id, priority) => apiClient.patch(`/extensions/${id}/priority`, { priority }),
  
  /**
   * 扫描并注册扩展实现
   * @returns {Promise} 扫描结果
   */
  scanAndRegisterExtensions: () => apiClient.post('/extensions/scan'),
  
  /**
   * 验证扩展实现
   * @param {number} id - 扩展实现ID
   * @returns {Promise} 验证结果
   */
  validateExtension: (id) => apiClient.post(`/extensions/${id}/validate`),
  
  /**
   * 获取扩展实现统计信息
   * @returns {Promise} 统计信息
   */
  getExtensionStatistics: () => apiClient.get('/extensions/statistics'),
  
  /**
   * 重置扩展实现统计信息
   * @param {number} id - 扩展实现ID
   * @returns {Promise} 重置结果
   */
  resetExtensionStatistics: (id) => apiClient.post(`/extensions/${id}/statistics/reset`),
  
  /**
   * 获取扩展实现统计详情
   * @returns {Promise} 统计详情
   */
  getExtensionStats: () => apiClient.get('/extensions/stats')
};

/**
 * 扩展点相关API
 */
export const extPointApi = {
  /**
   * 获取扩展点列表（分页）
   * @param {Object} params - 查询参数
   * @returns {Promise} 查询结果
   */
  getExtPoints: (params) => apiClient.get('/ext-points', { params }),
  
  /**
   * 根据ID获取扩展点详情
   * @param {string} id - 扩展点ID
   * @returns {Promise} 扩展点详情
   */
  getExtPointById: (id) => apiClient.get(`/ext-points/${id}`)
};

/**
 * 扩展点配置相关API
 */
export const extPointConfigApi = {
  /**
   * 获取扩展点配置列表
   * @returns {Promise} 配置列表
   */
  getExtensionPointConfigs: () => apiClient.get('/extension-point-configs'),
  
  /**
   * 根据ID获取扩展点配置详情
   * @param {string} id - 配置ID
   * @returns {Promise} 配置详情
   */
  getExtensionPointConfig: (id) => apiClient.get(`/extension-point-configs/${id}`),
  
  /**
   * 创建扩展点配置
   * @param {Object} config - 配置数据
   * @returns {Promise} 创建结果
   */
  createExtensionPointConfig: (config) => apiClient.post('/extension-point-configs', config),
  
  /**
   * 更新扩展点配置
   * @param {string} id - 配置ID
   * @param {Object} config - 配置数据
   * @returns {Promise} 更新结果
   */
  updateExtensionPointConfig: (id, config) => apiClient.put(`/extension-point-configs/${id}`, config),
  
  /**
   * 删除扩展点配置
   * @param {string} id - 配置ID
   * @returns {Promise} 删除结果
   */
  deleteExtensionPointConfig: (id) => apiClient.delete(`/extension-point-configs/${id}`),
  
  /**
   * 根据扩展点ID获取配置列表
   * @param {string} extPointId - 扩展点ID
   * @returns {Promise} 配置列表
   */
  getExtensionPointConfigsByExtPointId: (extPointId) => apiClient.get(`/extension-point-configs/by-extension-point/${extPointId}`)
};

// 导出API服务对象
export default {
  extension: extensionApi,
  extPoint: extPointApi,
  extensionPointConfig: extPointConfigApi,
  client: apiClient
};
