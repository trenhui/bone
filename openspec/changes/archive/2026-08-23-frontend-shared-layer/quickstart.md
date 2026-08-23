# Quickstart: 前端共享层治理（含 T11 先行）

> **T11 先行**：先清死代码/虚假依赖，最小风险。

## T11 快速执行
```bash
# 1. 删 masterdata-app qualityResultApi reject
# 2. 移除 bone-shell package.json 的 @reduxjs/toolkit/react-redux
cd bone-frontend
npm run lint   # eslint . --ext ts,tsx --max-warnings 0 应全绿
```

## 共享类型收敛
```bash
# 将各 app types/index.ts 上提 packages/shared-types/src
# app 改为 import type from '@bone/shared-types'
```

## 关键文件
- 死代码：`bone-frontend/apps/bone-masterdata-app/src/api/qualityApi.ts`
- 虚假依赖：`bone-frontend/apps/bone-shell/package.json`
