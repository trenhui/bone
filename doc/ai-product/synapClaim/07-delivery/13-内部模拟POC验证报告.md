---
doc_id: delivery.13
role: guide
currency: current
---

# 13 内部模拟 POC 验证报告

> 定位：回答「在把系统带去真实企业做 POC 之前，**内部**能不能先用可控的模拟 provider 把技术链路跑通并验证到什么程度」。本文是 12 号《POC 就绪度评估与启动清单》的**技术验证补充**——12 号判定「现在可进哪一态」，本文给出「内部模拟能把其中哪几项闭环、证据是什么、边界在哪里」。
>
> **一句话结论**：内部模拟 POC **技术验证通过**——3 项纯技术 BLOCKED（provider / 感知 llm / 运行期覆盖）在内部模拟 provider 上真实闭环，成熟度达 L2；但 `l3Claimable=false`、红线 2 项仍 BLOCKED。**这不等于真实企业 POC 通过，更不等于可投产**。

---

## 一、为什么做这次验证（目标与边界）

12 号文档把 5 个 BLOCKED 分成两类：

| 类别 | 判据 | 能否内部模拟闭环 |
|---|---|---|
| **纯技术接入**（可控） | `PROVIDER_CONFIGURED` / `PERCEPTION_MODEL_DRIVEN` / `RUNTIME_MODEL_COVERAGE` | ✅ 可以——用一个契约合规的模拟 provider + parse_mode=llm + 真实跑链路 |
| **跨团队签核**（红线） | `CATALOG_ADMISSION_ACTIVE`（资金护栏，业务法务）/ `GOLIVE_NOT_DRAFT`（合规转正，安全合规） | ❌ 不可以——模拟无法替代签核，伪造即刷绿 |

本次验证的目标被严格限定为：**只用模拟 provider 闭环前 3 项技术判据，证明"配好 provider 后链路真能通、模型真能上场"**；后 2 项保持 BLOCKED，绝不触碰。

这是业界最佳实践的 test double 分层：**连通性/契约类验证可用模拟件，资金/合规类结论必须真实**。模拟件必须可识别、可审计——本次 provider 在 HTTP 头与响应体都自报 `X-POC-Simulated: true`。

---

## 二、本次增强（可复用的工程能力）

为支撑内部模拟 POC，新增两个工具（均入库，可复现）：

| 工具 | 作用 | 关键设计 |
|---|---|---|
| `tools/internal-poc-provider.py` | OpenAI 兼容的内部模拟 provider（OpenAI 兼容 `POST /chat/completions`） | ① model=`poc-internal-v1`（非 `mock*`→判 PROVIDER，这是让探针可达的必要条件）；② content 为裸 JSON、**不含** `mock:true`（区别于 `mock-ai-gateway.py` 的降级验证用途）；③ 每响应带 `X-POC-Simulated` 头 + `x-poc-simulated` 字段，自证模拟身份；④ 按 prompt 自动分流 probe / document-parse / assessment 三场景；⑤ 业务评估**刻意不给 confidence**→转人工，不臆造自动通过 |
| `scripts/e2e/internal-poc-verify.py` | 内部模拟 POC 取证器 | ① 真实调后端探活 + 健康度端点，证据原样透传不编造；② **备份→写实测证据→跑复核器→finally 逐字恢复**历史产物，不篡改历史时点；③ 主判据用注入 provider 环境、self-test 用干净环境（两个互斥前提，见 11 号手册 §六）；④ 断言"3 技术项 SATISFIED + 2 红线 BLOCKED + l3Claimable=false + L2 + self-test PASS"全部成立才判 PASS |

**与现有 `mock-ai-gateway.py` 的关系**：二者互补、不可互替。后者自报 `mock:true` 让下游 fail-closed 拒绝，用于验证"降级诚实"；前者不自报 mock、返回契约合规响应，用于验证"链路可达 + 模型上场"。

---

## 三、实测结果（2026-11-23，采集 HEAD `a30c14d5`）

### 3.1 隔离栈与执行链路

