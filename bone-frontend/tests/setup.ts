// 测试环境设置
import '@testing-library/jest-dom';
import { vi } from 'vitest';

// 模拟全局对象
global.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// 模拟 matchMedia
global.matchMedia = vi.fn().mockImplementation((query) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}));

// 模拟 localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    clear: () => {
      store = {};
    },
    removeItem: (key: string) => {
      delete store[key];
    },
  };
})();

Object.defineProperty(window, 'localStorage', { value: localStorageMock });

// 模拟 fetch
vi.mock('cross-fetch', () => ({
  default: vi.fn(() => Promise.resolve({
    ok: true,
    json: () => Promise.resolve({}),
  })),
}));

// 模拟 qiankun
vi.mock('qiankun', () => ({
  registerMicroApps: vi.fn(),
  start: vi.fn(),
  loadMicroApp: vi.fn(),
  initGlobalState: vi.fn(() => ({
    onGlobalStateChange: vi.fn(),
    setGlobalState: vi.fn(),
    offGlobalStateChange: vi.fn(),
  })),
}));

// 模拟 getComputedStyle
Object.defineProperty(window, 'getComputedStyle', {
  value: vi.fn(() => ({
    getPropertyValue: vi.fn(() => ''),
  })),
});