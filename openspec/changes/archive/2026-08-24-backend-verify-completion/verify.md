# Verify: 后端真实验证补齐

> 状态：**已完成** — 4/4 模块真实编译 + ArchUnit 通过（真通过）。

## 验证命令（统一）
```bash
bash -lc 'cd /Users/renhui.trh/wps/bone && mvn -q -pl <module> -am test -Dtest=*ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false'
```
> 注：`-Dsurefire.failIfNoSpecifiedTests=false` 必要，因为 `-am` 会构建上游依赖 `bone-architecture-test`（无 ArchitectureTest），否则 surefire 会因找不到匹配测试而 FAIL，阻断依赖链。

## 模块结果

### T1 bone-framework/bone-datasource
- 输出摘要：SLF4J NOP logger 警告（无害），无编译/测试错误。
- 结果：**真通过**（EXIT=0）。ArchUnit 规则含 `allowEmptyShould(true)`，framework 库无匹配分层类时统一空过，符合预期。

### T2 bone-framework/bone-web
- 输出摘要：ArchUnit PluginLoader `Detected Java version 17.0.16`，测试执行无违规。
- 结果：**真通过**（EXIT=0）。

### T3 bone-engine/bone-metadata-sdk
- 输出摘要：ArchUnit PluginLoader `Detected Java version 17.0.16`，测试执行无违规。
- 结果：**真通过**（EXIT=0）。

### T4 bone-engine/bone-metadata-engine/bone-metadata-engine-core
- 输出摘要：ArchUnit PluginLoader `Detected Java version 17.0.16`，测试执行无违规。
- 结果：**真通过**（EXIT=0）。

## 结论
4/4 模块真实编译 + ArchUnit 通过。此前 10 个 Comet change 在 verify 阶段因误判"无 mvn"而"记录接受"的架构守护缺口，已由本 change 的真实 Maven 构建弥补为**真通过**。

## 环境修正回顾（本 change 前置）
- Maven 3.8.6 + JDK 17 环境在 `bash -lc` 下可用。
- `.bash_profile`/`.zshrc` 已统一 JAVA_HOME→JDK17、M2_HOME→Maven 3.8.6。
- 无需改动任何业务代码或测试代码；4 个 `*ArchitectureTest.java` 早已随之前提交进 master。
