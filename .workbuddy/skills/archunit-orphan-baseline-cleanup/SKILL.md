---
name: archunit-orphan-baseline-cleanup
description: 清理 ArchUnit FreezingArchRule 的孤儿冻结基线（archunit_store 里"规则不再消费"的死 UUID 文件）。触发：发现 stored.rules 引用的基线对应规则在某模块未冻结(plain)或描述过期(E-9.3/旧包名)；准备收缩/删除架构规则后残留的空基线；门禁"假绿"排查时怀疑基线失效。提供逐模块判定真源 + 实证删除验证流程。
---

# ArchUnit 孤儿冻结基线清理

Bone 用 `FreezingArchRule` 冻结架构违规。基线落在各模块 `archunit_store/`（UUID 命名文件），映射关系在 `archunit_store/stored.rules`（`规则文案=UUID`，properties 格式，空格转义为 `\ `）。本 skill 讲如何安全识别并删除"无人消费"的孤儿基线。

## 冻结机制（判孤儿的真源）

- `FreezingArchRule.freeze(BoneDddArchRules.xxx())` 按**规则文案**（ArchUnit 生成的 description）在 `stored.rules` 查 UUID，再读 `archunit_store/<UUID>` 基线。
- **只有被 `FreezingArchRule.freeze(...)` 包裹的规则才消费基线**；plain（`ArchRule`，未 freeze）规则永不读基线 → 其基线必为孤儿。
- 故孤儿判定：**某 stored.rules 条目，其文案不匹配该模块任何已冻结规则的当前文案** ⟹ 孤儿。两类典型：
  1. **规则在该模块未冻结（plain）**：如 `domainRepositoriesShouldOnlyDeclareWhitelistedMethods()`、`noBoneCoreUseCaseApiDependency()`、`applicationMustNotDependOnInfrastructure()` 在多数模块是 plain。
  2. **文案过期**：规则改过 `.because()` 或包谓词，旧文案的基线永不命中。如 `E-9.3` 版读侧 DSL 基线（现规则产 `E-4.2`）、指向已删除 `..application.command.handler..` 包的旧 P0-6 基线。

## ⚠ 关键陷阱

- **同规则在不同模块冻状态不同** → 必须**逐模块**对照冻结集，不能一刀切。例：`readSideDslOnlyInQueryLayer()` 在 bone-integration 被冻结（E-4.2 基线活），在 bone-iam 未冻结（E-4.2/E-9.3 基线均孤儿）。
- **收口放宽为 `..adapter..` 后，旧 `..adapter..controller..` 变体全部失效**：2026-09-19/21 把 `adapterControllersMustNotDependOn*(GodObjects|DomainRepository|DomainService)` 谓词由 `..adapter..controller..` 放宽为 `..adapter..`（domain.repository 另加 `resideOutsideOfPackage("..adapter.schedule..")`），旧 `..adapter..controller..` 变体基线（god-object 两变体、domain.repository、domain.service）全部成孤儿；live 是 `..adapter..` 通用变体。P0-6 `commandHandlersMustNotUseQueryBuilder` 由 `..application.command.handler..` 收口到 `..application..` 同理（旧 `..command.handler..` 基线失效，live 为 `..application..` 变体）。
- **必须重读每个 ArchitectureTest 重新判定，勿信上一轮的静态清单**：同仓多次收口后，上轮标记的"孤儿"可能因描述漂移而误判（如 masterdata 的 `e8602e39` 实为 live 的 `..adapter.. and schedule` 通用变体，并非 stale controller 变体；又如上轮把 extension-studio 的 `6ca2a113` 误标为 stale，它其实是 live 的 domain.repository 通用变体）。逐模块把 UUID 文件与冻结规则文案 1:1 对齐才是真源。
- **已有 `scripts/clean-archunit-store-orphans.py` 只按固定 marker**（`CommandHandler`/`QueryHandler`/`handle/execute`/旧 adapter 标记）删，**覆盖不到** E-9.3 / plain 规则类孤儿。勿完全依赖它。
- **缺失基线不是问题**：`stored.rules` 引用但磁盘无文件的 UUID（"缺失基线"），FreezingArchRule 跑时会重建空基线；删其 stored.rules 行即可，无需 `git rm` 该文件。
- **全反应堆 Spotless 门禁**：提交钩子 `[1/5]` 跑全仓格式，其他模块若有格式脏会挡提交。本任务只动 `archunit_store`（非 .java），Spotless 不受影响；但若顺带改了 .java 需先 `mvn spotless:apply` 对应模块。

## 流程

