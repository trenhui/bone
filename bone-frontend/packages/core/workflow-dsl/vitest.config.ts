import { defineConfig } from 'vitest/config';

/**
 * 本包的测试配置：**node 环境、无 setupFiles**。
 *
 * <p>根 `vitest.config.ts` 面向 React 组件测试（jsdom + `tests/setup.ts` 里的 window/localStorage mock）。
 * DSL 编译层是纯函数、零 DOM 依赖，套用 jsdom 环境既慢，又会被无关的传递依赖拖垮
 * （实测 jsdom 的 css-color 依赖在本仓库的 lock 下报 `LRUCache is not a constructor`，导致整包测试跑不起来）。
 * 单独一份配置让这层保持「纯 TypeScript、可独立验证」。
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    setupFiles: [],
  },
});
