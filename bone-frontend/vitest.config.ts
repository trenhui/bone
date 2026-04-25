// Vitest 配置文件
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react()],
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