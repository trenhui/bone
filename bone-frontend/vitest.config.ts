// Vitest 配置文件
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
  // 允许测试读取仓库根目录下的设计样例（doc/design/lowcode 在 bone-frontend 之外，
  // 默认 fs allowlist 会拦截，导致 uiSchemaScenarios 读取样例 JSON 报 ENOENT）。
  server: { fs: { allow: [resolve(__dirname, '..')] } },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@bone/ui': resolve(__dirname, 'packages/ui'),
      '@bone/core': resolve(__dirname, 'packages/core'),
      '@bone/shared-components': resolve(__dirname, 'packages/shared-components'),
      '@bone/shared-services': resolve(__dirname, 'packages/shared-services'),
      '@bone/shared-types': resolve(__dirname, 'packages/shared-types'),
      '@bone/shared-utils': resolve(__dirname, 'packages/shared-utils'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './tests/setup.ts',
    coverage: {
      provider: 'v8',
      include: ['**/src/**/*.{ts,tsx}'],
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/build/**',
        '**/tests/**',
        '**/*.d.ts',
      ],
      thresholds: {
        lines: 70,
        functions: 70,
        branches: 60,
        statements: 70,
      },
    },
  },
});