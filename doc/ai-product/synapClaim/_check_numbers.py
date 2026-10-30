#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""数值/口径回归机检（防 2026-10-23 已修复问题复发）。

_check_contract_drift.py 管契约面（规则 ID/枚举/错误码），本脚本管**散文层的
数值与口径断言**：FORBIDDEN = 已定性为错误、不应再出现的表述；REQUIRED = 已定型
的关键口径，缺失即回退。任一失败非零退出。
"""
import io, re, sys, glob

BASE = '.'
MAIN = {
    'landing': 'synapClaim 智能理赔平台-工程详细设计（落地版）.md',
    'prd': 'synapClaim 智能理赔平台-产品需求文档.md',
    'ai': 'synapClaim 智能理赔平台-AI 技术方案.md',
    'fde': 'synapClaim 智能理赔平台-部署集成方案（FDE）.md',
    'mvp': 'synapClaim MVP 可落地执行方案.md',
}

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
    ('单测计数核码登记（136=112+24）', 'landing', r'112 例 JUnit \+ 24 条 ArchUnit'),
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
]


def read(key):
    if key in MAIN:
        return io.open(BASE + '/' + MAIN[key], encoding='utf-8').read()
    txt = ''
    for f in glob.glob(BASE + '/' + key):
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


def main():
    fails = []
    fails += check_adr_traceability()
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
