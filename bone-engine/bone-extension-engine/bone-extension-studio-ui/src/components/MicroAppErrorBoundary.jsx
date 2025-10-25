import React, { Component } from 'react';

/**
 * 微应用错误边界组件
 * 用于捕获和优雅处理渲染过程中的错误，防止错误影响整个应用
 */
class MicroAppErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorTimestamp: null
    };
    // 错误分类统计
    this.errorStats = {
      componentErrors: 0,
      networkErrors: 0,
      resourceErrors: 0,
      unknownErrors: 0
    };
  }

  /**
   * 捕获渲染期间的错误
   */
  static getDerivedStateFromError(error) {
    // 更新状态，下一次渲染将显示降级UI
    return {
      hasError: true,
      error,
      errorTimestamp: Date.now()
    };
  }

  /**
   * 组件挂载后记录错误信息
   */
  componentDidCatch(error, errorInfo) {
    // 保存错误信息用于分析
    this.setState({ errorInfo });
    
    // 分类错误
    this.classifyError(error);
    
    // 记录错误到控制台
    console.error('微应用渲染错误:', error);
    console.error('错误组件栈:', errorInfo.componentStack);
    
    // 发送错误通知给父应用
    this.notifyParentApp(error, errorInfo);
  }

  /**
   * 分类错误类型
   */
  classifyError(error) {
    if (!error) return;
    
    const message = error.message || '';
    const stack = error.stack || '';
    
    // 网络相关错误
    if (message.includes('fetch') || message.includes('axios') || message.includes('network') || 
        message.includes('timeout') || message.includes('API')) {
      this.errorStats.networkErrors++;
    }
    // 资源相关错误
    else if (message.includes('resource') || message.includes('Abort') || 
             message.includes('timeout') || stack.includes('timeout')) {
      this.errorStats.resourceErrors++;
    }
    // 组件相关错误
    else if (stack.includes('componentWillMount') || stack.includes('render') || 
             stack.includes('useState') || stack.includes('useEffect')) {
      this.errorStats.componentErrors++;
    }
    // 未知错误
    else {
      this.errorStats.unknownErrors++;
    }
  }

  /**
   * 通知父应用错误发生
   */
  notifyParentApp(error, errorInfo) {
    try {
      // 尝试通过全局通信机制发送错误
      if (window.bone && window.bone.communicator) {
        window.bone.communicator.sendMessage('main', 'microapp:error', {
          appId: 'bone-extension-studio-ui',
          errorType: error.name || 'Error',
          errorMessage: error.message,
          componentStack: errorInfo.componentStack,
          timestamp: Date.now(),
          errorStats: this.errorStats
        });
      }
    } catch (notifyError) {
      console.warn('无法通知父应用错误:', notifyError);
    }
  }

  /**
   * 重置错误状态
   */
  resetError = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorTimestamp: null
    });
    
    // 通知父应用错误已恢复
    try {
      if (window.bone && window.bone.communicator) {
        window.bone.communicator.sendMessage('main', 'microapp:recovered', {
          appId: 'bone-extension-studio-ui',
          timestamp: Date.now()
        });
      }
    } catch (notifyError) {
      console.warn('无法通知父应用恢复:', notifyError);
    }
  };

  /**
   * 渲染降级UI或正常内容
   */
  render() {
    const { hasError, error, errorTimestamp } = this.state;
    const { fallbackComponent: FallbackComponent, children } = this.props;
    
    if (hasError) {
      // 如果提供了自定义降级组件，则使用它
      if (FallbackComponent) {
        return (
          <FallbackComponent
            error={error}
            resetError={this.resetError}
            timestamp={errorTimestamp}
            errorStats={this.errorStats}
          />
        );
      }
      
      // 默认降级UI
      return (
        <div className="microapp-error-boundary">
          <div className="error-content">
            <h2>应用发生错误</h2>
            <div className="error-details">
              <p className="error-message">{error?.message || '未知错误'}</p>
              <p className="error-time">
                发生时间: {errorTimestamp ? new Date(errorTimestamp).toLocaleString() : '未知'}
              </p>
              <div className="error-stats">
                <h4>错误统计:</h4>
                <ul>
                  <li>组件错误: {this.errorStats.componentErrors}</li>
                  <li>网络错误: {this.errorStats.networkErrors}</li>
                  <li>资源错误: {this.errorStats.resourceErrors}</li>
                  <li>未知错误: {this.errorStats.unknownErrors}</li>
                </ul>
              </div>
            </div>
            <button 
              className="retry-button" 
              onClick={this.resetError}
              aria-label="重试加载应用"
            >
              重试
            </button>
          </div>
        </div>
      );
    }
    
    // 正常情况下渲染子组件
    return children;
  }
}

// 默认样式，实际项目中可移至CSS文件
const defaultStyles = `
  .microapp-error-boundary {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 400px;
    padding: 20px;
    background-color: #f8f9fa;
    border-radius: 8px;
  }
  
  .error-content {
    text-align: center;
    max-width: 600px;
    padding: 30px;
    background-color: white;
    border-radius: 8px;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  }
  
  .error-content h2 {
    color: #dc3545;
    margin-bottom: 20px;
  }
  
  .error-details {
    text-align: left;
    margin: 20px 0;
    padding: 15px;
    background-color: #f8f9fa;
    border-radius: 4px;
    border-left: 4px solid #dc3545;
  }
  
  .error-message {
    color: #333;
    font-weight: bold;
    margin-bottom: 10px;
  }
  
  .error-time {
    color: #666;
    font-size: 0.9em;
    margin-bottom: 15px;
  }
  
  .error-stats {
    margin-top: 15px;
    font-size: 0.9em;
  }
  
  .error-stats h4 {
    margin-bottom: 8px;
    color: #495057;
  }
  
  .error-stats ul {
    list-style: none;
    padding: 0;
    margin: 0;
  }
  
  .error-stats li {
    padding: 3px 0;
    color: #666;
  }
  
  .retry-button {
    padding: 10px 20px;
    background-color: #007bff;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 16px;
    transition: background-color 0.2s;
  }
  
  .retry-button:hover {
    background-color: #0056b3;
  }
`;

// 将样式添加到文档中
if (typeof document !== 'undefined') {
  const styleElement = document.createElement('style');
  styleElement.textContent = defaultStyles;
  document.head.appendChild(styleElement);
}

export default MicroAppErrorBoundary;
