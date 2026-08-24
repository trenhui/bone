# Tasks: 后端真实验证补齐

## 1. 环境确认
- [x] 确认 Maven 3.8.6 + JDK 17 在 `bash -lc` 下可用（已实测 bone-web compile EXIT=0）。
- [x] 确认 4 个模块的 `*ArchitectureTest.java` 与 archunit/bone-architecture-test 依赖已存在并纳入 git。

## 2. 真实编译 + ArchUnit 验证（核心）
- [x] T1: `bone-framework/bone-datasource` 真实 `mvn -pl ... -am test -Dtest=*ArchitectureTest` 通过（EXIT=0）。
- [x] T2: `bone-framework/bone-web` 真实编译 + ArchUnit 通过（EXIT=0）。
- [x] T3: `bone-engine/bone-metadata-sdk` 真实编译 + ArchUnit 通过（EXIT=0）。
- [x] T4: `bone-engine/bone-metadata-engine/bone-metadata-engine-core` 真实编译 + ArchUnit 通过（EXIT=0）。

## 3. 失败修复（仅当需要时）
- [x] F1: 未触发（4 模块依赖与测试均已就绪）。
- [x] F2: 未触发（无架构违规）。

## 4. 验证报告
- [x] V1: 将 4 模块真实编译/测试结果写入 `verify.md`，标注"真通过"。
- [x] V2: 更新 MEMORY，记录"此前 10 个 change 的 verify 已由记录接受升级为真通过"（已在 MEMORY.md 后端 ArchUnit verify 条目写入）。

## 依赖关系
- T1–T4 相互独立，可并行执行。
- F* 仅在对应 T* 失败时触发。
- V1 依赖全部 T* 完成。