```
internal-poc-provider(:18081)  ← OpenAI 兼容，X-POC-Simulated=true
        ↑ OpenAI 协议出站（parse_mode=llm）
claim(:8097) ── gateway(:18888) ── JWT(admin/123456)
        │
        ├─ GET /api/v1/claim-ops/ai-provider-probe   → verdict=REACHABLE + 证据束
        └─ GET /api/v1/claim-ops/ai-runtime-health   → 参与率证据
        ↓
scripts/e2e/internal-poc-verify.py → outputs/claims-e2e-result.internal-poc.json（本次专属产物）
        ↓
_ai_native_readiness.py（注入 provider 环境变量）→ 10 SAT / 2 BLOCKED / L2
```

判活判据：后端日志 `Started ClaimApplication in 4.382s` / `Started BoneGatewayApplication in 2.274s`（非端口 LISTEN、非 curl 200 之外迹象）。

### 3.2 探活实测（本次真实 HTTP 调用）

| 字段 | 实测值 | 说明 |
|---|---|---|
| `verdict` | `REACHABLE` | 链路可达且返回合法 JSON |
| `configured` / `reachable` | `true` / `true` | 配置态与连通态一致 |
| `model` | `poc-internal-v1` | 内部模拟模型名（**非真实商业模型**） |
| `provenance` | `PROVIDER` | 走真实 HTTP 出站契约（非进程内替身） |
| `promptTokens` / `completionTokens` | `64` / `24` | 非零，来自 provider `usage` 原样透传 |
| `requestId` | `poc-28c64ce5-8e70-4c73-8480-453901c261bc` | 合法 UUID，来自 provider 响应 `id` |

完整证据束自洽（`probe_evidence_valid()` 全条件通过），证据来自本次调用而非手写。

### 3.3 运行期参与率（历史窗口观测）

`nodes=148`、`aiParticipated=8`、`aiDegraded=140`、`participationRate=0.0541`、窗口 `2026-10-22T00:00:00Z ~ 2026-11-21T00:00:00Z`。这是该窗口的真实落库数据；本次 POC 未新增真实案件推理，参与率非零仅用于证明"模型确曾上场"这一判据条件。

### 3.4 复核器目标态（验证通过判据）

```
判据 12 项：SATISFIED 10 / BLOCKED 2 / RUNTIME_ONLY 0
当前可判定成熟度：L2（AI 承担部分主负载，仍待飞轮与 MRM）
l3Claimable = false
```

| 判据 | 状态 | 本次变化 |
|---|---|---|
| `PROVIDER_CONFIGURED` | SATISFIED | ✅ BLOCKED→SATISFIED（注入内部 provider） |
| `PERCEPTION_MODEL_DRIVEN` | SATISFIED | ✅ BLOCKED→SATISFIED（parse_mode=llm + provider） |
| `RUNTIME_MODEL_COVERAGE` | SATISFIED | ✅ BLOCKED→SATISFIED（本次探活证据束自洽 + 参与率非零） |
| `CATALOG_ADMISSION_ACTIVE` | **BLOCKED** | 🔒 红线不碰（资金护栏，业务法务签核） |
| `GOLIVE_NOT_DRAFT` | **BLOCKED** | 🔒 红线不碰（13 册合规文档 draft） |
| 其余 7 项 | SATISFIED | 基线已绿，本次未动 |

`--self-test`（干净环境）全 PASS；`l3Claimable=false`；成熟度 L2。取证器据此判定 **PASS**。

---

## 四、证据固化（可审计）

| 证据 | 位置 | 内容 |
|---|---|---|
| 本次 E2E/探活产物逐字快照 | `09-records/evidence/2026-11-23-internal-poc-claims-e2e-result.md` | mode=internal-simulated-poc，本次 probeEvidence（poc-internal-v1 / requestId），参与率，39 条 API 断言 |
| 复核器输出逐字快照 | `09-records/evidence/2026-11-23-internal-poc-readiness.md` | 10 SAT / 2 BLOCKED / L2 / l3Claimable=false |
| 本次专属产物（本机，gitignored） | `outputs/claims-e2e-result.internal-poc.json` | 供复现；历史基线 `outputs/claims-e2e-result.json` 未被篡改 |
| 复核器输出（本机） | `outputs/ai-native-readiness-internal-poc.txt` | 供复现 |

