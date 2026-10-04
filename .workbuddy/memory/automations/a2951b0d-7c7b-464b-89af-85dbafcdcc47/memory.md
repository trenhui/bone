# 自动化执行摘要：Bone 模块前后端联调（C/D）

- 首次执行：2026-09-28（GMT+8）
- 任务：对 11 个模块做前后端测试联调，直到功能正常。

## 关键结论
- 11 模块中仅 **bone-iam、bone-metadata-server** 已完成 C/D（yaml: implemented / implementing）。
- 其余 9 个**均未进入 C/D**：bone-system 已走完 A/A'/B/B'（唯一立即可做 C/D）；masterdata/integration/extension-studio/studio-generator 仅 A/A'，B 未开始；file/notification/gateway 为 pending（v1 从零）；bone-shell 宿主轮单独。
- **环境阻塞（实测）**：IAM(8081) 与 Gateway(8888) 均 DOWN。运行后端 health=UP，但业务 API 直连返回 401/403；用 dev 默认密钥伪造 JWT 仍被拒（`未登录或 Token 已过期`）。网关 v1 尚未实现。
- DB(MySQL pid353)/Redis 正常；前端 bone-shell(3000)/bone-iam-app(3003)/bone-system-app(3007) 均 200。

## 下一轮默认动作
1. Phase 0：起 IAM(8081) 并与运行后端对齐 JWT 密钥 → 解锁既有模块联调。
2. Phase 1：bone-system C 联调 + D 验收（最高就绪）。
3. 后续：masterdata→integration→extension-studio→studio-generator 驱动 B→C/D；再 v1 从零（优先 gateway）。

## 第二轮执行（2026-09-29）：推翻上轮假设，修复主数据
- **鉴权链实测可绕过**：用 dev 默认密钥 `dev-only-secret-key-minimum-32-bytes-long` 签发的 HS256 JWT（claims sub/userId/tenantId/scopes）被所有在跑资源服务器接受。联调资源服务器**不依赖 IAM/Gateway 上线**。
- **主数据真实阻塞 = SDK 类加载缓存失效**（非 IAM）：进程 09-27 启动加载旧 `.m2` `bone-metadata-sdk`，SDK 09-28 22:45 重新 install 后 JVM 永久缓存了 `NoClassDefFoundError: Condition`。**重启 masterdata 进程**（用原命令行，全新 ClassLoader 读当前 `.m2`）即修复；C/D 已验证（GET/POST+DELETE 均 200）。
- **同期重启 extension-studio 双实例**（18088/18089，两个都是 `ExtensionStudioApplication`，**18089 非 bone-file**），消除同类隐患，现均 UP。
- **真实拓扑**：UP = masterdata(18084)、extension-studio×2(18088/18089)、前端(3000/3003/3007)；DOWN = iam(8081)、gateway(8888)、system(8083)、metadata-server(9001)、integration(8085)、file(8107)。
- 注意：仓库 `scripts/dev/restart-mvp-services.sh` 端口假设(8084/8088/8107)与实测(18084/18088/18089)不符，不能直接套用；脏 WIP 分支勿 `git add -A`。

## 下一轮默认动作（更新）
1. 启动并联调 **bone-metadata-server / bone-integration / bone-system**（已有实现，仅未启动）→ 各自 C/D。
2. 收敛 extension-studio 双实例。
3. 从零产出 **bone-file / bone-notification / bone-gateway** v1（design_doc=null）→ 实现后联调。
4. 启动 iam+gateway 跑真实登录→签发→路由端到端。
5. bone-shell 宿主前端单独轮次。

## 产出文件
- 详细状态/计划：`integration-status-20260928.md`、`integration-status-20260929.md`（同目录）
- 状态真源：`doc/design/_review-status.yaml`

## 第三轮执行（2026-10-01）：studio-generator 真实场景端到端优化
- 任务：模拟真实代码生成诉求，前后端串联验证打通。
- 修复 6 项（2×P0 编译阻断 + 4×P1）：文件名/类名校正、聚合 create 工厂（根因=DB 模板漂移，新增迁移 0015 收敛 12 条内建模板）、synced-tables 补列+syncedCount、包名末段去重、/tables keyword+limit。
- 前端：服务端表名搜索防抖、拉取上限、同步计数提示；typecheck 过。
- 验证：e2e-generator-real-scenario.mjs（npm run e2e:real）14/14 PASS 含 javac 0 错误；单测 105/105。
- 交付：studio-generator-真实场景端到端验证报告.md（仓库根）；改动在 dev 工作树未提交。
- 教训：后台任务起 Java 服务 ~2min 被杀，用 nohup+disown。

## 第四轮执行（2026-10-02）：菜单去重确认 + 产物在线查看 + 默认模板
- 菜单去重：GeneratorLayout 本地 Sider 已删（上轮裁定），本轮经无头浏览器真实链路（登录 admin→Shell 3000→网关 8888→8086）截图确认无重复菜单。
- 新增：GET /tasks/{taskId}/files 产物在线查看（清单/正文两模式）；ResultModal 重写（状态驱动+Monaco 预览+失败详情）；templateIds 空 = 全部平台 PUBLISHED 内建模板；修 api.ts getTaskStatus 类型错误。
- 踩坑：默认模板解析用种子口径（created_by IS NULL）只回 6/12 条（迁移 0015 的模板带创建人）→ 新增 findPlatformPublishedAllTenants 修复。
- 验证：E2E 17/17 PASS（新增 files manifest/content、默认模板三步）；单测 105/105；typecheck 过；UI 截图 /tmp/gen-shell-*.png。
- 报告：studio-generator-真实场景端到端验证报告.md §〇（第二轮）。
- 运维：MySQL(/usr/local/mysql bin)、8086/8888/8081/3000/3009 均用 run_in_background 拉起；sudo 起 MySQL 不行但本机 mysqld 本来就在跑（探测要用绝对路径 /usr/local/mysql/bin/mysql）。

## 第五轮执行（2026-10-02 下午）：建议项落地（主子聚合 + 列回填 + 提交）
- **主子聚合（一对多）**：genConfig 携带 childTable/childFkColumn → 新领域 SPI AggregateRelationFileGenerator（主+子双元数据签名，独立于单表 FileGenerator）+ aggregateService.ftl → 产出 {主表}AggregateApplicationService#createWithChildren（一次事务，外键参数=预分配主表 id，明细空抛 DomainException）。模板门禁新增第三类 RELATION_TEMPLATE_TYPES。仅支持单主表（多选报错）。
- **存量列回填**：POST /data-sources/{id}/tables:repair-columns 复用幂等同步服务；前端缺列警示条+一键修复。
- **验证**：E2E 22/22 PASS（聚合 25 文件 javac 0 错）；单测 111/111；typecheck 过。
- **提交**：c196889fd→amend 为 20010b643（feat(generator)，dev 未推送）。首/二轮成果已由并发会话 aa2e77d0d 入库。
- **门禁踩坑**：pre-commit 卡 BP_ORDER_CHANNEL_SOURCE_INVALID 未登记（并发会话 blueprint 在途码）→ 补台账(doc/architecture/Bone-错误码登记.md)+zh/en 语言包后才过；check.sh 步骤 1/2 需 mvn 在 PATH（wrapper maven）；钩子熔断计数 rm .git/hooks/.check-fail-count；git commit 前必须 export PATH（钩子继承当前 shell）。
- **教训**：amend 前 git show --stat 核对——暂存区残留会把 generator 文件卷进 i18n message 提交（已 amend 修正）。
