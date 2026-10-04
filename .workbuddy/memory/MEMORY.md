# Bone 项目长期要点（跨会话易踩坑，细节见 YYYY-MM-DD.md）

## 一、工具/取证纪律
- BSD/zsh `grep "a\|b"`、`grep -c`、`--include=*.java` 会静默返回 0 且不报错；凡据 grep 的否定结论，必须用 Grep 工具或 `python3 -c "print(s.count(x))"` 复核。
- 管道吃掉退出码：`python x.py | tail` 的 exit 是 tail 的；用 `>f 2>&1; echo $?`。
- 写进文档/ADR 的每个数字必须当场统计（例：OpenAPI 8 份 / 100 path / 142 操作）。
- 改代码脚本只替换目标词并保留字段名/`?`/冒号；Python f-string 把 `None` 渲成字面量（曾 44 文件 332 处 `idNone: string`）。
- 探针必须 `assert old in t` 校验命中；门禁参数各异（`--check`/`--report-only`/无参），引用步骤号前先 `grep -oE '\[[0-9]+/[0-9]+\]' scripts/check.sh`。
- `mvn` 不在 PATH（旧记录的 `~/java/apache-maven-3.8.6` 已不存在）：现用 IDEA 内置 `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`（3.9.11）；JDK 由 `~/.m2/toolchains.xml` 强制 21（`~/Library/Java/JavaVirtualMachines/temurin-21`，不可放 /tmp 会被清理）。MySQL DDL 隐式提交，验证幂等会真实落库须手工 DROP。

## 二、共享工作树 / Git 并发
- AI 禁自行建/切/删分支（AGENTS.md §三），写操作前 `git branch --show-current`。
- 回退自己的改动**只用 `cp` 备份+还原**，绝不 `git checkout`/`stash`（会连带 revert 他人在途改动）。stash drop 后对象仍在：`git fsck --no-reflogs --unreachable` 按 message 找回。
- 双远程（Gitee+GitHub）一次 `git push origin <ref>` 同推；push 的 old..new 与本地 HEAD 不符时 `git ls-remote origin refs/heads/dev`。判归属看 mtime+`git status`，别只看 mtime（时区差 8h 会误读）。

## 三、门禁与基线纪律
- 门禁状态唯一真源 = `doc/architecture/gate-state.json`；改完 `check-ddd-gate-state.py generate` 重渲 + `generate --check` 验幂等（只查表格行、只查"声明 Active 却无载体"，反引号内只放纯路径）。
- `scripts/check.sh`（步骤号漂移，先 grep 核对）与 `ci-check.sh` 独立，CI 都不调 `ci-check.sh`；HC-008 DDL/租户声明/gate-state/架构测试是独立手动门禁。
- 门禁降级路径必须和主路径一样做正反验证（缺 PyYAML 时 exit 2 而非正则误报）。负向验证用真实回归样本（`git show HEAD:<file>`）。
- 基线只可收缩（移除而非 resolved）；实库会漂移于 `bone-init.sql`，改 DDL 须 `SHOW COLUMNS` 对账。
- 门禁红灯落在自己没碰的模块 → 是并发会话在途改动，别跑模块级 `spotless:apply`，只对自己模块 `spotless:check`。
- **门禁全绿 ≠ 仓库全绿**：`check.sh` L28 变更模块检测是 `git diff --cached --name-only --diff-filter=ACM | grep '\.java$'` ⇒ **只认 staged 的 .java**。pom-only 改动、未列入的模块、前端全部是盲区（2026-10-04：`bone-architecture-test` 因父 pom 把 archunit 锁 test 作用域而本模块在 src/main 用它，长期编译不过却从未被门禁编译）。改 pom 后要单独 `-f <pom> compile` 验一次。
- 前端不在 check.sh 21 步内；`npm run lint` 的 `--max-warnings 0` 语义是 0 error + N warning 仍 EXIT=1，且仓库普遍不绿（bone-system-app 自身 1 error + 7 warning）。qiankun 子应用 `build` 产物只有 index.html 是**既有约定**（`public/qiankun-entry.js` 静态拷贝，mount 时才 import 真实入口），别误判为构建失败——先拿同族 app 对照。
- 对外真 SDK 仅两个且健康：`bone-metadata-sdk`、`bone-extension-sdk`（原 `bone-sdk/` 空壳已从根 pom 移除）。

