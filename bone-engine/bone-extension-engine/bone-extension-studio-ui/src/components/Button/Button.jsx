import React from 'react';
import './Button.css';

/**
 * Button 组件 - 支持微前端环境的按钮组件
 */
const Button = React.forwardRef(({
  type = 'default',
  size = 'medium',
  disabled = false,
  loading = false,
  children,
  onClick,
  ...props
}, ref) => {
  // 检测是否在微前端环境中
  const isMicroApp = React.useMemo(() => {
    return window.__POWERED_BY_WUJIE__ || 
           window.__MICRO_APP_ENVIRONMENT__ || 
           window.__MICRO_DEV_ENV__;
  }, []);

  // 处理点击事件
  const handleClick = (e) => {
    if (disabled || loading) return;
    
    // 在微前端环境中可以发送消息
    if (isMicroApp) {
      if (window.__MICRO_DEV_KIT__) {
        window.__MICRO_DEV_KIT__.sendMessage('button:clicked', {
          buttonType: type,
          buttonSize: size,
          buttonText: children,
        });
      }
    }
    
    if (onClick) onClick(e);
  };

  // 组合className
  const className = [
    'custom-button',
    `custom-button--${type}`,
    `custom-button--${size}`,
    disabled && 'custom-button--disabled',
    loading && 'custom-button--loading',
    isMicroApp && 'custom-button--micro-app'
  ].filter(Boolean).join(' ');

  return (
    <button
      ref={ref}
      className={className}
      disabled={disabled || loading}
      onClick={handleClick}
      {...props}
    >
      {loading && <span className="custom-button__loading">⏳</span>}
      {children}
    </button>
  );
});

Button.displayName = 'Button';

export default Button;