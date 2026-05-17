# bone-extension-app

扩展管理微应用（Qiankun，端口 **3008**）。

## 开发

```bash
npm install
npm run dev
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `BONE_API_PROXY_TARGET` | `http://localhost:8888` | Vite `/api` 代理（推荐经 **bone-gateway**） |
| 直连 Studio | `http://localhost:8088` | 跳过 Gateway 时使用 |

Shell 主应用加载本微应用时，同样应将 `/api/v1/extension` 代理到 Gateway（见 `bone-shell/vite.config.ts`）。

## 测试

```bash
npm run test          # Vitest 单元测试
npm run e2e:api       # 需 Gateway + Studio 已启动
npm run e2e:ui        # 需 dev 服务 + Playwright chromium
```

契约与详设：`doc/design/modules/5. 扩展管理模块详细设计方案.md`。
