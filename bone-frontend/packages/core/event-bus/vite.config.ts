import { defineConfig } from 'vite';

// 2026-10-26 联调修复：原配置挂 `vite-plugin-dts@3`，该版本在 ESM 下会去解析
// `entities/lib/decode.js`，而 hoist 到根 node_modules 的 entities 未在 exports 暴露该子路径，
// 直接 ERR_PACKAGE_PATH_NOT_EXPORTED ⇒ `npm run build`（进而全仓 typecheck）在全新 checkout 上必失败。
// 声明文件已由 package.json 的 `tsc && vite build` 中前一步 tsc 产出（dist/index.d.ts），
// dts 插件属重复能力，去掉不影响产物契约（types/main/module 三个入口均由 tsc + vite 覆盖）。
export default defineConfig({
  build: {
    lib: {
      entry: 'src/index.ts',
      name: '@bone/core/event-bus',
      formats: ['es', 'cjs', 'umd'],
      fileName: (format) => `index.${format}.js`,
    },
    rollupOptions: {
      external: [],
      output: {
        globals: {},
      },
    },
  },
});