## 四、分层与编码
- `adapter→application→domain←infrastructure`；domain 零框架依赖。**禁 `SELECT ... FOR UPDATE`**（业务/应用层），聚合并发用 SDK `@Version`（ADR-0031）。
- 持久化只用 `bone-metadata-sdk`；HC-006 基线只可收缩，新增绕过走 `Repository.updateByCriteria`。
- 统一异常处理器在 bone-web（Bean 名 `globalExceptionHandler`）；错误码三件套 `{Module}ErrorCodes`+`{Module}Errors`+登记 md，补登状态从代码 `String X="值"`+`Map.entry` 自动取。
- 手改 Java javadoc 断行几乎必败（spotless 填满 100 列）：`mvn -o -q -pl <mod> spotless:apply`。

## 四·五、序列化契约（勿轻动）
- 全局 `Long`→JSON string 唯一落点：`MetadataAutoConfiguration.boneLongToStringCustomizer()`（动机：雪花 ID 超 JS 2^53 被静默截断）。改动 ⇒ L3/L4。

## 五·五、OpenAPI 契约
- `doc/architecture/openapi/` = 8 份 `*-v1.yaml` + 4 份 `components/*.yaml` + `redocly.yaml`。门禁 `check-openapi-contract.py`（标准库+PyYAML 离线；`npx redocly` 仅 CI 各模块 job）只要求 `*-v1.yaml` 有认证。

## 六、租户隔离
- `TableMetadata.isTenantScoped()` 只看是否声明 tenantId；正确做法继承 `TenantAggregateRoot`/`TenantAbstractEntity` 并在工厂真正 `setTenantId`。门禁 `check-tenant-entity-declaration.py`。

## 七、SQL 迁移 / 八、接口契约
- 业务数据走 `scripts/migration/NNNN_*.sql`（备份→Expand→数据→校验→SIGNAL），禁 INSERT IGNORE，幂等连跑 2~3 次，执行 `--default-character-set=utf8mb4`。
- 分页入参三套：`page/size`(iam/generator)、`pageNum/pageSize`(system 等多模块，违 §5)、`cursor/limit`(notification/extension)。`PageResult` 有 3 个 `@Deprecated` getter 致响应同含 list/records。
- 软删判据=实体有无 `@Deleted`（看继承链最终基类），与 DDL 有无 deleted 列无关；extends 聚合根+表带 deleted 列 ⇒ `deleteById` 物理删。

## 九、前端响应解包
- `createApiClient` 拦截器 `(r)=>r.data` → 业务 `await` 到 ApiResponse 本体（axios 类型仍是 AxiosResponse ⇒ type lie）。三硬约束：不读 `.status`、不在 `.data` 上取信封字段、用双泛型 `get<unknown, ApiResponse<T>>()`。

## 十、本地运行（沙箱）
- 沙箱注入 `SERVER__PORT/HOST` → 启 Spring Boot 用 `env -u SERVER__PORT -u SERVER__HOST java -jar ...`。
- 每个 dev server 各一个 `run_in_background` 独立任务；`(cmd &)+sleep` 子进程会被回收（502≠挂）。jar env：`BONE_DB_PASSWORD/ BONE_JWT_SECRET(≥32B)/ BONE_REDIS_*/ SPRING_PROFILES_ACTIVE=dev`。账号 admin/123456、tenant_admin/123456(租户1001)。前端 7 微应用：iam3003/metadata3004/masterdata3005/integration3006/system3007/extension3008/generator3009。macOS 无 `timeout`；`npx @redocly/cli@1 lint` 可联网。

