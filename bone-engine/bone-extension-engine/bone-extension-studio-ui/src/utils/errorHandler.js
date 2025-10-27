/**
 * 统一错误处理器
 * 提供标准化的错误处理、分类、日志记录和上报功能
 * 遵循最佳实践：
 * - 错误分类与标准化
 * - 详细的错误日志
 * - 支持错误上报
 * - 提供友好的用户提示
 */
import { notification } from 'antd';

// 应用ID常量
const APP_ID = 'bone-extension-studio-ui';

// 错误类型常量
export const ERROR_TYPES = {
  API_ERROR: 'api_error',
  NETWORK_ERROR: 'network_error',
  VALIDATION_ERROR: 'validation_error',
  BUSINESS_ERROR: 'business_error',
  UNKNOWN_ERROR: 'unknown_error'
};

// 错误严重级别
export const ERROR_LEVELS = {
  INFO: 'info',
  WARNING: 'warning',
  ERROR: 'error',
  CRITICAL: 'critical'
};

class ErrorHandler {
  constructor(options = {}) {
    this.options = {
      enableErrorReporting: true,
      enableDetailedLogging: process.env.NODE_ENV === 'development',
      maxErrorHistory: 100,
      notifyUserOnError: true,
      ...options
    };
    
    this.errorHistory = [];
    this.errorStats = {
      total: 0,
      byType: {},
      byOperation: {},
      lastErrorTime: null
    };
  }
  
  /**
   * 处理错误
   * @param {Error|Object} error - 错误对象
   * @param {Object} context - 错误上下文信息
   * @returns {Object} 处理后的错误对象
   */
  handleError(error, context = {}) {
    const processedError = this._processError(error, context);
    
    // 记录错误
    this._logError(processedError);
    
    // 更新错误统计
    this._updateErrorStats(processedError);
    
    // 保存到历史记录
    this._saveToHistory(processedError);
    
    // 上报错误
    if (this.options.enableErrorReporting) {
      this._reportError(processedError);
    }
    
    // 显示用户通知
    if (this.options.notifyUserOnError && this._shouldNotifyUser(processedError)) {
      this._showUserNotification(processedError);
    }
    
    return processedError;
  }
  
  /**
   * 处理API错误
   * @param {Error} error - Axios错误对象
   * @param {string} operation - 操作名称
   * @returns {Object} 处理后的错误对象
   */
  handleApiError(error, operation = 'unknown') {
    return this.handleError(error, {
      type: ERROR_TYPES.API_ERROR,
      operation,
      timestamp: Date.now(),
      source: 'api_call'
    });
  }
  
  /**
   * 处理业务错误
   * @param {string} message - 错误消息
   * @param {Object} details - 错误详情
   * @returns {Object} 处理后的错误对象
   */
  handleBusinessError(message, details = {}) {
    const error = new Error(message);
    error.details = details;
    
    return this.handleError(error, {
      type: ERROR_TYPES.BUSINESS_ERROR,
      timestamp: Date.now(),
      source: 'business_logic'
    });
  }
  
  /**
   * 处理验证错误
   * @param {Object} validationErrors - 验证错误详情
   * @returns {Object} 处理后的错误对象
   */
  handleValidationError(validationErrors) {
    const error = new Error('数据验证失败');
    error.validationErrors = validationErrors;
    
    return this.handleError(error, {
      type: ERROR_TYPES.VALIDATION_ERROR,
      timestamp: Date.now(),
      source: 'validation'
    });
  }
  
  /**
   * 格式化错误消息
   * @param {Object} error - 错误对象
   * @returns {string} 格式化的错误消息
   */
  formatErrorMessage(error) {
    switch (error.type) {
      case ERROR_TYPES.API_ERROR:
        return this._formatApiErrorMessage(error);
      case ERROR_TYPES.VALIDATION_ERROR:
        return this._formatValidationErrorMessage(error);
      case ERROR_TYPES.BUSINESS_ERROR:
        return this._formatBusinessErrorMessage(error);
      default:
        return error.message || '发生未知错误';
    }
  }
  