1. **枚举**：`git ls-files | grep archunit_store` 列出所有 baseline 文件 + `stored.rules`。确认全仓无 `storedAt(...)` 引用（Bone 的 freeze 定位靠 `stored.rules`，不靠 `storedAt`）。
2. **读各模块 ArchitectureTest**：`grep -nE 'FreezingArchRule\.freeze\(' <module>/src/test/**/ArchitectureTest.java` 取**已冻结规则集**。注意多行 `FreezingArchRule.freeze(\n ... )` 也会被冻结，需读全文确认（单行 grep 会漏）。
3. **逐模块映射 stored.rules**：每条 `规则文案=UUID`，判断对应规则是否在该模块冻结集内。不在 → 候选孤儿。
4. **备份**：`cp -r <module>/archunit_store /tmp/<module>-archunit_store.bak`（跑测试前必做，用于 `diff -rq` 自证无副作用）。
5. **删除候选**：`perl -i -ne 'print unless /<UUID>/' <module>/archunit_store/stored.rules` 删映射行；`git rm <module>/archunit_store/<UUID>`（文件已在磁盘才需；缺失则跳过）。
6. **实证验证（核心安全网）**：逐个模块跑 `mvn -o -pl <module> test -Dtest=ArchitectureTest -Dspotless.check.skip=true`。**全绿 = 候选确为孤儿**。若某基线被误删（它其实被冻结规则消费），测试会报"新违规" → 从备份 `git checkout --` 还原该文件 + stored.rules 行，重跑确认。
7. **副作用核对**：`git status` 应只显示删除 + stored.rules 修改；`diff -rq <live> <backup>` 应只差已删 UUID（无新建文件、无 stored.rules 时间戳之外的改写）。
8. **提交**：建议每模块一个 commit（bisect 友好），message 注明删了几个、对应规则为何不消费、ArchTest 结果。再 `git push origin HEAD`（双远程 Gitee+GitHub）。

## 验证命令速查

```bash
# 某模块冻结规则集
git grep -nE 'FreezingArchRule\.freeze\(' -- <module>/src/test/**/*.java
# 删映射行
perl -i -ne 'print unless /<UUID>/' <module>/archunit_store/stored.rules
# 跑门禁（跳过 spotless 省时）
mvn -o -pl <module> test -Dtest=ArchitectureTest -Dspotless.check.skip=true
# 副作用核对
diff -rq <module>/archunit_store /tmp/<module>-archunit_store.bak | grep -v stored.rules
```

## 已落地实例（截至 2026-09-22，全仓扫完）

全仓带 `archunit_store` 的模块共 8 个：bone-iam、studio-generator、bone-integration、bone-system、notification、bone-extension-studio、bone-metadata-server、bone-masterdata。

- **bone-iam**：11 个孤儿（commit `d0f68d12`）。早期 `dbb11a26`(E-4.2)、`720d8a73`(E-9.3) 规则未冻结；`d927f744` 仓储白名单 plain；`72ba633f` 旧 `..command.handler..` P0-6；其余为 `..adapter..controller..` 收口失效变体。`ArchTest 26/0`，幂等。
- **studio-generator(4) + bone-integration(5) + bone-system(3) = 12 个**（Task #6）：规则 plain 或 E-9.3/旧 command.handler 文案过期，各自 ArchTest 全绿。
- **bone-extension-studio**：7 个孤儿（commit `4a9f99e7`）。`bc3784d9`(app→infra plain)、`e002e71f`(白名单 plain)、`e339f25c`(usecase plain)，加 4 个 `..adapter..controller..` 收口失效变体（`8d225da0` domain.service、`29fefa13`+`01462c99` god-object 两变体、`4357348f` domain.repository）。**注意**：`6ca2a113` 是 live 的 `..adapter.. and schedule` domain.repository，勿删。保留 8 个 live。ArchTest 19/0，幂等。
- **bone-metadata-server**：8 个孤儿（commit `28688372`）。`bf07237b` 旧 `..command.handler..` P0-6（live `661d2a7a` `..application..`）、`69c5a658`/`e87dd59a`/`7a1f8207` plain，加 4 个 `..adapter..controller..` 收口失效变体（`9f588ddd` domain.service、`6a693463`+`a088d45f` god-object 两变体、`70a06ce8` domain.repository）。保留 9 个 live。ArchTest 全绿，幂等。
- **bone-masterdata**：**0 孤儿**（commit 无）。9 个 UUID 与 9 个冻结规则 1:1 对齐；`e8602e39` 是 live 的 `..adapter.. and schedule` domain.repository，非 stale。
- **bone-notification**：**0 孤儿**（早前扫描，5 stored = 5 frozen）。
- **bone-blueprint**：无 Java `archunit_store`（前端/蓝图模块，无 ArchUnit 门禁）。

**累计**：4 模块清理（iam 11 + ext-studio 7 + metadata-server 8 + 早期 12 = 38 个孤儿），4 模块确认 0 孤儿。全仓 ArchUnit 孤儿基线清理收口。
