import React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'default';
}

export const Button: React.FC<ButtonProps> = ({ children, ...props }) => (
  <button type="button" {...props}>
    {children}
  </button>
);
