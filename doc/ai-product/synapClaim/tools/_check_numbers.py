#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""数值/口径回归机检（防 2026-10-23 已修复问题复发）。

_check_contract_drift.py 管契约面（规则 ID/枚举/错误码），本脚本管**散文层的
数值与口径断言**：FORBIDDEN = 已定性为错误、不应再出现的表述；REQUIRED = 已定型
的关键口径，缺失即回退。任一失败非零退出。
"""
import io, os, re, sys, glob

import _manifest as M

# 活跃文档禁止再写「35 / 36 张表」这类硬编码 DDL 表数。
# 2026-11-07 一次性在 4 类载体发现 6 个不同数字：架构图、设计正文、治理规约、
# manifest 摘要 + 由它派生的 AI-INDEX。根因是「表数」被当成可复制口径散落；
# D7 已改为集合对账，治理文件一律只写「§9.5 DDL 集合」。本判据防止回潮。
DDL_COUNT_PAT = re.compile(r'(?:35|36)\s*(?:张|个)?(?:理赔)?表|建表数\s*=\s*36|DDL\s+36')

# 以脚本所在目录为基准，而不是 CWD：此前 BASE='.' 时从仓库根运行会 glob 到 0 个文件，
# 于是 FORBIDDEN 恒过（假绿）、REQUIRED 恒红——运行位置不同结论不同，最不可信的一种。
BASE = M.ROOT

# 历史键（landing/prd/ai/fde/mvp）→ 路径，来自 manifest 的 checkKeys。
# 键名保持不变，是为了让下面 100+ 条 FORBIDDEN/REQUIRED 断言不必改动（它们按语义分组）。
MAIN = M.check_keys()

# 原本扫描「根目录 *.md」；P10 分层后根目录只剩 README，若仍按根目录扫描，
# 这三条 FORBIDDEN 会退化为只检查 README —— 覆盖率静默归零。
# 因此改为扫描「原先位于根目录的那些层」：L0 导航 + L1 战略 + L2 需求 + L3 契约 + L5 实施。
# L4 于 2026-11-07 加入：`AI技术方案` 由 L3 移入 L4（内容 67% 是设计原则与评测，不是契约），
# 它是 checkKeys 里的 'ai' 键所指向的文档，若不加 L4，按层扫描的那一组断言会静默丢覆盖——
# 门禁照跑、校验的却少了一份，与「机检覆盖归零」同源。
_ROOT_EQUIV_LAYERS = ["L0", "L1", "L2", "L3", "L4", "L5"]

# (说明, 文件键或 glob, 正则) —— 命中即失败
FORBIDDEN = [
    ('表数回退为 28', 'landing', r'28\s*张理赔表|28表'),
    ('DDL 走 Flyway 自动迁移（ADR-0001 禁）', 'landing', r'DDL 走 Flyway 版本化'),
    ('应用层角色包 service/ 复活（ADR-0035 禁；domain/service 属领域层合法形态不在此列）', 'landing', r'├── service/\s+#\s*应用服务'),
    ('超期关单替代法定核定（合规红线）', 'landing', r'按内部 SLA 关单'),
    ('旧推翻率定义（分母漏否决部分）', 'landing', r'人工否决 AI 建议数 / AI 建议被采纳数'),
    ('恒等于 1 的人工确认率定义', 'landing', r'人工确认率 \| 人工 `decision` 生效数'),
    ('枚举计数回退（六维 14 值/因子 10）', 'landing', r'六维字典（14 值）|因子 `factor`（10）'),
    ('limit_hour 类型回退为 INT', 'landing', r'`limit_hour` / `severeness_level` \| INT'),
    ('引用已归档《系统设计方案》章节', 'fde', r'设计方案 §9\.3'),
    ('OCR 并发 ≥50 写成吞吐要求', 'prd', r'OCR 并发 ≥50'),
    ('一期流程画入 RAG', 'prd', r'调用 OCR/风险评分/RAG'),
    ('US-02「30 秒知道理赔结果」口径混用', 'prd', r'30秒内知道理赔结果'),
    ('幂等键退回两段式（资金事故诱因）', 'ai', r'idempotencyKey = claimNo \+ ":" \+ stage;'),
    ('自建 LLMClient 绕开统一出口', 'ai', r'自建 LLMClient'),
    ('表数回退为 28（AI 方案）', 'ai', r'28 张理赔表'),
    ('熔断单档化并错误引用 PRD（04）', 'delivery/04*.md', r'<0\.85（与 PRD §6 熔断器统一'),
    ('99.99% SLA 无档位前提（超卖）', 'delivery/10*.md', r'可用性 99\.99%（对外承诺档）'),
    ('部署手册教明文写密钥', 'delivery/08*.md', r'配置（config\.yaml）：租户、密钥'),
    ('RTO 退回 15 分钟（US-09 为 5min）', 'delivery/08*.md', r'15 分钟内恢复或降级'),
    ('行锁并发实测（业务层禁 FOR UPDATE）', 'delivery/07*.md', r'TiDB 行锁并发实测'),
    ('脱敏示例对时序字段抖动', 'delivery/02*.md', r'- name: invoice_date\n    algorithm: jitter'),
    ('盐单人托管（§11.3 双人分片）', 'delivery/02*.md', r'仅数据管理员（1 人）可访问'),
    ('删除条款越过监管归档（§12.3 撤站矩阵）', 'delivery/01*.md', r'删除或返还数据（法律要求保留的除外'),
    ('POC 承诺 4 周出评测报告（排期真源 10 周）', 'delivery/03*.md', r'4 周内出评测报告'),
    # --- 2026-10-23 第三批（语义漂移 + 核码登记）---
    ('Higress 进入 PRD（未落地选型不得进需求层）', 'prd', r'Higress'),
    ('Higress 写成 AI 方案既定架构组件（待验证选型）', 'ai', r'Higress'),
    ('RocketMQ 写成一期事件通信（二期选型）', 'prd', r'（RocketMQ）'),
    ('幂等键取报案号（报案号服务端生成；FDE）', 'fde', r'取报案号'),
    ('幂等键取报案号（05 契约）', 'delivery/05*.md', r'取报案号'),
    ('US-02 旧口径「最终赔付以人工确认为准」', 'prd', r'最终赔付以人工确认为准'),
    ('区块链存证未标注可选（PRD 图注）', 'prd', r'结案，区块链存证|⛓ 区块链存证'),
    ('后督回流写成一期案件状态回流（V2 预留）', 'landing', r'回流 30/31/32 质检'),
    ('A7 登记回退为「未落地」（端点已实现）', 'landing', r'额度占额按 §5\.3 归资金阶段'),
    ('安全三件套计数回退为 64 例（实为 8 例）', 'landing', r'单测 64 例'),
    ('超期关单误登记为 V2 预留（领域能力已落地）', 'landing', r'`21→45` 超期关单等）为 V2'),
    ('限界上下文数回退为 9（§3.6 权威切割为 8）', 'landing', r'9 个限界上下文'),
    ('申诉发起端点回退为未落地', 'landing', r'申诉发起端点未落地'),
    ('发起申诉端点回退为未落地', 'delivery/05*.md', r'发起申诉.*代码未落地'),
    # --- 2026-10-25 第四批（评审复核：口径漂移 + 宣称过誉 + 空话式排期）---
    ('材料解析口径回退为 98%/翻拍 85%（第二套口径已废）', 'mvp', r'数电票 ≥98%'),
    ('对外口径回退为 APPEALING（04 Rulebook）', 'delivery/04*.md', r'\|APPEALING 状态'),
    ('对外口径回退为 APPEALING（09 培训）', 'delivery/09*.md', r'进入 APPEALING'),
    ('排期差额用「靠并行消化」空话掩盖（已废止）', 'landing',
     r'靠并行、范围收窄（AI 技术方案 §12\.1 的收窄建议）消化'),
    ('AI 硬配额回到 2000 元默认值（会在 2.7% 用量处误降级）', 'landing',
     r'硬配额 \*\*2000 元/月/租户\*\*'),
    ('AI 配额默认值回退（AI 方案）', 'ai', r'软配额 500 元/月/租户'),
    # 2026-10-25 二次核码：CloseExpiredClaimJob 已真实落地（事项2 时 mvn clean test 116/0/0，事项7 后全模块 120/0/0），旧 FORBIDDEN（禁登记"已建"）撤销；改由下方 REQUIRED 防回退至"未落地"
    ('bone-ai 端口登记为已接入（实为零实现）', 'landing', r'bone-ai 适配器随 `bone-ai-orchestration` 接入'),
    ('PRD 裸承诺 99.99% 无档位前提（超卖）', 'prd', r'\|系统可用性\|99\.99%（对外承诺）'),
    # --- 2026-10-30 第五批（P9 整合：性能/SLO「唯一真源」锁值）---
    #  §11.2 已自声明为性能与 SLO 的唯一真源，但此前只靠人工遵守：
    #  深化篇曾另立「文档解析 ≤3s」，与 §11.2 的「数电票 P95 < 5s」并存数周无人发现。
    #  「分级预算」（AI 方案 ≤8s/≤6s/≤2s 合计 ≈27s）是合法分解；被禁的是**整体口径**另立。
    ('文档解析预算另立 ≤3s（§11.2 数电票 P95<5s 为唯一真源）', '*.md', r'文档解析[^\n]{0,10}≤\s*3\s*s'),
    ('AI 建议整体预算另立 ≤5s（§11.2 建议返回 P95≤30s；分级预算可，整体口径不可）', '*.md', r'AI 建议[^\n]{0,12}≤\s*5\s*s'),
    ('端到端自动通过预算写成 ≤30s（§11.2 为 P95<60s；30s 是「建议返回」口径，不可互换）', '*.md', r'端到端[^\n]{0,12}(?:<|≤)\s*30\s*s(?!\s*内)'),
    # --- 2026-10-30 第六批（V1 重建后定位纠偏：oracle 不是产品代码）---
    #  背景：synap-claim-oracle 的 README 与 pom 曾自称「生产实现骨架」「生产级模块」「7 端口的
    #  JDBC 生产实现」。读者据此以为它是待上线的产品代码（实测确有误判），进而提出把它并入
    #  bone-apps/synap-claim——而这恰好违反 AdjustmentEngine 类注释的明文禁令
    #  「不允许两套算法并存」。它真实身份是双轨验证的第二轨（独立 oracle），
    #  价值全在「独立于被测实现」，一旦并入即自证。
    ('synap-claim-oracle 自称为生产实现骨架（它是双轨 oracle，不是产品代码）',
     '../synap-claim-oracle/synap-claim-settlement/README.md', r'生产实现骨架'),
    ('synap-claim-oracle 恢复旧「生产级模块」表述（会误导为待上线代码）',
     '../synap-claim-oracle/synap-claim-settlement/README.md',
     r'Maven 模块（生产级|## 生产级实现要点'),
    # --- 2026-11-09 第七批（O-01：`总体技术方案.md` 数字首次入机检）---
    #  背景：该文件此前不在 checkKeys 内，同日两次实测类数 378 → 382、Controller
    #  31 → 35 都无人发现，且 §4 表内 7 行相加 147 与声明 148 互斥（O-01）。
    #  台账 O-01 原文要求「正则写进脚本，不在正文重复字面量以免自触发」——
    #  故下列旧数字**只存在于本脚本**，正文只写「已加 FORBIDDEN」。
    ('Controller 数回退为 31（实测 35，含 `/api/v1/internal` 运维面）', 'arch',
     r'31 Controller|rest（31）'),
    ('类总数回退为 370/378（2026-11-08 复测 382）', 'arch',
     r'(?:370|378) 个 Java 类'),
    ('`domain/model/` 子集文件数回退为 147/148（实测 150）', 'arch',
     r'14[78] 个文件'),
    ('应用服务数回退为 34/35（实测 36）', 'arch',
     r'3[45] 个应用服务'),
    # 旧四阶段的行标题：M2 是「标量 → 超集 JSON」，migrate/switch/contract 的
    # 双向双写前提不成立（旧标量无法表达分段/区间/累计）。只禁**行标题**，
    # 不禁 expand/contract 这几个词本身——§9.1.1 仍要讲「为什么它不适用」。
    ('M2 退回 migrate 阶段（存量回填，标量→超集 JSON 不可逆）', 'arch',
     r'\*\*② Migrate\*\*'),
    ('M2 退回 switch 阶段（双向双写一个发版周期）', 'arch', r'\*\*③ Switch\*\*'),
    ('M2 退回 contract 阶段（删旧标量列，历史版本不可还原）', 'arch',
     r'\*\*④ Contract\*\*'),
]

# (说明, 文件键或 glob, 正则) —— 缺失即失败
REQUIRED = [
    ('额度事务×三值字段映射矩阵（§11.7 判定基准）', 'landing', r'额度事务 × 三值字段映射矩阵'),
    ('宣称态 vs 实现态登记', 'landing', r'宣称态 vs 实现态'),
    ('置信度三档→状态映射（复核档落点）', 'landing', r'AI 置信度三档 → 状态映射'),
    ('差异率三阈值口径表', 'landing', r'差异率口径表'),
    ('RPO 绑定部署形态', 'landing', r'RPO \*\*按部署形态绑定\*\*'),
    ('LLM 三重校验上线门禁', 'ai', r'缺一不得启用 `AiAssess`'),
    ('模型选型与路由（vLLM/云端分层）', 'ai', r'### 2\.3 模型选型与路由'),
    ('幂等键三段式', 'ai', r':{stage}:{attempt}|stage \+ ":" \+ attempt'),
    ('可用性档位↔拓扑映射（不得超卖）', 'fde', r'可用性档位与部署形态映射'),
    ('cutover 小节', 'fde', r'### 6\.1 迁移与 cutover'),
    ('cutover 检查清单', 'fde', r'cutover 检查清单'),
    ('阶段验收签收单模板', 'fde', r'验收签收单'),
    ('CDC 消费者驱动契约测试门禁', 'delivery/07*.md', r'消费者驱动契约测试（CDC）'),
    ('四向对账调度 runbook', 'delivery/08*.md', r'四向对账调度与差异派单'),
    ('培训覆盖风控/对账/集成岗', 'delivery/09*.md', r'风控/反欺诈'),
    ('知识转移清单', 'delivery/09*.md', r'知识转移清单'),
    # --- 2026-10-23 第三批 REQUIRED（新定型口径，缺失即回退）---
    ('幂等键创建类客户端业务键（FDE）', 'fde', r'创建类取客户端业务键'),
    ('幂等键创建类客户端业务键（05）', 'delivery/05*.md', r'创建类取客户端生成的业务键'),
    ('单案 >5 份不承诺 30s（口径卡）', 'landing', r'单案材料 >5 份的案件不计入'),
    ('单案 >5 份不承诺 30s（PRD 吞吐行）', 'prd', r'单案材料 >5 份不承诺 30s'),
    ('一期后督抽检落地边界核码登记', 'landing', r'落地边界（2026-10-23 核码登记）'),
    ('选型表登记 Higress 待验证', 'landing', r'Higress 为候选'),
    ('存证可选口径（选型表）', 'landing', r'第三方区块链存证为\*\*可选\*\*'),
    ('超期关单 Job 已建登记（分线：CloseExpiredClaimJob 已建 / 其余 3 Job 待建）', 'landing', r'CloseExpiredClaimJob.{0,8}已建'),
    ('端点路径分歧登记（§10.6 偏离 #6）', 'landing', r'任务中心路径'),
    # 2026-11-04：原断言锁死「644 例 JUnit + 26 条 ArchUnit」这一绝对计数，属脆弱断言——
    # 每加一批用例就要同步改文档 + 改断言，否则机检假红（本次 _check_numbers.py 失败即由此而来，
    # 落地版已演进到 745 例但该断言仍要求 644+26 字面量存在）。改为校验「实机复验 + 计数登记」
    # 的结构在位，数值由落地版当期登记承载、不锁死。
    # 当前实数（2026-11-04 实机）：mvn -o -pl bone-apps/synap-claim/synap-claim-app clean test → 813/0/0/5。
    ('单测计数核码登记（实机复验 + 计数在位；数值不锁死，防脆弱断言）', 'landing',
     r'模块合计单测 \*\*\d+ 例'),
    ('单测计数核码登记须附实机命令与结果摘要', 'landing',
     r'mvn -o -pl bone-apps/synap-claim/synap-claim-app clean test[^\n]*Tests run: \d+, Failures: 0, Errors: 0'),
    ('I1 文本层子集口径（PRD 验收）', 'prd', r'数电票文本层子集'),
    ('质检三件未落地登记', 'landing', r'质检三件'),
    ('adapter/schedule 入口未建登记', 'landing', r'adapter/schedule` 触发入口'),
    ('申诉发起端点已落地登记（复数 /appeals 对齐契约）', 'landing', r'申诉发起端点已落地'),
    # --- 2026-10-25 第四批 REQUIRED（新定型口径，缺失即回退）---
    ('ADR 生效状态表（Accepted ≠ 已实现）', 'landing', r'ADR 生效状态'),
    ('bone-ai 六端口未接线登记', 'landing', r'六端口：AiGateway / RuleEngine'),
    ('ADR-003/008 依赖表未落地登记', 'landing', r'ADR-003 / ADR-008 依赖表'),
    ('偏离项到期治理台账（owner+到期+硬失败）', 'landing', r'偏离项治理纪律'),
    ('配额改为按租户推导算法（删默认值）', 'landing', r'配额下发算法'),
    ('MVP 迭代容量校验矩阵', 'mvp', r'迭代容量校验矩阵'),
    ('MVP 三段式排期（乐观/基准/保守）', 'mvp', r'基准 13 周（推荐）'),
    ('MVP 人日合计校正（145）', 'mvp', r'145 人日'),
    ('PRD 99.99% 档位前提', 'prd', r'99\.99% 自 V2 同城双活起适用'),
    # 超期关单 Job 防回退至"未落地" 已由上方 REQUIRED（CloseExpiredClaimJob.{0,8}已建）覆盖，此处不再单列
    # --- 2026-10-30 第五批 REQUIRED（P9：把「唯一真源」从声明变成断言）---
    #  只锁**已定型**的权威值；设计目标（如理算 ≤200ms、STP 端到端 ≤30s）未进本节——
    #  它们尚未经压测或客户认账，锁进 REQUIRED 等于把目标冒充承诺。
    ('性能/SLO 唯一真源声明在位（否则其他文档可合法另立口径）', 'landing', r'本节为性能与 SLO 指标的唯一真源'),
    ('数电票解析 P95<5s', 'landing', r'P95 < 5s'),
    ('自动通过路径端到端 P95<60s', 'landing', r'P95 < 60s'),
    ('建议返回 P95≤30s 口径卡', 'landing', r'建议返回 P95 ≤30s'),
    ('规则批量执行 3s 熔断', 'landing', r'< 3s 熔断'),
    ('工作台待办列表 P95<500ms', 'landing', r'P95 < 500ms'),
    ('对外承诺档 99.99% 须绑同城双活（单集群不适用，防超卖）', 'landing', r'单集群形态不适用该档'),
    # --- 2026-10-30 第六批 REQUIRED（防「唯一设计真源」悄悄过期）---
    #  背景：V1 重建（10-30）把生产实现从「标量四步」改成「九步分层管线」，但设计真源
    #  design/理算引擎设计.md 仍写 0–7 八步（全文 "V1" 零命中）。新设计当时只落在
    #  生产就绪完善/16、17 两个分册——而那两处**不纳机检**（见文档地图 §五）。
    #  于是出现最坏的组合：真源过期 + 真内容藏在不受保护的地方，读者无从判断该信谁。
    #  这组断言把「设计真源必须覆盖当前实现」从口头约定变成机检。
    #  注意：断言的是**关键概念在位**，不是逐字复述——避免逼人抄分册。
    ('设计真源须声明当前实现（否则 §4 的参考实现编排会被误读为现状）',
     'design/理算引擎设计.md', r'当前生产实现已按 V1 重建'),
    ('设计真源须覆盖 V1 九步编排（16/17 分册要点必须回写，不能只留在旁支）',
     'design/理算引擎设计.md', r'九步编排（权威顺序，不可调换）'),
    ('设计真源须覆盖两个 Min 嵌套（控额嵌套顺序不可交换，写错即吞额度）',
     'design/理算引擎设计.md', r'两个 Min 嵌套'),
    ('设计真源须登记「死配置」教训（声明 ≠ 生效；七层控额曾只有三层接线）',
     'design/理算引擎设计.md', r'文档宣称 ≠ 代码做到'),
    ('设计真源须写明 V1〔缺〕能力处置（显式拒绝而非猜算法）',
     'design/理算引擎设计.md', r'显式拒绝，而不是猜一个算法'),
    ('synap-claim-oracle 定位声明在位（非产品代码 + 不并入生产 + 独立 CI 入口）',
     '../synap-claim-oracle/synap-claim-settlement/README.md', r'这不是产品代码'),
    # --- 2026-11-02 P0-2（租户隔离 CI 护栏 golive.04 已落地）---
    #  防设计回退为「靠人守」：SSOT §12.2.1 必须登记租户隔离反例测试类，
    #  该测试类 + 两条租户读取结构锁（tenant_context_read_only_by_tenant_port_adapter）是
    #  OWASP A01 身份边界的自动化护栏，缺位即回退为人工守门。
    ('租户隔离 ArchUnit 反例测试在位（防设计回退为靠人守）', 'landing', r'TenantIsolationGuardrailTest'),
    # --- 2026-11-09 第七批 REQUIRED（O-01）---
    #  只锁**结构**不锁数值：数值由「基线行 + 复算命令 + 当天日期」承载，
    #  自洽性由 arch_baseline_verdict() 算，避免 2026-11-04「644 例」式的脆弱断言。
    ('基线行须带实测日期（快照必须能判断新旧，否则「382 类」是永久宣称）', 'arch',
     r'事实基线\*\*（20\d{2}-\d{2}-\d{2}'),
    ('数字须附复算命令（不能只给结论、不给复核手段）', 'arch',
     r"find src/main/java -name '\*\.java' \| wc -l"),
    ('PITest 现状句在位（防把目标态写成已生效门禁）', 'arch',
     r'当前不存在这个门禁'),
    ('M2 迁移口径在位（双读回落；退回双写四阶段即失去可回滚性）', 'arch',
     r'Dual-read'),
    ('M2 不做全量存量回填的判断在位（否则旧标量要被迫编造数值）', 'arch',
     r'不做全量存量回填|不回填 6 个新维度默认值'),
    ('开放项台账在位（未闭环事项必须有归处，不能散在正文）', 'arch',
     r'## 13\. 开放项台账'),
]


def read(key):
    if key in MAIN:
        return io.open(BASE + '/' + MAIN[key], encoding='utf-8').read()
    if key == '*.md':
        # 「根目录」语义等价集：分层前位于根目录的那些层（见 _ROOT_EQUIV_LAYERS 注释）
        txt = ''
        for p in sorted(M.in_layers(_ROOT_EQUIV_LAYERS)):
            txt += io.open(BASE + '/' + p, encoding='utf-8').read()
        return txt
    # 其余是形如 'delivery/05*.md' 的历史逻辑 glob：先映射到真实层目录再 glob
    txt = ''
    for f in glob.glob(BASE + '/' + M.denormalize(key)):
        txt += io.open(f, encoding='utf-8').read()
    return txt


def check_adr_traceability():
    """ADR 可追溯性（Nygard 治理）：正文引用的 ADR-XXXX 必须在 §14 索引定义。

    悬空引用 = 写文档时引用了某个 ADR 编号但该编号从未在 §14 登记，
    会破坏「为何这么定」的评审追溯链。
    """
    txt = read('landing')
    m = re.search(r'## 14\. 架构决策记录（ADR）索引(.*?)(?:\n## 15\.|\Z)', txt, re.S)
    sec14 = m.group(1) if m else ''
    defined = set(re.findall(r'ADR-(\d+)', sec14))
    refs = set(re.findall(r'ADR-(\d+)', txt))
    # H-xxx 业务 ADR 不在本校验范围；ADR-xxxx 一律须有定义
    missing = sorted(refs - defined, key=lambda x: int(x))
    if missing:
        return ['【FAIL·ADR可追溯】正文引用 ADR-%s 在 §14 索引未定义（悬空引用）'
                % ' / ADR-'.join(missing)]
    return []


def check_no_hardcoded_ddl_count():
    """扫活跃口径载体，命中硬编码表数即判红。

    排除两类**刻意保留**的历史取证：
      * `00-governance/02-整合历史.md` —— 叙述某时点的事实（P4 当时确实是 35 表），
        历史不该被当下的口径规约改写；
      * `10-archive/` 与 `*.txt` 冻结交付说明 —— 不在扫描后缀 / 目录内。
    排除规则必须是**白名单枚举**而不是「看起来像历史就跳过」，否则会静默漏扫。
    """
    files = []
    for layer in ('L0', 'L1', 'L2', 'L3', 'L4', 'L5', 'L6', 'L7', 'L8', 'L9'):
        for p in M.in_layers([layer]):
            if '10-archive' in p or p.endswith('02-整合历史.md'):
                continue
            files.append(p)
    files += ['docs.manifest.json', 'AI-INDEX.md']  # 摘要真源 + 它的生成物
    hits = []
    for rel in sorted(set(files)):
        full = os.path.join(BASE, rel)
        if not os.path.isfile(full):
            continue
        for i, line in enumerate(io.open(full, encoding='utf-8').read().splitlines(), 1):
            if DDL_COUNT_PAT.search(line):
                hits.append('%s:%d: %s' % (rel, i, line.strip()[:80]))
    if hits:
        return (['【FAIL·硬编码表数】活跃文档仍在写死 DDL 表数（应写「§9.5 DDL 集合」）：']
                + ['    ' + h for h in hits])
    return []


ORACLE_PATH_RE = r'`(doc/ai-product/synap-claim-oracle/synap-claim-settlement)/?`'


# --- 架构基线算术一致性（2026-11-09，O-01 落地）--------------------------------
# 为什么**不锁死绝对计数**：本文件 2026-11-04 已有前车之鉴——把「644 例 JUnit」
# 写进 REQUIRED，每加一批用例就假红，最后被迫改成「结构在位、数值不锁死」。
# 类数同理：382 这个数字每次加类都会变，锁死它只会逼人删断言。
# 但「任何数字都不校验」正是本轮要修的 bug：同日两次实测 378 → 382，且 §4 表
# 内 7 行相加 147 ≠ 声明 148。前者是**日期化快照**（合法），后者是**算错**（非法）。
# 故这里只校验「同一份文档内多处声明必须自洽」：
#   ① §4 表逐行类数之和 == 「表内 N 行相加 = M」的 M == 「domain/model 的 M 个文件」
#   ② §4 表服务名个数之和 == 基线行声明的应用服务数
#   ③ §4 表反引号子域个数之和 == 基线行声明的子域数
#   ④ §4.1.x 小节标题里的「N 类 / M 个应用服务」== 对应表格行
# 这四条在**加类**时不会误报（表与头部一起改就自洽），在**只改一处**时必红。
ARCH_HEAD_PAT = re.compile(
    r'事实基线\*\*（(?P<date>\d{4}-\d{2}-\d{2})[^）]*）'
    r'[^\n]*?\*\*(?P<cls>\d+) 个 Java 类\s*/\s*(?P<sub>\d+) 个领域子域\s*/\s*'
    r'(?P<svc>\d+) 个应用服务\s*/\s*(?P<ctl>\d+) 个 Controller')
# | **案件 Claim** | `claim` `inspection` … | 38 | ClaimCase / Inspection / … | 说明 |
ARCH_ROW_PAT = re.compile(
    r'^\|\s*\*\*(?P<ctx>[^*|]+?)\*\*\s*\|(?P<subs>[^|]*)\|(?P<cls>\s*(?:\d+|—)\s*)\|(?P<svcs>[^|]*)\|',
    re.M)
ARCH_SUM_PAT = re.compile(r'表内\s*(?P<rows>\d+)\s*行相加\s*=\s*(?P<total>\d+)')
ARCH_SUBSET_PAT = re.compile(r'domain/model/`?\s*的\s*(?P<n>\d+)\s*个文件')
ARCH_SEC_PAT = re.compile(
    r'^#### (?P<sec>4\.1\.\d+) (?P<ctx>.+?)（(?P<cls>\d+) 类 / (?P<svc>\d+) 个应用服务）', re.M)


def _row_class_count(raw):
    raw = raw.strip()
    return 0 if raw in ('', '—') else int(raw)


def _row_service_names(raw):
    # 服务列形如 `ClaimCase / Inspection / …`；空列或单独一个 — 视为 0
    names = [x.strip() for x in raw.split('/')]
    return [x for x in names if x and x not in ('—', '-')]


def arch_baseline_verdict(text):
    """纯函数：§4 归属表 ↔ 基线行 ↔ 逐节标题 的三方算术一致性。

    抽成纯函数是为了 `--self-test` 能用假文本反证「算错必红、自洽不红」，
    而不需要改写真实文档（共享树里改真文档会被并行会话覆盖，代价不可接受）。
    """
    probs = []
    head = ARCH_HEAD_PAT.search(text)
    if not head:
        return ['找不到「事实基线（YYYY-MM-DD …）：N 个 Java 类 / … 」基线行，'
                '或字段顺序被改（本判据按该顺序三元定位）']
    rows_m = list(ARCH_ROW_PAT.finditer(text))
    if not rows_m:
        return ['找不到 §4 上下文归属表（形如 `| **案件 Claim** | `claim` | 38 | … |`）']
    cls_sum = sum(_row_class_count(m.group('cls')) for m in rows_m)
    svc_sum = sum(len(_row_service_names(m.group('svcs'))) for m in rows_m)
    sub_sum = sum(len(re.findall(r'`[^`]+`', m.group('subs'))) for m in rows_m)

    s = ARCH_SUM_PAT.search(text)
    if not s:
        probs.append('缺少「表内 N 行相加 = M」自证句（无法对账表格合计）')
    else:
        if int(s.group('rows')) != len(rows_m):
            probs.append('自证句声明 %s 行，实际表格 %d 行' % (s.group('rows'), len(rows_m)))
        if int(s.group('total')) != cls_sum:
            probs.append('自证句声明合计 %s，表格逐行相加实为 %d（差 %+d）'
                         % (s.group('total'), cls_sum, cls_sum - int(s.group('total'))))
    sub = ARCH_SUBSET_PAT.search(text)
    if sub and int(sub.group('n')) != cls_sum:
        probs.append('声明 `domain/model/` 有 %s 个文件，表格逐行相加实为 %d'
                     % (sub.group('n'), cls_sum))
    if int(head.group('svc')) != svc_sum:
        probs.append('基线行声明 %s 个应用服务，表格服务列实为 %d 个'
                     % (head.group('svc'), svc_sum))
    if int(head.group('sub')) != sub_sum:
        probs.append('基线行声明 %s 个领域子域，表格反引号子域实为 %d 个'
                     % (head.group('sub'), sub_sum))

    by_ctx = {m.group('ctx').strip(): m for m in rows_m}
    for m in ARCH_SEC_PAT.finditer(text):
        ctx = m.group('ctx').strip()
        row = by_ctx.get(ctx)
        if row is None:
            probs.append('§%s 小节「%s」在 §4 归属表里找不到同名行（改名未同步？）'
                         % (m.group('sec'), ctx))
            continue
        if int(m.group('cls')) != _row_class_count(row.group('cls')):
            probs.append('小节「%s」写 %s 类，§4 表实为 %s 类'
                         % (ctx, m.group('cls'), row.group('cls').strip()))
        if int(m.group('svc')) != len(_row_service_names(row.group('svcs'))):
            probs.append('小节「%s」写 %s 个应用服务，§4 表服务列实为 %d 个'
                         % (ctx, m.group('svc'), len(_row_service_names(row.group('svcs')))))
    return probs


def check_arch_baseline():
    """§4 归属表 / 基线行 / 逐节标题三方自洽（见 arch_baseline_verdict 注释）。"""
    return ['【FAIL·架构基线】' + p for p in arch_baseline_verdict(read('arch'))]



def oracle_ci_verdict(wf_text, ptr_text, has_pom):
    """纯函数：CI working-directory ↔ 真实目录 ↔ 指针文档三者一致性判定。

    `has_pom(wd)` 由调用方注入，便于 --self-test 用假数据反证而**不碰任何文件**
    （共享树里临时改写仓库根 CI 文件会被并行会话覆盖，代价不可接受）。
    """
    m = re.search(r'working-directory:\s*(\S+)', wf_text)
    if not m:
        return ['workflow 里没有 working-directory，mvn test 会在仓库根跑']
    wd = m.group(1).rstrip('/')
    if not has_pom(wd):
        return ['working-directory=%s 指向的目录不存在或无 pom.xml（目录被移动？）' % wd]
    m2 = re.search(ORACLE_PATH_RE, ptr_text)
    if not m2:
        return ['指针文档里找不到 oracle 真实路径登记']
    if m2.group(1).rstrip('/') != wd:
        return ['指针文档=%s 与 CI working-directory=%s 不一致' % (m2.group(1), wd)]
    return []


def check_oracle_ci_wiring():
    """CI 入口 ↔ oracle 真实目录 ↔ 指针文档，三者必须指向同一个地方。

    「脚本存在 ≠ 门禁生效」的同族风险：若将来有人移动 oracle 目录而没改
    `.github/workflows/synap-claim-oracle.yml`，CI 会直接失败，但本目录的
    文档门禁全绿、看不出任何异常。把三者绑定成可判定判据，移动即报红。
    """
    repo = os.path.abspath(os.path.join(BASE, '..', '..', '..'))  # synapClaim → ai-product → doc → bone
    wf = os.path.join(repo, '.github', 'workflows', 'synap-claim-oracle.yml')
    ptr = os.path.join(BASE, 'synap-claim-oracle-POINTER.md')
    if not os.path.isfile(wf):
        return ['【FAIL·oracle CI】找不到 %s，独立 CI job 可能已被删除' % wf]
    if not os.path.isfile(ptr):
        return ['【FAIL·oracle CI】找不到指针文档 synap-claim-oracle-POINTER.md']
    wf_text = io.open(wf, encoding='utf-8').read()
    ptr_text = io.open(ptr, encoding='utf-8').read()
    verdict = oracle_ci_verdict(
        wf_text, ptr_text,
        lambda wd: os.path.isfile(os.path.join(repo, wd, 'pom.xml')))
    return ['【FAIL·oracle CI】' + v for v in verdict]


def self_test():
    """判据自证：全绿也可能是「正则根本不命中」，必须正反都验。"""
    positives = ['工程契约 SSOT：35 表 DDL', '（36 张理赔表）', '建表数 = 36', 'DDL 36 表']
    negatives = ['§9.5 设计态 DDL 集合', '26 表全部通过（机检输出，非文档口径）',
                 '§9.1 清单 − §9.0 差异登记 = §9.5 DDL 集合']
    bad = [p for p in positives if not DDL_COUNT_PAT.search(p)]
    bad += [n for n in negatives if DDL_COUNT_PAT.search(n)]

    # oracle CI 接线判据：纯函数反证，不读写任何文件
    real = 'doc/ai-product/synap-claim-oracle/synap-claim-settlement'
    good_wf = 'working-directory: ' + real
    good_ptr = '真实位置： `' + real + '/`'
    yes = lambda wd: True
    no = lambda wd: False
    probes = [
        ('合规正例', oracle_ci_verdict(good_wf, good_ptr, yes) == []),
        ('目录被移走（无 pom）', bool(oracle_ci_verdict(good_wf, good_ptr, no))),
        ('CI 指向新目录但指针没改', bool(oracle_ci_verdict(
            'working-directory: doc/ai-product/MOVED', good_ptr, yes))),
        ('workflow 删了 working-directory', bool(oracle_ci_verdict('run: mvn test', good_ptr, yes))),
        ('指针文档丢了路径登记', bool(oracle_ci_verdict(good_wf, '只是一个指针', yes))),
    ]
    for label, ok in probes:
        if not ok:
            bad.append('oracle CI 判据：' + label)

    # 架构基线算术判据（O-01）：纯函数探针，不读写任何文件。
    # 每条都是**改坏一处**，验证判据真的在算而不是恒过。
    good = (
        '> **事实基线**（2026-11-08 19:40 对 `x` 复测）：**10 个 Java 类 / 2 个领域子域 / '
        '3 个应用服务 / 3 个 Controller**\n'
        '| **甲 A** | `a` | 6 | S1 / S2 | 说明 |\n'
        '| **乙 B** | `b` | 4 | S1 | 说明 |\n'
        '表内 2 行相加 = 10\n'
        '**§4 表内类数是 `domain/model/` 的 10 个文件**\n'
        '#### 4.1.1 甲 A（6 类 / 2 个应用服务）\n'
        '#### 4.1.2 乙 B（4 类 / 1 个应用服务）\n')
    arch_probes = [
        ('基线自洽不误报', arch_baseline_verdict(good) == []),
        ('行相加合计不符', any('相加' in p for p in arch_baseline_verdict(
            good.replace('行相加 = 10', '行相加 = 11')))),
        ('子集文件数不符', any('domain/model' in p for p in arch_baseline_verdict(
            good.replace('的 10 个文件', '的 11 个文件')))),
        ('应用服务数不符', any('应用服务' in p for p in arch_baseline_verdict(
            good.replace('3 个应用服务 / 3 个 Controller', '4 个应用服务 / 3 个 Controller')))),
        ('子域数不符', any('子域' in p for p in arch_baseline_verdict(
            good.replace('2 个领域子域', '3 个领域子域')))),
        ('逐节标题类数不符', any('小节' in p for p in arch_baseline_verdict(
            good.replace('甲 A（6 类', '甲 A（7 类')))),
        ('小节在归属表无同名行', any('同名行' in p for p in arch_baseline_verdict(
            good.replace('#### 4.1.2 乙 B', '#### 4.1.2 丙 C')))),
        ('基线行整体缺失', bool(arch_baseline_verdict(good.replace('事实基线', '事实')))),
    ]
    for label, ok in arch_probes:
        if not ok:
            bad.append('架构基线判据：' + label)

    print('=== _check_numbers.py --self-test ===')
    if bad:
        for b in bad:
            print('  ❌ 判据误判：%r' % b)
        return 1
    print('  ✅ 4 个正例命中（35 表 / 36 张 / 建表数 / DDL 36）')
    print('  ✅ 3 个反例不误报（集合引用 / 机检输出 / 对账式表述）')
    print('  ✅ oracle CI 判据 5 个纯函数探针（不碰文件）：合规正例 + 4 种改坏均判红')
    print('  ✅ 架构基线判据 8 个纯函数探针（不碰文件）：自洽不误报 + 7 种改坏均判红')
    return 0


def main():
    if '--self-test' in sys.argv:
        return self_test()
    fails = []
    fails += check_adr_traceability()
    fails += check_no_hardcoded_ddl_count()
    fails += check_oracle_ci_wiring()
    fails += check_arch_baseline()
    for desc, key, pat in FORBIDDEN:
        m = re.search(pat, read(key))
        if m:
            fails.append('【FAIL·复发】%s → 命中 %r（%s）' % (desc, m.group(0)[:40], key))
    for desc, key, pat in REQUIRED:
        if not re.search(pat, read(key)):
            fails.append('【FAIL·缺失】%s → 未找到 %s（%s）' % (desc, pat, key))
    if fails:
        print('=== 数值/口径回归机检 ===')
        for f in fails:
            print(f)
        print('共 %d 项失败' % len(fails))
        return 1
    print('=== 数值/口径回归机检 ===')
    print('【OK】%d 项禁用表述零命中，%d 项关键口径均在位' % (len(FORBIDDEN), len(REQUIRED)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
