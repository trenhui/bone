# 验证报告 — D-06 存量采购付款逃逸处理器硬隔离

- change: `procurement-legacy-payment-hard-isolation`
- 验证日期: 2026-11-20
- 工作区: `/Users/renhui.trh/code/bone`（dev，current 隔离）
- base-ref: `2f70dbfb595aa5dc4aa6ad76df6e8c57dc9f4eb1`

## 1. 任务完成

tasks.md 9/9 全部勾选（`comet state tasks` = 9/9 complete）。

## 2. 构建与测试（真实执行）

| 检查 | 命令 | 结果 |
|------|------|------|
| 模块全量构建 | `mvn -o -f bone-apps/bone-app-purchase/pom.xml clean test`（JDK21 + Maven 3.6.3） | **BUILD SUCCESS，25 例全绿**（failures=0/errors=0） |
| spotless | 绑定 validate，`clean test` 内含 `spotless:check` | 21 文件全 clean |
| 存量逻辑不回归 | `PurchaseOrderPayHandlerTest` 4 例未改 | 4/4 通过 |
| 新增判别力 | 装配 3 + 属性 2 + 日志 2 = 7 例 | 7/7 通过，三态互斥 |

- RED-GREEN 证据：先改装配测试期望"缺省禁用"→ 2 例 RED（`shouldNotRegisterPayHandler_whenSwitchAbsent/False` 失败，`doesNotHaveBean` 不满足）；实现条件装配 + REGEX 排除后转 GREEN。未回退代码伪造 RED。
- 环境问题修复记录：首次 `guard build --apply` 的 `clean test` 因 `.m2` 旧 `bone-metadata-server` jar 缺 `RuntimeActionContext.OUTCOME_PLATFORM_STATUS/PLATFORM_STATUS_KEEP` 转红（增量编译此前掩盖）；离线重装该模块源码版本后 `clean test` 全绿。此为环境依赖问题，非本 change 源码缺陷。

## 3. 规格一致性

- `comet classic openspec -- validate procurement-legacy-payment-hard-isolation`：**valid**。
- 逐条核对 spec 4 条需求与实现 file:line：
  - 缺省禁用 → `PayHandlerConditionalRegistration.java:19-22`（`matchIfMissing=false`）+ `PurchaseComponentScanConfiguration.java:19-23`（REGEX 排除）+ 装配测试 `:29/:40`。
  - 显式 true 才注册且仅演示 → `:19-24` + 处理器逻辑未改（note 占位）。
  - 启动期可观察 → `LegacyPaymentIsolationReporter.java:26-32` + 装配测试 `:31`（Reporter 在装配链上）。
  - 不改存量业务逻辑 → `PurchaseOrderPayHandler.java` 零 diff，4 例守卫测试未改。

## 4. 门禁回归（11 项全绿）

align(10/10) / align --self-test(146/146) / align --manifest / asyncapi --check(29 事件) / evalset(PASS) / evalset --self-test(69/69) / frontmatter(33 份) / frontmatter --self-test(7/7) / ddl-doc-sync(192 表) / ddl-duplicate-column / ddl-required-columns；另 check-sql-template(72 模板 0 违规) / check-sql-entity-column-coverage(G42 无缺口)。

## 5. 独立审查（review_mode: standard）

独立审查（只读 diff + file:line）结论：无 CRITICAL。
- IMPORTANT（平台侧同名 bean 副本靠 `@ConditionalOnMissingClass` 让位，"唯一注册入口"措辞过绝对）→ 已在 `design.md` D1 补边界澄清：当前 bone-boot 组合根形态下 fail-closed 成立，standalone metadata-server 不加载采购业务链路，本 change 不改该既有行为。
- SUGGESTION 1（日志测试假绿盲区）→ 已在装配测试缺省态补 `hasSingleBean(LegacyPaymentIsolationReporter.class)`。
- SUGGESTION 3（design 称 @Component 实为 @Bean）→ 已更正 design D2 措辞。
- SUGGESTION 2（tasks 未勾选）→ 9/9 已勾选。

## 6. 红线核对

- 未改 `ci.yml` / `check.sh` / `FULL_GATE_KEYS` / `bone-init.sql` / 权限码 / evalset（非 L3）。
- 未改存量 `PurchaseOrderPayHandler.java` 业务逻辑（基线"退役前不得删改存量类"）。
- DECISION-REGISTER D-06 更新附真实 file:line，owner 如实写"未指派（用户授权本轮落地，未伪造签署）"，数字"25 例/存量 4 例"经 surefire 报告核实。
- 未动并行会话目录 `openspec/changes/synapclaim-architecture-convergence/`。

## 7. 验证结论

**PASS** — 全部任务完成、构建测试全绿、规格一致、门禁无回归、独立审查无阻断项、红线未触碰。无未接受偏差；无 WARNING/SUGGESTION 遗留待决。
