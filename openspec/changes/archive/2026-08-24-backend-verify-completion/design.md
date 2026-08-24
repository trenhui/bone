# Design: 后端真实验证补齐

## 背景
此前 10 个 Comet change（iam-org-menu-baseline、gateway-auth-ratelimit、enhance-modules、masterdata-standard-lineage、integration-connectors-camel、system-dict-scheduler 等）在 verify 阶段，由于误判本地无 Maven，ArchUnit/spotless 自检均以"记录接受"方式跳过真实编译。现 Maven 环境已修复，可补做真实验证。

## 环境前提（已修复）
- Maven：`/Users/renhui.trh/java/apache-maven-3.8.6`，通过 `M2_HOME` + PATH 暴露。
- JDK：`/Users/renhui.trh/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home`（JAVA_HOME）。
- **调用约定**：必须用 `bash -lc 'mvn ...'`（登录 shell 才会 source `.bash_profile`/`.zshrc`）；裸 `execute_command` 的非登录 bash 默认 PATH 无 mvn。

## 验证策略
对每个目标模块执行：
```
bash -lc 'cd /Users/renhui.trh/wps/bone && mvn -q -pl <module> -am test -Dtest=*ArchitectureTest'
```
- `-am`：同时构建上游依赖模块（如 bone-architecture-test、bone-core 等）。
- `-Dtest=*ArchitectureTest`：仅跑架构守护测试，快速验证分层约束，避免触发整库业务测试耗时。
- 若某模块 ArchUnit 因 `allowEmptyShould(true)` 规则无匹配类而"空过"，属预期（framework/SDK 库无业务分层），仍记为通过。

## 失败处理
若真实编译/测试失败：
1. 阅读失败栈，区分是"依赖缺失"（pom 缺 bone-architecture-test/archunit）还是"架构违规"（业务代码违反分层）。
2. 仅允许在 **pom 依赖层面** 或 **测试 allowEmptyShould 调整** 修复；不得为通过测试而改动业务分层结构。
3. 修复后重跑直至 EXIT=0。

## 不涉及 Spec Delta
本 change 不产生新能力、不改 API/行为，故无 `specs/` delta。验证结论记录于 `verify.md`。

## 产物
- `verify.md`：4 个模块的编译+测试输出摘要与最终状态（真通过 / 修复后通过）。
- 可选：git 提交仅当发生 pom 修复时产生；纯验证无代码改动则不产生新 commit。
