import React from 'react';

export const Card: React.FC<React.PropsWithChildren> = ({ children }) => (
  <div className="bone-card">{children}</div>
);