## 三·五、权限码与授权门禁
- **权限码四层真源**：`DefaultPermissionCodes`（admin 回退）/ `TenantAdminBootstrapSupport`（租户管理员白名单）/ `bone-init.sql` 种子 / 前端 `bonePermissionCodes.ts`。改任一 `@PreAuthorize` 必须四处同步，漏一层全员 403。
- `iam_permission` 有 `PRIMARY KEY (id)` 与 `UNIQUE KEY (code, deleted)` ⇒ 种子**重复 id 或重复 code 都会让导入直接失败**。**补齐"漂移"前必须先全表统计**（曾因"Java 侧有、看着像缺种子"而重复登记 `metadata:read/write`，而种子 id 7/8 早已存在）。统计只对 `INSERT INTO iam_permission` → `INSERT INTO iam_role_permission` 之间做（后者是三列，混进去得假重复）。`DefaultPermissionCodes` 用 `List.of` ⇒ 重复元素是**运行期** `IllegalArgumentException`。
- 写端点授权门禁 `check-controller-authorization.py`，基线**已归零**。豁免分**类级**（按文件名跳整文件，慎用）与**方法级** `EXEMPT_METHODS`（键 `文件名#METHOD:路径`），**两类都要有失配判据**——类级判据当场抓到一条一直躺着的失效条目（已删控制器），日后同名文件重建会白继承豁免。基线键用**短键**（含仓库相对目录会因一次目录调整引发数十条假红灯）。
- 恒 501 端点**预挂高危码**而非豁免（`SystemController` 4 个运维端点 → `sys:ops:execute`，只授超管）：实现时若忘加注解就是"任意登录用户重启生产实例"。
- **机器身份数据面**（上报/回调端点）：不能挂用户权限码，但**绝不能裸豁免**。`permitAll` + 无注解 + 真落库 + 关键字段全取自入参 = 匿名可写。做法见 `ReporterTokenFilter`（共享密钥 + 失败关闭 + 断言"filterChain 未被调用"而非仅状态码）。
- 完整规程见用户级技能 `bone-controller-authorization-gate`。
- **安全配置的默认必须是「拒绝」**：机器身份（共享密钥）未配置时若默认放行，"忘记配置"就是一次
  静默降级（只有 WARN 会被忽略）。故 `reporter-auth-required` 默认 `true` → 未配置时返 **503**
  （部署问题）与令牌错的 **401**（身份问题）必须分开；日志级别 WARN→**ERROR**；客户端**失败前置短路**。
  **默认值本身要被单独测试锁住** —— 改默认值时代码能编译、全部业务测试仍能过。
  ⚠️ domain 端口加第二个方法后**不再是函数式接口，所有 lambda 桩会同时编译失败**。

## 十一、静态门禁判据设计
- 每条判据必须能被反例证伪：避免位置窗口法（应逐方法块解析）、剥离文本与值提取用两个文本域、漏类级注解、宽正则误匹配 import、签名跨多行。扫描根覆盖全部业务模块；豁免条目逐条写理由；`--baseline` 须在探针还原后跑。
- **豁免的真正风险不是"放行不该放的"，而是"门禁看不见这个端点"** ⇒ 豁免键失配（端点被删/改名/已加注解）必须显式失败，否则它比缺授权更难发现。
- 文档里提到**已删除的类名**会触发 `check-doc-code-symbols`红灯 ⇒ 写历史事实时用描述性表述（"原健康检查控制器"）。

