import React from 'react';
import './AppMain.css';

const AppMain: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  return (
    <div className="app-main">
      {children}
    </div>
  );
};

export default AppMain;