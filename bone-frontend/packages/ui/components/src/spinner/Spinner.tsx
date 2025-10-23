import React from 'react';
import { styled } from '../../styled-system';

interface SpinnerProps {
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  color?: string;
  className?: string;
}

const StyledSpinner = styled('div', {
  base: {
    display: 'inline-block',
    position: 'relative',
    animation: 'spinner-rotate 1s linear infinite',
    
    '&::after': {
      content: '""',
      position: 'absolute',
      border: '2px solid transparent',
      borderTopColor: 'currentColor',
      borderRadius: '50%',
      animation: 'spinner-grow 0.75s ease-in-out infinite'
    }
  },
  variants: {
    size: {
      xs: {
        width: '12px',
        height: '12px',
        
        '&::after': {
          borderWidth: '2px',
          width: '10px',
          height: '10px',
          top: '1px',
          left: '1px'
        }
      },
      sm: {
        width: '16px',
        height: '16px',
        
        '&::after': {
          borderWidth: '2px',
          width: '14px',
          height: '14px',
          top: '1px',
          left: '1px'
        }
      },
      md: {
        width: '24px',
        height: '24px',
        
        '&::after': {
          borderWidth: '3px',
          width: '20px',
          height: '20px',
          top: '1px',
          left: '1px'
        }
      },
      lg: {
        width: '32px',
        height: '32px',
        
        '&::after': {
          borderWidth: '3px',
          width: '28px',
          height: '28px',
          top: '2px',
          left: '2px'
        }
      },
      xl: {
        width: '48px',
        height: '48px',
        
        '&::after': {
          borderWidth: '4px',
          width: '42px',
          height: '42px',
          top: '3px',
          left: '3px'
        }
      }
    }
  },
  defaultVariants: {
    size: 'md'
  }
});

// 创建内联样式定义动画
const createAnimationStyles = () => {
  const styleId = 'bone-spinner-animations';
  
  // 检查是否已经添加过动画样式
  if (!document.getElementById(styleId)) {
    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
      @keyframes spinner-rotate {
        100% {
          transform: rotate(360deg);
        }
      }
      
      @keyframes spinner-grow {
        0% {
          transform: scale(0.8);
          opacity: 1;
        }
        100% {
          transform: scale(1.2);
          opacity: 0;
        }
      }
    `;
    document.head.appendChild(style);
  }
};

export const Spinner: React.FC<SpinnerProps> = ({
  size = 'md',
  color,
  className
}) => {
  // 创建动画样式
  React.useEffect(() => {
    createAnimationStyles();
  }, []);

  const style: React.CSSProperties = {};
  if (color) {
    style.color = color;
  }

  return (
    <StyledSpinner
      size={size}
      className={className}
      style={style}
      aria-label="Loading"
      role="status"
    />
  );
};

Spinner.displayName = 'Spinner';