## 十二、验证途径：优先 Maven；独立 javac 是备选（且 JDK 前提曾被误判）
**JDK 前提已纠正（2026-10-04）**：本机装有 **Temurin 21.0.12.1**，`~/.m2/toolchains.xml` 也已指向它，**Maven 可用**。
若 `mvn` 报「无效的目标发行版：21」/「class file version 65.0」，**真实原因通常是 `~/.zshrc:6` 硬编码
`export JAVA_HOME=.../ms-17.0.16`**（`java`/`javac` 走 `/usr/bin` 仍是 21，故三者不一致）。
**先实测 `java -version` / `javac -version` / `mvn -v` 三者再下结论** ——
「本机只有 JDK 17」这个判断曾让我把ArchUnit 整段红灯误判为环境问题，而它其实是本地配置问题、修正后 4/4 模块 exit 0。
**"环境问题"是能立刻停掉追问且几乎总是错的归因**，写进任何文档前先问"有没有一条命令能证伪它"。
真正需要走独立 `javac` + JUnit Platform Console 的场景是 Maven reactor 被并发会话在途改动阻塞。届时 classpath 有五个坑：
1. 同 artifact 多版本 → 按 `group/artifact` **只取最高版本**（11k → 3.1k jar），否则数十条假编译错误（`javax`/`jakarta` 混用）。
2. 同 **group** 内不同 artifact 也须同版本，否则运行期 `NoSuchMethodError`。
3. `org.springframework:spring:2.5.6.SEC03` 等历史聚合包含旧 `CollectionUtils`，**抢在 spring-core 前加载** ⇒ `NoSuchMethodError`。按 artifact 取最高抓不到它（它是唯一版本），须显式排除 `org.springframework*` 的 `< 6`。**排查最快：探针打印 `Class.forName(X).getProtectionDomain().getCodeSource()` 直接看类从哪加载**。
4. `target/classes` 可能落后源码（并发会话）→ 把该模块源码并入编译单元；也可能 **class 版本高于 JDK** → 从 classpath 剔除该目录。
5. **别加 `-proc:none`**（关掉 Lombok ⇒ `@RequiredArgsConstructor` 不生成构造器 ⇒ 假错误）。用 `javac @argfile`（classpath 过长会 `argument list too long`）。
- JUnit 1.9.x Console 参数**必须 `--select-class=全限定名`**（空格版只打帮助）。断言 `getContentType()` 用 `startsWith`（`setContentType`+`setCharacterEncoding` 会合成 `;charset=UTF-8`）。

## 十三、前端微应用与本地门禁（2026-10-04 补齐交易域时踩到）
- **`scripts/check.sh` 在沙箱 shell 必挂于 `mvn: command not found`**（16 次）→ 报「自检失败（第 1/3 次）」`EXIT=1`。**先加 `PATH=/Users/renhui.trh/java/apache-maven-3.8.6/bin:$PATH` 再跑**（该 mvn 绑 JDK `temurin-21`，与当前产物 class 65.0 一致）。别把 PATH 缺失误判成自己引入的红灯。
- **Vite `strictPort:false` 会静默顺延端口**（配 3010 → 实际 3012），导致 shell entry / 网关 CORS 全指错，现象是「微应用加载不出来却毫无报错」。新建子应用务必 `strictPort:true` 并同步全部引用处。
- **`createQiankunViteConfig` 用 `...config` 顶层浅展开**：传 `{server:{...}}` 会整体替换工厂生成的 `server`（含 proxy）→ 落到默认 5173。要传 `proxyTarget`，**别覆盖 server 段**。
- **日期渲染禁 `toLocaleString()`**（i18n 门禁 `scripts/check-i18n-sync.py`，check.sh [8/22]），用 `@bone/shared-utils` 的 `formatDate()`。**门禁是文本扫描，注释里出现该词也会命中**。
- 交易域微应用 `bone-commerce-app`（**3012**）承载 blueprint 的 OrderController 6 + PaymentController 4 端点；**回调端点故意不做前端「模拟回调」**（HMAC 密钥下发浏览器 = 可伪造任意金额支付成功）。
- 本机 JDK **17 与 21 并存**；并发会话重建的 jar 常是 class 65.0（JDK 21），启动前先辨认版本，否则表现为「服务起不来」。
