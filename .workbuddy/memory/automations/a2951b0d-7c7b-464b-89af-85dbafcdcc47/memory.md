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