  /**
   * 获取错误统计信息
   * @returns {Object} 错误统计
   */
  getErrorStats() {
    return { ...this.errorStats };
  }
  
  /**
   * 获取最近的错误
   * @param {number} count - 获取数量
   * @returns {Array} 错误历史
   */
  getRecentErrors(count = 10) {
    return this.errorHistory.slice(0, count);
  }
  
  /**
   * 清除错误历史
   */
  clearErrorHistory() {
    this.errorHistory = [];
  }
  
  /**
   * 处理原始错误，添加标准化信息
   * @private
   */
  _processError(error, context) {
    const processed = {
      ...context,
      message: error.message || '未知错误',
      stack: error.stack,
      timestamp: context.timestamp || Date.now(),
      level: ERROR_LEVELS.ERROR,
      originalError: error
    };
    
    // 分类错误
    if (!processed.type) {
      processed.type = this._categorizeError(error);
    }
    
    // 从Axios错误中提取更多信息
    if (error.response) {
      processed.response = {
        status: error.response.status,
        statusText: error.response.statusText,
        data: error.response.data
      };
      
      // 使用响应中的错误消息
      if (error.response.data?.message) {
        processed.message = error.response.data.message;
      }
      
      // 根据HTTP状态码确定严重级别
      processed.level = this._determineLevelByStatus(error.response.status);
    } else if (error.request) {
      processed.type = ERROR_TYPES.NETWORK_ERROR;
      processed.level = ERROR_LEVELS.WARNING;
    }
    
    return processed;
  }
  
  /**
   * 错误分类
   * @private
   */
  _categorizeError(error) {
    if (error.response) {
      return ERROR_TYPES.API_ERROR;
    } else if (error.request) {
      return ERROR_TYPES.NETWORK_ERROR;
    } else if (error.validationErrors) {
      return ERROR_TYPES.VALIDATION_ERROR;
    } else if (error.details) {
      return ERROR_TYPES.BUSINESS_ERROR;
    } else {
      return ERROR_TYPES.UNKNOWN_ERROR;
    }
  }
  
  /**
   * 根据HTTP状态码确定错误级别
   * @private
   */
  _determineLevelByStatus(status) {
    if (status >= 500) return ERROR_LEVELS.CRITICAL;
    if (status >= 400) return ERROR_LEVELS.ERROR;
    if (status >= 300) return ERROR_LEVELS.WARNING;
    return ERROR_LEVELS.INFO;
  }
  
  /**
   * 记录错误日志
   * @private
   */
  _logError(error) {
    if (!this.options.enableDetailedLogging) {
      console.error(`[${error.type}] ${error.message}`);
      return;
    }
    
    console.group(`[${error.timestamp}] Error (${error.type})`);
    console.error('Message:', error.message);
    console.error('Level:', error.level);
    console.error('Operation:', error.operation || 'unknown');
    
    if (error.response) {
      console.error('Response:', {
        status: error.response.status,
        data: error.response.data
      });
    }
    
    if (error.stack) {
      console.error('Stack:', error.stack);
    }
    
    if (error.params) {
      console.error('Params:', error.params);
    }
    
    console.groupEnd();
  }
  
  /**
   * 更新错误统计
   * @private
   */
  _updateErrorStats(error) {
    this.errorStats.total++;
    this.errorStats.lastErrorTime = error.timestamp;
    
    // 按类型统计
    if (!this.errorStats.byType[error.type]) {
      this.errorStats.byType[error.type] = 0;
    }
    this.errorStats.byType[error.type]++;
    
    // 按操作统计
    if (error.operation) {
      if (!this.errorStats.byOperation[error.operation]) {
        this.errorStats.byOperation[error.operation] = 0;
      }
      this.errorStats.byOperation[error.operation]++;
    }
  }
  
