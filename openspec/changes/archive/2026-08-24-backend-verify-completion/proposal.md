# Proposal: 后端真实验证补齐（backend-verify-completion）

## 目标
弥补此前 10 个 Comet change 在 verify 阶段因误判"无 mvn 编译环境"而被**记录接受（未真实编译）**的验证缺口。使用已修复的 Maven 环境（Maven 3.8.6 + JDK 17），对以下 4 个此前缺架构守护测试的模块做真实编译与 ArchUnit 验证，将 verify 状态由"记录接受"升级为"真通过"：

- `bone-framework/bone-datasource`
- `bone-framework/bone-web`
- `bone-engine/bone-metadata-sdk`
- `bone-engine/bone-metadata-engine/bone-metadata-engine-core`

> 说明：这 4 个模块的 `*ArchitectureTest.java` 与 archunit / bone-architecture-test 依赖已在先前会话补齐并 commit 至 master（已 `git ls-files` 确认纳入版本控制）。本 change 不新增测试代码，仅做**真实构建验证**。

## 非目标
- 不修改任何业务代码、API 或架构规则。
- 不处理 bone-file / bone-notification 联调（属独立遗留项，不在本 change 范围）。
- 不对已归档的 10 个 change 做范围外的新功能验证。

## 为什么现在做
- 已确认 Maven 环境可用：`bash -lc 'mvn -pl bone-framework/bone-web -am compile'` 实测 EXIT=0。
- 之前"无 mvn"为误判（新 shell 未 source 配置），现已修正 `.bash_profile`/`.zshrc`，统一 JAVA_HOME→JDK17、M2_HOME→Maven 3.8.6。
- 因此此前所有"记录接受"的 verify 均可被真实验证替代，恢复交付可信度。

## 验收标准
1. 4 个模块均能 `mvn -pl <module> -am test -Dtest=*ArchitectureTest` 真实编译并跑通 ArchUnit（EXIT=0）。
2. 验证结果写入 `verify.md`，并标注"真通过"以区别于历史"记录接受"。
3. 若任一模块真实编译/测试失败，则定位并修复（仅在测试或 pom 依赖层面，不触及业务代码），直至全部真通过。

## 范围边界
- 仅限上述 4 个模块及其必要的 `-am` 上游依赖。
- 不触发整库全量构建（除非单模块 `-am` 需要）。
