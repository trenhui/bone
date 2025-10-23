import React from 'react';
import { styled } from '../../styled-system';
import { Spinner } from '../spinner/Spinner';
import { useButton } from './useButton';

// 版本常量
export const BONE_RUNTIME_VERSION = '1.0.0';

// Button 属性接口
interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  fullWidth?: boolean;
  disabled?: boolean;
}

// StyledButton 组件
const StyledButton = styled('button', {
  base: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    border: 'none',
    borderRadius: 'token(spacing.md)',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    fontFamily: 'token(typography.fonts.primary)',
    fontWeight: 500,
    textDecoration: 'none',
    minHeight: '2.5rem',
    position: 'relative',
    overflow: 'hidden',
    
    '&:focus-visible': {
      outline: '2px solid token(colors.primary.500)',
      outlineOffset: '2px'
    },
    
    '&:disabled': {
      opacity: 0.6,
      cursor: 'not-allowed',
      pointerEvents: 'none'
    },
    
    '&[data-loading="true"]': {
      opacity: 0.8,
      cursor: 'wait'
    }
  },
  variants: {
    variant: {
      primary: {
        backgroundColor: 'token(colors.primary.600)',
        color: 'white',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.primary.700)',
          transform: 'translateY(-1px)'
        },
        
        '&:active:not(:disabled)': {
          backgroundColor: 'token(colors.primary.800)',
          transform: 'translateY(0)'
        }
      },
      secondary: {
        backgroundColor: 'token(colors.secondary.200)',
        color: 'token(colors.secondary.800)',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.secondary.300)',
          transform: 'translateY(-1px)'
        },
        
        '&:active:not(:disabled)': {
          backgroundColor: 'token(colors.secondary.400)',
          transform: 'translateY(0)'
        }
      },
      outline: {
        backgroundColor: 'transparent',
        color: 'token(colors.primary.600)',
        border: '1px solid token(colors.primary.500)',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.primary.50)',
          borderColor: 'token(colors.primary.600)',
          transform: 'translateY(-1px)'
        },
        
        '&:active:not(:disabled)': {
          backgroundColor: 'token(colors.primary.100)',
          borderColor: 'token(colors.primary.700)',
          transform: 'translateY(0)'
        }
      },
      ghost: {
        backgroundColor: 'transparent',
        color: 'token(colors.text)',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.surface)',
          transform: 'translateY(-1px)'
        },
        
        '&:active:not(:disabled)': {
          backgroundColor: 'token(colors.border)',
          transform: 'translateY(0)'
        }
      }
    },
    size: {
      sm: {
        padding: 'token(spacing.xs) token(spacing.sm)',
        fontSize: 'token(typography.scales.sm.fontSize)',
        minHeight: '2rem',
        gap: 'token(spacing.xs)'
      },
      md: {
        padding: 'token(spacing.sm) token(spacing.md)',
        fontSize: 'token(typography.scales.md.fontSize)',
        minHeight: '2.5rem',
        gap: 'token(spacing.sm)'
      },
      lg: {
        padding: 'token(spacing.md) token(spacing.lg)',
        fontSize: 'token(typography.scales.lg.fontSize)',
        minHeight: '3rem',
        gap: 'token(spacing.md)'
      }
    },
    fullWidth: {
      true: {
        width: '100%',
        justifyContent: 'center'
      }
    }
  },
  defaultVariants: {
    variant: 'primary',
    size: 'md',
    fullWidth: false
  }
});

// Button 组件
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ 
    children, 
    loading = false, 
    leftIcon, 
    rightIcon, 
    variant = 'primary',
    size = 'md',
    fullWidth = false,
    disabled = false,
    ...props 
  }, ref) => {
    const { buttonProps, isDisabled } = useButton({
      ...props,
      disabled: disabled || loading
    });
    
    return (
      <StyledButton
        ref={ref}
        {...buttonProps}
        variant={variant}
        size={size}
        fullWidth={fullWidth}
        disabled={isDisabled || loading}
        data-loading={loading}
      >
        {loading && (
          <Spinner 
            size={size === 'sm' ? 'xs' : 'sm'} 
            className="button-spinner"
          />
        )}
        {!loading && leftIcon && (
          <span className="button-icon-left">{leftIcon}</span>
        )}
        <span className="button-content">{children}</span>
        {rightIcon && (
          <span className="button-icon-right">{rightIcon}</span>
        )}
      </StyledButton>
    );
  }
);

Button.displayName = 'Button';