## ADDED Requirements

### Requirement: 前端共享层治理
前端共享层（shared-types / shared-components / core-event-bus / shared-services）需从空壳死包状态治理为真正被复用的基础设施，并清理死代码与虚假依赖，解锁 8 个微应用复用。

#### Scenario: 清理死代码与虚假依赖（T11 先行）
- **WHEN** 删除 `masterdata-app` 的 `qualityResultApi` reject 死代码并移除 `bone-shell` 的 `@reduxjs/toolkit`/`react-redux` 虚假依赖
- **THEN** `npm run lint`（eslint . --ext ts,tsx --max-warnings 0）全绿，无未使用依赖报错

#### Scenario: 收敛共享类型
- **WHEN** 将各 app `types/index.ts` 领域类型上提 `shared-types/src`
- **THEN** 各 app 改为 `import type from '@bone/shared-types'`，本地冗余删除，构建通过

#### Scenario: 接入事件总线
- **WHEN** 在 shell 与至少一个子应用通过 `core/event-bus` 广播主题/语言切换
- **THEN** 跨应用事件可达，event-bus 从 0 接入变为有真实链路

#### Scenario: 抽取公共 Vite 配置
- **WHEN** 抽 `createQiankunViteConfig(name, port)` 公共配置
- **THEN** 8 个 app 的 `vite.config.ts` 复用，重复配置消除
