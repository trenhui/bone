import { useState, useRef, useCallback } from 'react';

interface UseButtonOptions extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  disabled?: boolean;
  isFocusable?: boolean;
}

export function useButton(options: UseButtonOptions = {}) {
  const { 
    disabled = false, 
    isFocusable = true,
    onClick,
    onKeyDown,
    ...props 
  } = options;

  const [isFocused, setIsFocused] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [isActive, setIsActive] = useState(false);
  const ref = useRef<HTMLButtonElement>(null);

  // 计算实际的禁用状态
  const isDisabled = disabled || !isFocusable;

  // 处理点击事件
  const handleClick = useCallback((event: React.MouseEvent<HTMLButtonElement>) => {
    if (isDisabled) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }

    setIsActive(true);
    setTimeout(() => setIsActive(false), 200);

    if (onClick) {
      onClick(event);
    }
  }, [isDisabled, onClick]);

  // 处理键盘事件
  const handleKeyDown = useCallback((event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (isDisabled) {
      return;
    }

    switch (event.key) {
      case 'Enter':
      case ' ': {
        event.preventDefault();
        setIsActive(true);
        
        // 模拟点击行为
        ref.current?.click();
        
        // 触发 onClick 事件
        if (onClick) {
          const mouseEvent = new MouseEvent('click', {
            bubbles: true,
            cancelable: true,
            view: window
          });
          onClick(mouseEvent as any);
        }
        
        // 重置激活状态
        setTimeout(() => setIsActive(false), 200);
        break;
      }
      case 'Escape':
        if (isFocused) {
          ref.current?.blur();
        }
        break;
      default:
        break;
    }

    if (onKeyDown) {
      onKeyDown(event);
    }
  }, [isDisabled, isFocused, onClick, onKeyDown]);

  // 处理焦点事件
  const handleFocus = useCallback(() => {
    if (!isDisabled) {
      setIsFocused(true);
    }
  }, [isDisabled]);

  // 处理失焦事件
  const handleBlur = useCallback(() => {
    setIsFocused(false);
    setIsActive(false);
  }, []);

  // 处理鼠标进入事件
  const handleMouseEnter = useCallback(() => {
    if (!isDisabled) {
      setIsHovered(true);
    }
  }, [isDisabled]);

  // 处理鼠标离开事件
  const handleMouseLeave = useCallback(() => {
    setIsHovered(false);
    setIsActive(false);
  }, []);

  // 处理鼠标按下事件
  const handleMouseDown = useCallback(() => {
    if (!isDisabled) {
      setIsActive(true);
    }
  }, [isDisabled]);

  // 处理鼠标释放事件
  const handleMouseUp = useCallback(() => {
    setIsActive(false);
  }, []);

  // 焦点管理方法
  const focus = useCallback(() => {
    if (!isDisabled && ref.current) {
      ref.current.focus();
    }
  }, [isDisabled]);

  const blur = useCallback(() => {
    if (ref.current) {
      ref.current.blur();
    }
  }, []);

  // 按钮属性
  const buttonProps: React.ButtonHTMLAttributes<HTMLButtonElement> = {
    ...props,
    ref: (node: HTMLButtonElement | null) => {
      ref.current = node;
      if (props.ref && typeof props.ref === 'function') {
        props.ref(node);
      } else if (props.ref && 'current' in props.ref) {
        (props.ref as React.MutableRefObject<HTMLButtonElement | null>).current = node;
      }
    },
    disabled: isDisabled,
    tabIndex: isDisabled ? -1 : 0,
    role: 'button',
    onClick: handleClick,
    onKeyDown: handleKeyDown,
    onFocus: handleFocus,
    onBlur: handleBlur,
    onMouseEnter: handleMouseEnter,
    onMouseLeave: handleMouseLeave,
    onMouseDown: handleMouseDown,
    onMouseUp: handleMouseUp,
    'aria-disabled': isDisabled,
    'data-focus': isFocused,
    'data-hover': isHovered,
    'data-active': isActive
  };

  return {
    buttonProps,
    isDisabled,
    isFocused,
    isHovered,
    isActive,
    focus,
    blur,
    ref
  };
}