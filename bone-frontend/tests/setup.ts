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

// 本文件要能在 jsdom 与 node 两种环境里加载：用例可以按文件声明 `// @vitest-environment node`
// （例如纯服务层契约测试，不需要 DOM）。node 环境下没有 window，直接 defineProperty 会抛
// "ReferenceError: window is not defined"，把整个测试文件挡掉，故按环境分流。
if (typeof window === 'undefined') {
  (globalThis as unknown as Record<string, unknown>).localStorage = localStorageMock;
} else {
  Object.defineProperty(window, 'localStorage', { value: localStorageMock });
}

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

// 模拟 getComputedStyle（仅 DOM 环境需要）
if (typeof window !== 'undefined') {
  Object.defineProperty(window, 'getComputedStyle', {
    value: vi.fn(() => ({
      getPropertyValue: vi.fn(() => ''),
    })),
  });
}