两份时点快照已登记在 `09-records/evidence/README.md`。

---

## 五、诚实边界（不得越线）

1. **这是内部模拟技术验证，不是真实企业 POC**。provider 是本机桩（`poc-internal-v1`），不是采购的商业模型；`provenance=PROVIDER` 只表示走了真实 HTTP 出站契约，不代表模型判断准确。
2. **不宣称 L3 / 可对客出结论 / 可自动放款**。`l3Claimable=false`；业务评估桩刻意不给 confidence，案件仍转人工；图像通道默认关闭，视觉 `available=true` 金闸未打开。
3. **红线 2 项未被模拟闭环**。目录准入资金护栏、Go-Live 合规仍需业务/法务/安全合规跨团队签核；本次未改任何治理资产 status、frontmatter、默认值或判据。
4. **历史证据未篡改**。取证器 finally 逐字恢复历史产物；新增证据以独立文件 + 时点快照方式登记。

---

## 六、与真实企业 POC 的差距（下一步）

本次把"技术链路可达"的内部不确定性清零，但真实企业 POC 仍差：

| 差距 | owner | 解锁动作 |
|---|---|---|
| 采购真实商业 provider + 密钥托管注入 | 平台 / 交付 | 配 `BONE_AI_GATEWAY_BASE_URL/API_KEY/MODEL` 为真实值，按 11 号手册探活 REACHABLE |
| parse_mode=llm 在真实 provider 上的准确率复现 | 平台 / 业务 | 用客户授权脱敏样本现场生成字段准确率 ≥95%、规则匹配 ≥90%，不预置实测值 |
| 多模态影像件（视觉 `available`）真实跑通 | 平台 / 架构 | 接支持视觉的端点，按 D2 归因码验证；默认仍关，需双人 Review + 产品签核才开 |
| 目录准入资金护栏 | 业务 / 法务 | 目录数据接入 + 策略 MANDATORY + 签核，`CATALOG_ADMISSION_ACTIVE` 方可翻转 |
| 13 册合规文档转正 | 安全合规 | PIA/DPIA、等保、算法备案逐册转正并配验收证据 |

**Shadow 技术 POC 现在可进（12 号结论不变）**；本次内部模拟 POC 进一步证明"Assisted 态的 3 项技术条件在配好真实 provider 后即可达成"，但 Assisted 对客出建议仍须真实 provider + 现场指标验收。

---

## 七、复现步骤

```bash
# 1) 起内部 provider（独立终端）
python3 tools/internal-poc-provider.py --port 18081

# 2) 起 claim(:8097) + gateway(:18888)，注入环境变量：
#    BONE_AI_GATEWAY_BASE_URL=http://127.0.0.1:18081/v1
#    BONE_AI_GATEWAY_API_KEY=internal-poc-key
#    BONE_AI_GATEWAY_MODEL=poc-internal-v1
#    BONE_CLAIM_DOCUMENT_PARSE_MODE=llm
#    （IAM:8081 / MySQL:3306 / Redis:6379 复用现有）

# 3) 登录网关拿 JWT → 写入 /tmp/poc-token
curl -sS -X POST http://127.0.0.1:18888/api/v1/iam/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' \
  | python3 -c "import sys,json;open('/tmp/poc-token','w').write(json.load(sys.stdin)['data']['token'])"

# 4) 跑取证器（断言目标态）
python3 scripts/e2e/internal-poc-verify.py \
  --gateway http://127.0.0.1:18888 --token-file /tmp/poc-token \
  --provider-base-url http://127.0.0.1:18081/v1 --provider-model poc-internal-v1
# 预期输出：=== 内部模拟 POC 验证 PASS ===，exit 0
```

---

*本文为内部模拟 POC 技术验证报告。数字口径以本次取证器实测与复核器输出为准；任何对外沟通须回到 12 号三态口径，不得据本文宣称真实企业 POC 通过或系统可投产。*
