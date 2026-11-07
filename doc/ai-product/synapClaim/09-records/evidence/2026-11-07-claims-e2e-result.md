---
doc_id: records.evidence.e2e-20261107
role: evidence
currency: frozen
---

# 2026-11-07 前后端 E2E 时点快照

> 采集时间 2026-11-07T03:06:38Z · 采集 HEAD d953da0fc · real-services-no-mock。
> 下方为 `outputs/claims-e2e-result.json` 逐字快照（本机一次性实跑，不可复现）。

```json
{
  "startedAt": "2026-11-07T03:06:38.226Z",
  "mode": "real-services-no-mock",
  "api": [
    {
      "name": "登录经网关 200 且签发 JWT",
      "pass": true,
      "detail": "status=200 tokenLen=4475"
    },
    {
      "name": "赔案列表真实读库 200",
      "pass": true,
      "detail": "status=200 records=3"
    },
    {
      "name": "AI 能力就绪清单经网关可达",
      "pass": true,
      "detail": "status=200 gatesPassed=false"
    },
    {
      "name": "就绪清单如实报未通过（防假绿）",
      "pass": true,
      "detail": "blockers=6"
    },
    {
      "name": "运行期健康度 UTC 日界 30 天窗口 200",
      "pass": true,
      "detail": "status=200 nodes=104 window=2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z"
    },
    {
      "name": "证据窗口同源：后端回显与 EVIDENCE_WINDOW 逐字一致",
      "pass": true,
      "detail": "backend=2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z expected=2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z"
    },
    {
      "name": "负向探针：无 token 被网关拦截 401",
      "pass": true,
      "detail": "status=401"
    },
    {
      "name": "负向探针：超过 31 天窗口被业务校验拒绝 400",
      "pass": true,
      "detail": "status=400 msg=SC_CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID: 时间窗过大：最多 31 天（当前 35 天）"
    },
    {
      "name": "校准报表端点经网关可达且 verdict 为受控枚举（部署态与源码一致）",
      "pass": true,
      "detail": "status=200 verdict=NO_EVIDENCE"
    },
    {
      "name": "无盲标样本时 NO_EVIDENCE 且 ECE/Brier/建议阈值全为 null（不编造数值）",
      "pass": true,
      "detail": "verdict=NO_EVIDENCE ece=null brier=null proposed=null"
    },
    {
      "name": "负向探针：minSamples=1 时生效下限仍为 300/30（守卫可观测、不可绕过）",
      "pass": true,
      "detail": "effective=300/30 verdict=NO_EVIDENCE"
    },
    {
      "name": "正向对照：显式上调到 500/50 时生效值如实回传（只允许上调、不放宽）",
      "pass": true,
      "detail": "effective=500/50"
    },
    {
      "name": "回滚演练只读端点可达且报告结构完整（dry-run）",
      "pass": true,
      "detail": "current=SHADOW target=SHADOW mutations=0"
    },
    {
      "name": "演练零副作用：演练前后生效档位逐位不变（state integrity）",
      "pass": true,
      "detail": "before=SHADOW after=SHADOW"
    },
    {
      "name": "报告口径自洽：atFloor 与当前档一致，且档位与放量快照同源",
      "pass": true,
      "detail": "current=SHADOW before=SHADOW atFloor=true"
    },
    {
      "name": "L3 就绪度复核器可运行且输出可解析",
      "pass": true,
      "detail": "l3Claimable=false counts=判据 12 项：SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0"
    },
    {
      "name": "静态判据与运行期就绪清单结论一致（防单端假绿）",
      "pass": true,
      "detail": "static=false runtime=false"
    },
    {
      "name": "负向自检：静态判据被伪造成可宣称 L3 时会对账失败（判据有判别力）",
      "pass": true,
      "detail": "forged=true"
    },
    {
      "name": "浏览器登录成功并落地 token",
      "pass": true,
      "detail": "localStorage.token 已写入"
    },
    {
      "name": "qiankun 子应用容器真实渲染核赔工作台（4 个主体标记命中 >=3）",
      "pass": true,
      "detail": "url=http://localhost:3000/claim/review#/review hits=核赔工作台/报案号/待领取/决策图 excerpt=核赔工作台 AI 治理 待领取 3 处理中 47 大额（≥1万，需双人复核） 1 列表合计 50 AI 仅出建议，资金与责任结论以确定性规则为准；拒赔须填五要素（事实认定+条款依据"
    },
    {
      "name": "业务页面深链按钮可点击进入 hidden 路由 /ai-governance",
      "pass": true,
      "detail": "buttonFound=true url=http://localhost:3000/claim/review#/ai-governance"
    },
    {
      "name": "hidden 路由 /ai-governance 真实渲染 5 个治理区块（6 标记命中 >=5）",
      "pass": true,
      "detail": "url=http://localhost:3000/claim/review#/ai-governance hits=AI 治理/能力就绪/运行期模型参与/独立盲标校准/放量档位/回滚演练 excerpt=AI 治理 只读运行期证据：能力就绪、模型参与、盲标校准、放量档位、回滚演练与连通性探活 刷新 provider 连通性探活 返回核赔工作台 部署门禁 未达标 存在未解除的门禁项，不得宣称 L3。 能"
    },
    {
      "name": "provider 探活默认零请求（禁自动轮询）",
      "pass": true,
      "detail": "requests=0 hasNotTested=true"
    },
    {
      "name": "无盲标证据时不渲染校准分桶/切片表格（不编造曲线）",
      "pass": true,
      "detail": "verdict=NO_EVIDENCE bucketTableRendered=false"
    },
    {
      "name": "探活响应判词写入运行期证据（不可读即判红，不编造 REACHABLE）",
      "pass": true,
      "detail": "verdict=NOT_CONFIGURED"
    },
    {
      "name": "点击探活按钮后恰好发起 1 次真实探活请求",
      "pass": true,
      "detail": "requests=1"
    },
    {
      "name": "探活结果展示受控判词，不把 NOT_CONFIGURED 写成连通",
      "pass": true,
      "detail": "excerpt=AI 治理 只读运行期证据：能力就绪、模型参与、盲标校准、放量档位、回滚演练与连通性探活 刷新 provider 连通性探活 返回核赔工作台 部署门禁 未达标 存在未解除的门禁项，不得宣称 L3。 能力就绪 风险等级 HIGH 自动通过开关 关闭 门禁阻断项 6 范围说明 部署态"
    },
    {
      "name": "证据窗口单一真源：治理面板声明窗口与 EVIDENCE_WINDOW 逐字一致",
      "pass": true,
      "detail": "panel=2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z expected=2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z"
    },
    {
      "name": "防假绿：参与率展示与同窗口 API 独立重算一致（null/0/百分比三态）",
      "pass": true,
      "detail": "rate=0.0769 expected=7.69% probe={\"windowDeclared\":true,\"status\":200,\"hasExpected\":true,\"forbiddenOnStage\":false,\"forbiddenFullRate\":false,\"rendered\":\"7.69%\"}"
    },
    {
      "name": "防假绿：门禁未通过显示未达标，SHADOW 不写成已自动放款",
      "pass": true,
      "detail": "excerpt=AI 治理 只读运行期证据：能力就绪、模型参与、盲标校准、放量档位、回滚演练与连通性探活 刷新 provider 连通性探活 返回核赔工作台 部署门禁 未达标 存在未解除的门禁项，不得宣称 L3。 能力就绪 风险等级 HIGH 自动通过开关"
    },
    {
      "name": "默认加载的 5 个治理端点全部返回可用数据（第 6 个探活仅手动触发）",
      "pass": true,
      "detail": "endpointErrors=0"
    },
    {
      "name": "负向自检：治理页标记不会被核赔工作台文本命中（判据有判别力）",
      "pass": true,
      "detail": "reviewHits=AI 治理"
    },
    {
      "name": "负向自检：旧宽松正则确实会被 shell 菜单骗过（故已废弃）",
      "pass": true,
      "detail": "loose=true"
    },
    {
      "name": "负向自检：新严格判据不把菜单文本当成子应用",
      "pass": true,
      "detail": "strictHitsOnMenu=0"
    },
    {
      "name": "理赔链路无 5xx",
      "pass": true,
      "detail": "chain5xx=[] external5xx=4"
    },
    {
      "name": "终态对账：最终产物被复核器消费（输出回显本轮探活判词）",
      "pass": true,
      "detail": "verdict=NOT_CONFIGURED"
    },
    {
      "name": "终态对账：判词非 REACHABLE 时 RUNTIME_MODEL_COVERAGE 必须 BLOCKED 且 L3 不可宣称",
      "pass": true,
      "detail": "claimable=false coverage=BLOCKED/PROVIDER_NOT_CONFIGURED"
    }
  ],
  "ui": {
    "loggedIn": true,
    "url": "http://localhost:3000/claim/review#/review",
    "viewportExcerpt": "核赔工作台 AI 治理 待领取 3 处理中 47 大额（≥1万，需双人复核） 1 列表合计 50 AI 仅出建议，资金与责任结论以确定性规则为准；拒赔须填五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限）。 全部待办（待领取 + 处理中） 刷新 报案号 保单号 申请金额 状态 大额 第一审核人 受理时间 操作 CL20261105773428122488930304 E2E-POLICY-0001 ¥100.00 待核赔 - 2026-11-05T06:07:14.002Z 决策图 领取 CL2026110",
    "markersHit": [
      "核赔工作台",
      "报案号",
      "待领取",
      "决策图"
    ],
    "aiGovernanceDeepLink": {
      "buttonFound": true,
      "urlAfterClick": "http://localhost:3000/claim/review#/ai-governance"
    },
    "aiGovernance": {
      "url": "http://localhost:3000/claim/review#/ai-governance",
      "excerpt": "AI 治理 只读运行期证据：能力就绪、模型参与、盲标校准、放量档位、回滚演练与连通性探活 刷新 provider 连通性探活 返回核赔工作台 部署门禁 未达标 存在未解除的门禁项，不得宣称 L3。 能力就绪 风险等级 HIGH 自动通过开关 关闭 门禁阻断项 6 范围说明 部署态清单（适配器自报 + 配置态开关），不代表任何具体案件已执行该能力；运行期证据见决策轨迹 capabilityMissing/capabilityReason/fraudGateReason/triageReason 与 t_claim_rule_hit。deploymentGatesPassed 仅表示部署态闸门不再阻断，不构成放款许可：还须逐案运行期 available=true、确定性分诊通过、大额双人复核规则不触发，以及 §16 风险管理委员会批准与 §17 人工干预/紧急停用机制就绪。 能力 状态 原因 整改项 C2_RULE 未知 DECLARATION_MISSING 适配器须覆盖 declaration() 自报部署态状态 E1_VISUAL 未知 DECLARATION_MISSING 适配器须覆盖 declaration() 自报部署态状态 E2_GRAPH 未知 DECLARATION_MISSING 适配器须覆盖 declaration() 自报部署态状态 E3_KNOWLEDGE 未知 DECLARATION_MISSING 适配器须覆盖 declaration() 自报部署态状态 C5_LLM 未知 DECLARATION_MISSING 适配器须覆盖 declaration() 自报部署态状态 未解除门禁项 AUTO_APPROVE_TOGGLE_OFF；C2_RULE:UNKNOWN:DECLARATION_MISSING；E1_VISUAL:UNKNOWN:DECLARATION_MISSING；E2_GRAPH:UNKNOWN:DECLARATION_MISSING；E3_KNOWLEDGE:UNKNOWN:DECLARATION_MISSING；C5_LLM:UNKNOWN:DECLARATION_MISSING 运行期模型参与 7.69% 按已落库决策轨迹节点统计。 统计窗口：2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z 扫描节点数 104 真实模型参与 8 确定性替身 0 模型未证实 0 降级 96 无法判定 0 输出不可解析 0 归因覆盖率 0.0833 证据截断 否 独立盲标校准 无盲标证据 冻结集无独立盲标配对样本，不得宣称模型已校准。 样本数 0 不可解析样本 0 分桶数 10 ECE —（无数据） Brier —（无数据） 样本下限 300 当前阈值 0.7 建议阈值 —（无数据） 建议阈值覆盖数 0 无盲标样本：ECE/Brier 为 null（不是 0），不得重定阈值 无盲标证据 冻结集无独立盲标配对样本，不得宣称模型已校准。 本状态下不渲染分桶/切片曲线。 放量档位 影子模式 无证据 放量比例 0 抽检数 0 差异数 0 错误率 —（无数据） 错误率目标 0.02 样本充分 否 下一档位 ASSISTED 可晋级 否 生效中 是 晋级阻断项 NO_EVIDENCE 回滚演练 回滚后不自动出金 演练确认回滚后自动出金被关闭。 当前档位 SHADOW 回滚目标 SHADOW 已到底层 是 完整性 完好 写操作次数 0 禁止项 5 provider 连通性探活 未测过 尚未点击探活；本端点每次真实消耗一次 provider 用量，不做自动轮询。",
      "markersHit": [
        "AI 治理",
        "能力就绪",
        "运行期模型参与",
        "独立盲标校准",
        "放量档位",
        "回滚演练"
      ],
      "probeRequestsOnLoad": 0,
      "probeRequestsAfterClick": 1,
      "windowSameSource": true,
      "expectedParticipation": {
        "uiRate": 0.0769,
        "label": "7.69%",
        "windowDeclared": "2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z"
      },
      "participationProbe": {
        "windowDeclared": true,
        "status": 200,
        "hasExpected": true,
        "forbiddenOnStage": false,
        "forbiddenFullRate": false,
        "rendered": "7.69%"
      }
    },
    "chain5xx": [],
    "external5xx": [
      {
        "status": 503,
        "url": "http://localhost:3000/api/v1/console/overview"
      },
      {
        "status": 503,
        "url": "http://localhost:3000/api/v1/console/quick-actions"
      },
      {
        "status": 503,
        "url": "http://localhost:3000/api/v1/notification/messages/unread-count"
      },
      {
        "status": 503,
        "url": "http://localhost:3000/api/v1/notification/messages/unread-count"
      }
    ]
  },
  "runtime": {
    "windowFrom": "2026-10-09T00:00:00Z",
    "windowTo": "2026-11-08T00:00:00Z",
    "evidenceWindowFrom": "2026-10-09T00:00:00Z",
    "evidenceWindowTo": "2026-11-08T00:00:00Z",
    "nodesScanned": 104,
    "aiParticipated": 8,
    "aiDegraded": 96,
    "participationRate": 0.0769,
    "capabilityMissing": 91,
    "capturedAt": "2026-11-07T03:06:38.994Z",
    "calibrationVerdict": {
      "baseline": "NO_EVIDENCE",
      "withMinSamplesOne": "NO_EVIDENCE",
      "effectiveFloorsAtBypass": [
        300,
        30
      ],
      "guardHeld": true
    },
    "rollbackDrill": {
      "currentStage": "SHADOW",
      "rollbackTargetStage": "SHADOW",
      "integrityIntact": true,
      "mutations": 0,
      "stageUnchanged": true
    },
    "readinessSnapshot": {
      "l3Claimable": "false",
      "gates": {
        "total": 12,
        "satisfied": 5,
        "blocked": 7,
        "runtimeOnly": 0
      },
      "alignedWithRuntime": true,
      "blockedGates": [
        "PROVIDER_CONFIGURED",
        "BASELINE_WIRED",
        "FROZEN_EVALSET_ACTIVE",
        "PERCEPTION_MODEL_DRIVEN",
        "CATALOG_ADMISSION_ACTIVE",
        "GOLIVE_NOT_DRAFT",
        "RUNTIME_MODEL_COVERAGE"
      ]
    },
    "probeVerdict": "NOT_CONFIGURED",
    "probeVerdictCapturedAt": "2026-11-07T03:06:50.646Z",
    "finalReadinessSnapshot": {
      "l3Claimable": "false",
      "coverageStatus": "BLOCKED",
      "coverageReason": "PROVIDER_NOT_CONFIGURED",
      "probeVerdict": "NOT_CONFIGURED",
      "probeVerdictCapturedAt": "2026-11-07T03:06:50.646Z"
    }
  },
  "consoleErrors": [
    "Failed to load resource: the server responded with a status of 503 (Service Unavailable)",
    "Failed to load resource: the server responded with a status of 503 (Service Unavailable)",
    "Failed to load resource: the server responded with a status of 503 (Service Unavailable)",
    "Failed to load resource: the server responded with a status of 503 (Service Unavailable)"
  ],
  "passed": true,
  "finishedAt": "2026-11-07T03:06:53.850Z"
}
```
