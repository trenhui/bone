# bone-architecture-test

Bone DDD 共享 ArchUnit 规则（`BoneDddArchRules`），真源见 `doc/architecture/Bone-DDD-最终实践方案.md` §21。

## 用法

1. 模块 `pom.xml` 增加 test 依赖：`bone-architecture-test`、`archunit-junit5`。
2. 新增 `ArchitectureTest`，对存量违规使用 `FreezingArchRule.freeze(...)`。
3. **首次**生成基线（模块根目录 `archunit_store/`）：

```bash
mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true
```

4. 将 `archunit_store/` 提交入库；日常 CI 勿开启 `allowStoreCreation`。