  /**
   * 保存错误到历史记录
   * @private
   */
  _saveToHistory(error) {
    this.errorHistory.unshift(error);
    
    // 限制历史记录数量
    if (this.errorHistory.length > this.options.maxErrorHistory) {
      this.errorHistory = this.errorHistory.slice(0, this.options.maxErrorHistory);
    }
  }
  
  /**
   * 上报错误到主应用
   * @private
   */
  _reportError(error) {
    try {
      if (window.__bone_communicator__) {
        window.__bone_communicator__.sendMessage('main', 'error:report', {
          appId: APP_ID,
          error: {
            type: error.type,
            message: error.message,
            level: error.level,
            operation: error.operation,
            timestamp: error.timestamp,
            status: error.response?.status
          }
        });
      }
    } catch (reportError) {
      console.warn('错误上报失败:', reportError);
    }
  }
  
  /**
   * 判断是否应该显示用户通知
   * @private
   */
  _shouldNotifyUser(error) {
    // 根据错误级别决定
    return error.level === ERROR_LEVELS.ERROR || 
           error.level === ERROR_LEVELS.CRITICAL;
  }
  
  /**
   * 显示用户通知
   * @private
   */
  _showUserNotification(error) {
    notification[error.level === ERROR_LEVELS.CRITICAL ? 'error' : 'warning']({
      message: this._getNotificationTitle(error),
      description: this.formatErrorMessage(error),
      key: `error-${error.timestamp}`,
      duration: error.level === ERROR_LEVELS.CRITICAL ? 10 : 5
    });
  }
  
  /**
   * 获取通知标题
   * @private
   */
  _getNotificationTitle(error) {
    switch (error.type) {
      case ERROR_TYPES.API_ERROR:
        return 'API请求失败';
      case ERROR_TYPES.NETWORK_ERROR:
        return '网络连接异常';
      case ERROR_TYPES.VALIDATION_ERROR:
        return '数据验证失败';
      case ERROR_TYPES.BUSINESS_ERROR:
        return '业务处理失败';
      default:
        return '操作失败';
    }
  }
  
  /**
   * 格式化API错误消息
   * @private
   */
  _formatApiErrorMessage(error) {
    if (error.response) {
      const { status } = error.response;
      
      switch (status) {
        case 400:
          return error.message || '请求参数错误，请检查输入';
        case 401:
          return '未授权，请重新登录';
        case 403:
          return '没有权限执行此操作';
        case 404:
          return '请求的资源不存在';
        case 408:
          return '请求超时，请稍后重试';
        case 429:
          return '请求过于频繁，请稍后重试';
        case 500:
          return '服务器内部错误，请联系管理员';
        case 502:
        case 503:
          return '服务暂时不可用，请稍后重试';
        default:
          return error.message || `请求失败 (${status})`;
      }
    } else if (error.request) {
      return '网络连接异常，请检查网络设置';
    }
    
    return error.message || 'API请求失败';
  }
  
  /**
   * 格式化验证错误消息
   * @private
   */
  _formatValidationErrorMessage(error) {
    if (error.validationErrors && typeof error.validationErrors === 'object') {
      const messages = [];
      
      for (const [field, msgs] of Object.entries(error.validationErrors)) {
        if (Array.isArray(msgs)) {
          messages.push(`${field}: ${msgs.join(', ')}`);
        } else {
          messages.push(`${field}: ${msgs}`);
        }
      }
      
      return messages.join('\n');
    }
    
    return error.message || '数据验证失败';
  }
  
  /**
   * 格式化业务错误消息
   * @private
   */
  _formatBusinessErrorMessage(error) {
    if (error.details) {
      return `${error.message}${error.details.code ? ` [${error.details.code}]` : ''}`;
    }
    
    return error.message || '业务处理失败';
  }
}

// 创建默认实例
const defaultErrorHandler = new ErrorHandler();

// 导出类和默认实例
export { ErrorHandler };
export default defaultErrorHandler;
