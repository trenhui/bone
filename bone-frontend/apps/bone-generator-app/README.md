# bone-generator-app

Studio Generator 微应用（Qiankun，端口 **3009**）。

## 开发

```bash
npm install
npm run dev
```

| 变量 | 默认 | 说明 |
|------|------|------|
| `BONE_API_PROXY_TARGET` | `http://localhost:8888` | Vite `/api` 代理（推荐 **bone-gateway**） |
| 直连 generator | `http://localhost:8085` | 跳过 Gateway |

后端模块：`bone-engine/studio-generator`。详设：`doc/design/modules/8.Studio Generator 详细设计方案.md`。

## 测试

```bash
npm run typecheck
npm run e2e:api    # 需 Gateway + studio-generator
npm run e2e:ui     # 需 dev + Playwright
```
