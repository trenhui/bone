import React, { useMemo } from 'react';
import PropTypes from 'prop-types';
import './Button.css';

/**
 * Button 组件 - 支持微前端环境的按钮组件
 * 遵循React最佳实践：
 * - 使用PropTypes进行类型检查
 * - 采用useMemo优化性能
 * - 提供完整的可访问性支持
 * - 错误边界处理
 */
const Button = React.forwardRef(({
  type = 'default',
  size = 'medium',
  disabled = false,
  loading = false,
  children,
  onClick,
  variant = 'filled',
  icon,
  ...props
}, ref) => {
  // 检测是否在微前端环境中
  const isMicroApp = useMemo(() => {
    // 安全检查，避免window未定义的问题
    if (typeof window === 'undefined') return false;
    
    return Boolean(
      window.__POWERED_BY_WUJIE__ || 
      window.__MICRO_APP_ENVIRONMENT__ || 
      window.__MICRO_DEV_ENV__
    );
  }, []);

  // 处理点击事件
  const handleClick = (e) => {
    if (disabled || loading) return;
    
    try {
      // 在微前端环境中可以发送消息
      if (isMicroApp && typeof window.__MICRO_DEV_KIT__ === 'object' && 
          typeof window.__MICRO_DEV_KIT__.sendMessage === 'function') {
        window.__MICRO_DEV_KIT__.sendMessage('button:clicked', {
          buttonType: type,
          buttonSize: size,
          buttonText: String(children).trim(),
          timestamp: Date.now()
        });
      }
      
      if (typeof onClick === 'function') {
        onClick(e);
      }
    } catch (error) {
      console.error('Button click handler failed:', error);
      // 不中断执行流程
    }
  };

  // 组合className
  const className = useMemo(() => {
    return [
      'custom-button',
      `custom-button--${type}`,
      `custom-button--${size}`,
      `custom-button--${variant}`,
      disabled && 'custom-button--disabled',
      loading && 'custom-button--loading',
      isMicroApp && 'custom-button--micro-app',
      icon && 'custom-button--with-icon'
    ].filter(Boolean).join(' ');
  }, [type, size, variant, disabled, loading, isMicroApp, icon]);
  
  // 设置按钮属性
  const buttonProps = useMemo(() => ({
    ...props,
    className,
    disabled: disabled || loading,
    onClick: handleClick,
    type: props.type || 'button', // 默认type为button避免表单提交
    ref
  }), [className, disabled, loading, handleClick, props, ref]);

  return (
      <button {...buttonProps}>
        {loading && <span className="custom-button__loading">加载中...</span>}
        {!loading && icon && <span className="custom-button__icon">{icon}</span>}
        {!loading && children}
      </button>
    );
  });

Button.displayName = 'Button';

// 添加PropTypes类型检查
Button.propTypes = {
  // 按钮类型
  type: PropTypes.oneOf(['default', 'primary', 'secondary', 'success', 'error', 'warning', 'info']),
  // 按钮大小
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  // 变体样式
  variant: PropTypes.oneOf(['filled', 'outlined', 'text']),
  // 是否禁用
  disabled: PropTypes.bool,
  // 是否加载中
  loading: PropTypes.bool,
  // 点击事件处理函数
  onClick: PropTypes.func,
  // 图标组件
  icon: PropTypes.node,
  // 子元素
  children: PropTypes.node
};

export default Button;