import React from 'react';
import './App.css';

const App: React.FC = () => {
  return (
    <div className="app">
      <header className="app-header">
        <h1>Bone Frontend Main Application</h1>
      </header>
      <main className="app-content">
        <p>Main application is running successfully!</p>
        <p>This is the shell application for the micro-frontend architecture.</p>
      </main>
    </div>
  );
};

export default App;