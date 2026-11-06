---
change: synapclaim-ai-native-closeout
design-doc: docs/superpowers/specs/2026-11-05-synapclaim-ai-native-closeout-design.md
base-ref: 6987054002fdd337bfcc322daa7cb2f5bf8a2629
---

# synapClaim AI 原生收口 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 synapClaim 全部可编码 AI 缺口——学习层校准生产消费方（翻转 FLYWHEEL_DOWNSTREAM）、感知层图像解析（最小网关多模态扩展）、知识层受控 RAG 组合适配器、复核器 IMAGE_PARSE_WIRED/RAG_WIRED 判据扩展、E2E 增量断言——外部前置（provider/GPU、≥300 盲标票据、医学语料、验真通道）如实登记，接受 `l3Claimable=false` 的诚实结论。

**Architecture:** 全部为增量代码 + 默认关闭门控（`bone.claim.document-parse.image.enabled` / `bone.claim.knowledge.rag.enabled` 默认 false），无 DDL、无数据迁移、决策图五节点 DAG 拓扑不变。校准消费方为 application 层只读服务，直接调用域内核 `AiConfidenceCalibration` 静态方法（复核器 `calibration_wired` 扫非测试代码中的 `AiConfidenceCalibration.` 静态引用）；图像通道走 `DocumentParsePort` default 方法扩展（既有适配器零改动）；RAG 走组合适配器（目录准入逻辑不变 + 门控语义支路）。

**Tech Stack:** Java 17 / Spring Boot 3.5（bone-parent 1.0.0）、bone-metadata-sdk（唯一持久化，自定义 SQL 按文件名约定绑定）、bone-ai-gateway（OpenAI 兼容协议自研客户端）、bone-ai-rag（受控检索：RRF + 引用强制）、JUnit 5 + Mockito + AssertJ、Python 3 复核器（负向探针自测）、Playwright E2E（Node CJS）。

## 设计文档与任务对位

设计文档：`docs/superpowers/specs/2026-11-05-synapclaim-ai-native-closeout-design.md`（D1–D5）。
OpenSpec 任务边界：`openspec/changes/synapclaim-ai-native-closeout/tasks.md`（5 组 13 项）。

| tasks.md 条目 | 本计划任务 |
|---|---|
| 1.1 校准计算服务 | 任务 1 + 任务 2（域内核已存在，本计划落生产消费方） |
| 1.2 champion-challenger 离线对比 | 任务 3 |
| 1.3 校准消费链接 + FLYWHEEL_DOWNSTREAM 翻转 | 任务 2 |
| 2.1 图像多模态解析适配器 | 任务 4 + 任务 5 |
| 2.2 门控与降级 | 任务 5 |
| 2.3 运行态参与口径 | 任务 6 |
| 3.1 组合型 KnowledgeRetrievalPort 适配器 | 任务 7 |
| 3.2 KnowledgeBasis 二级归因 | 任务 7 |
| 4.1 复核器判据扩展 + --self-test | 任务 8 |
| 4.2 AI技术方案.md 回写 | 任务 9 |
| 4.3 复核器主判据终跑存档 | 任务 9 |
| 5.1 E2E 扩展 | 任务 10 |
| 5.2 全量质量门禁 | 任务 11 |
| 5.3 循环复核收口 | 任务 11 |

## Global Constraints（每个任务隐含遵守）

- **AI 不碰钱**：校准报表只读，绝不自动改在线阈值/开关/放款配置；图像失败转人工，禁止文本层兜底冒充。
- **拓扑不变**：不扩决策图 DAG（五节点不变）；校准是评测侧离线计算，不进决策图。
- **无 DDL**：不新增/修改任何 `V*__*.sql` 迁移文件；新仓储查询只加 SQL 资源文件（非迁移）。
- **持久化唯一**：只用 bone-metadata-sdk；禁 MyBatis-Plus/JPA；禁 `SELECT ... FOR UPDATE`。
- **统一响应**：Controller 返回 `ApiResponse<T>`（HC-003）；Controller 禁直注 domain/service、domain/repository、infrastructure。
- **默认配置不变现网行为**：两个新门控 `bone.claim.document-parse.image.enabled`、`bone.claim.knowledge.rag.enabled` 默认 false。注意：tasks.md 2.2 写的是 `bone.claim.document.parse.image.enabled`（全点号），设计文档 D2 与现有 yml 命名（`bone.claim.document-parse.mode`）一致用连字符形式；**以设计文档为准**（连字符），`@ConditionalOnProperty`、application.yml、复核器判据三处必须用同一键。
- **格式**：每个改 Java 的任务结束后执行 `mvn spotless:apply`。
- **分支纪律**：禁止建/切分支，在仓库当前 checkout 分支工作；每次 commit 前 `git branch --show-current` 确认落点；逐文件 `git add`，不用 `git add -A`。
- **L3 提示**：任务 7 的 pom 依赖变更（新增 bone-ai-rag）按 AGENTS.md 自主权属 L3（依赖需架构师审批）——执行该任务前先向用户确认，确认后按本计划步骤实施。
- **commit 规范**：`type(scope): description`（type: feat|fix|test|docs|chore；scope: synap-claim / bone-ai-gateway / e2e / readiness 等）。

## 文件结构总览

| 任务 | Create | Modify |
|---|---|---|
| 1 | `bone-apps/synap-claim/src/main/resources/sql/com/bone/claim/domain/repository/BlindLabelTaskRepository/findUnblindedPaired.sql`；`src/main/java/com/bone/claim/application/BlindLabelCalibrationSamples.java` | `src/main/java/com/bone/claim/domain/repository/BlindLabelTaskRepository.java` |
| 2 | `src/main/java/com/bone/claim/application/AiCalibrationReportApplicationService.java`；`src/main/java/com/bone/claim/adapter/web/controller/AiCalibrationReportController.java`；两个测试类 | — |
| 3 | `src/main/java/com/bone/claim/application/ChampionChallengerComparator.java` + 测试 | — |
| 4 | `bone-ai/bone-ai-gateway/src/test/java/com/bone/ai/gateway/infrastructure/OpenAiCompatibleClientMultimodalTest.java` | `bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/domain/LlmMessage.java`；`.../infrastructure/OpenAiCompatibleClient.java` |
| 5 | `src/main/java/com/bone/claim/infrastructure/document/ImageDocumentParsePortAdapter.java` + 测试 | `src/main/java/com/bone/claim/application/port/out/DocumentParsePort.java`；`src/main/resources/application.yml` |
| 6 | `src/test/java/com/bone/claim/application/EvidenceImageParsingTest.java` | `src/main/java/com/bone/claim/application/EvidenceApplicationService.java` |
| 7 | `src/main/java/com/bone/claim/infrastructure/knowledge/RagKnowledgeRetrievalPortAdapter.java`；`src/main/java/com/bone/claim/infrastructure/knowledge/ClaimRagScanConfiguration.java`；测试 | `bone-apps/synap-claim/pom.xml`；`src/main/java/com/bone/claim/domain/model/decision/KnowledgeBasis.java`；`src/main/resources/application.yml` |
| 8 | — | `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` |
| 9 | `outputs/ai-native-readiness-2026-11-05.txt`（产物） | `doc/ai-product/synapClaim/03-contract/AI技术方案.md` |
| 10 | — | `bone-frontend/e2e/synap-claim-e2e.cjs` |
| 11 | — | — |

---

### Task 1: 盲标校准样本源——仓储查询与样本映射纯函数

**Files:**
- Modify: `bone-apps/synap-claim/src/main/java/com/bone/claim/domain/repository/BlindLabelTaskRepository.java`
- Create: `bone-apps/synap-claim/src/main/resources/sql/com/bone/claim/domain/repository/BlindLabelTaskRepository/findUnblindedPaired.sql`
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/application/BlindLabelCalibrationSamples.java`
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/application/BlindLabelCalibrationSamplesTest.java`

**Interfaces:**
- Consumes: `BlindLabelTask`（Lombok `@Getter`：`getAiSuggestionJson()` / `getLabelResultJson()` / `getLabelPayloadJson()`）；`AiConfidenceCalibration.CalibrationSample(confidence, correct, sliceKey, source, frozenSetExcluded)`（record，confidence 须在 [0,1]，sliceKey 非空，source=BLIND_LABEL，frozenSetExcluded=true）。
- Produces: `BlindLabelTaskRepository.findUnblindedPaired(int limit)` 返回 `List<BlindLabelTask>`（status='UNBLINDED' AND agreement='PAIRED'，按 unblinded_at 倒序，上限 :limit）；`BlindLabelCalibrationSamples.from(List<BlindLabelTask>, ObjectMapper)` 返回 `Mapping(List<CalibrationSample> samples, int unparseableSamples)`（包级可见，供 Task 2 的 application 服务消费）。

- [x] **Step 1: 在 BlindLabelTaskRepository 增加查询方法**

在 `findRecentByAssignee` 方法后追加（import 已有 `List`/`@Param`，无需新增 import）：

```java
  /**
   * 查「已解盲且成对样本已产出」的任务（G4-1 数据飞轮下游校准样本源）。
   *
   * <p>校准只认独立盲标金标准（解盲后 aiSuggestionJson + labelResultJson 成对快照）；
   * 上界经 :limit 显式传入（服务层钳制到 [1,500]），不是全量导出。
   */
  List<BlindLabelTask> findUnblindedPaired(@Param("limit") int limit);
```

- [x] **Step 2: 创建 SQL 资源文件（自定义 SQL 按文件名约定绑定，非 DDL 迁移）**

创建 `findUnblindedPaired.sql`（与同目录 `findRecentByAssignee.sql` 同构：首行 SELECT、含 `/*bone:tenant*/` 租户标记、`deleted = 0` 软删过滤）：

```sql
SELECT
       id,
       task_no,
       claim_id,
       assignee_id,
       status,
       label_payload_json,
       payload_hash,
       label_result_json,
       submitted_at,
       ai_suggestion_json,
       agreement,
       unblinded_at,
       tenant_id,
       created_at,
       updated_at,
       version
FROM t_claim_blind_label_task
WHERE status = 'UNBLINDED'
  AND agreement = 'PAIRED'
  AND /*bone:tenant*/
  AND deleted = 0
ORDER BY unblinded_at DESC
LIMIT :limit
```

- [x] **Step 3: 写失败测试（样本映射纯函数：正常 / channel 切片 / 解析失败计数 / 空入参）**

创建 `BlindLabelCalibrationSamplesTest.java`：

```java
package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.LabelSource;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 盲标 → 校准样本映射（纯函数）：解析失败计数不编造，channel 切片缺省 GLOBAL。 */
class BlindLabelCalibrationSamplesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static BlindLabelTask task(String payload, String result, String aiSnapshot) {
    BlindLabelTask t = BlindLabelTask.open(1L, 42L, "BL-1", 9001L, "annotator-1",
        payload, "a".repeat(64));
    t.submit(result, Instant.now());
    t.unblind(aiSnapshot, BlindLabelTask.Agreement.PAIRED, Instant.now());
    return t;
  }

  @Test
  @DisplayName("正常成对样本：confidence/结论/channelslice/冻结集隔离标记齐备")
  void normalSample() {
    BlindLabelTask t = task(
        "{\"claimNo\":\"C-1\",\"channel\":\"AUTO\"}",
        "{\"decision\":\"APPROVE\"}",
        "{\"aiConfidence\":0.82,\"decisionResult\":\"APPROVE\"}");

    BlindLabelCalibrationSamples.Mapping m =
        BlindLabelCalibrationSamples.from(List.of(t), MAPPER);

    assertThat(m.unparseableSamples()).isZero();
    assertThat(m.samples()).hasSize(1);
    CalibrationSample s = m.samples().get(0);
    assertThat(s.confidence().doubleValue()).isEqualTo(0.82);
    assertThat(s.correct()).isTrue();
    assertThat(s.sliceKey()).isEqualTo("AUTO");
    assertThat(s.source()).isEqualTo(LabelSource.BLIND_LABEL);
    assertThat(s.frozenSetExcluded()).isTrue();
  }

  @Test
  @DisplayName("结论不一致 correct=false；无 channel 切片回退 GLOBAL")
  void disagreeAndGlobalSlice() {
    BlindLabelTask t = task(
        "{\"claimNo\":\"C-2\"}",
        "{\"decision\":\"REVIEW\"}",
        "{\"aiConfidence\":0.90,\"decisionResult\":\"APPROVE\"}");

    BlindLabelCalibrationSamples.Mapping m =
        BlindLabelCalibrationSamples.from(List.of(t), MAPPER);

    assertThat(m.samples()).hasSize(1);
    assertThat(m.samples().get(0).correct()).isFalse();
    assertThat(m.samples().get(0).sliceKey()).isEqualTo("GLOBAL");
  }

  @Test
  @DisplayName("解析失败逐条计数：坏 JSON / 缺 aiConfidence / 缺人工结论，绝不编造数值")
  void unparseableCounted() {
    List<BlindLabelTask> tasks = List.of(
        task("{\"claimNo\":\"C-3\"}", "{\"decision\":\"APPROVE\"}", "not-json"),
        task("{\"claimNo\":\"C-4\"}", "{\"decision\":\"APPROVE\"}",
            "{\"decisionResult\":\"APPROVE\"}"),
        task("{\"claimNo\":\"C-5\"}", "{\"other\":1}",
            "{\"aiConfidence\":0.5,\"decisionResult\":\"APPROVE\"}"));

    BlindLabelCalibrationSamples.Mapping m =
        BlindLabelCalibrationSamples.from(tasks, MAPPER);

    assertThat(m.samples()).isEmpty();
    assertThat(m.unparseableSamples()).isEqualTo(3);
  }

  @Test
  @DisplayName("confidence 越界 [0,1] 计入 unparseable；空列表产出空映射")
  void outOfRangeAndEmpty() {
    BlindLabelTask bad = task("{\"claimNo\":\"C-6\"}", "{\"decision\":\"APPROVE\"}",
        "{\"aiConfidence\":1.5,\"decisionResult\":\"APPROVE\"}");

    assertThat(BlindLabelCalibrationSamples.from(List.of(bad), MAPPER).unparseableSamples())
        .isEqualTo(1);
    assertThat(BlindLabelCalibrationSamples.from(List.of(), MAPPER).samples()).isEmpty();
  }
}
```

- [x] **Step 4: 写最小实现**

创建 `BlindLabelCalibrationSamples.java`：

```java
package com.bone.claim.application;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.LabelSource;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 盲标任务 → 校准样本映射（纯函数，无 IO、无状态）。
 *
 * <p>防假绿口径：解析失败计入 {@code unparseableSamples}，绝不编造置信度或结论；
 * sliceKey 取派发载荷 {@code labelPayloadJson.channel}（渠道切片），缺失回退 GLOBAL。
 * 冻结集隔离恒为 true：校准样本只用于观测与阈值建议，冻结集不得参与阈值拟合。
 */
final class BlindLabelCalibrationSamples {

  static final String SLICE_GLOBAL = "GLOBAL";

  private BlindLabelCalibrationSamples() {}

  /** 映射产物：可用的校准样本 + 解析失败计数（失败样本直接丢弃，不补值）。 */
  record Mapping(List<CalibrationSample> samples, int unparseableSamples) {}

  static Mapping from(List<BlindLabelTask> tasks, ObjectMapper mapper) {
    List<CalibrationSample> samples = new ArrayList<>();
    int unparseable = 0;
    for (BlindLabelTask task : tasks) {
      CalibrationSample sample = toSample(task, mapper);
      if (sample == null) {
        unparseable++;
      } else {
        samples.add(sample);
      }
    }
    return new Mapping(List.copyOf(samples), unparseable);
  }

  private static CalibrationSample toSample(BlindLabelTask task, ObjectMapper mapper) {
    JsonNode ai = read(mapper, task.getAiSuggestionJson());
    JsonNode label = read(mapper, task.getLabelResultJson());
    if (ai == null || label == null) {
      return null;
    }
    JsonNode confidenceNode = ai.path("aiConfidence");
    String decisionResult = ai.path("decisionResult").asText("");
    String humanDecision = label.path("decision").asText("");
    if (!confidenceNode.isNumber() || decisionResult.isBlank() || humanDecision.isBlank()) {
      return null;
    }
    BigDecimal confidence = BigDecimal.valueOf(confidenceNode.asDouble());
    if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      return null;
    }
    String channel = read(mapper, task.getLabelPayloadJson()).path("channel").asText("");
    String sliceKey = channel.isBlank() ? SLICE_GLOBAL : channel;
    return new CalibrationSample(
        confidence,
        decisionResult.equalsIgnoreCase(humanDecision),
        sliceKey,
        LabelSource.BLIND_LABEL,
        true);
  }

  private static JsonNode read(ObjectMapper mapper, String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(json);
    } catch (Exception ex) {
      return null;
    }
  }
}
```

- [x] **Step 5: 运行测试确认通过**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=BlindLabelCalibrationSamplesTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`（BUILD SUCCESS）。

- [x] **Step 6: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current   # 确认落点，再逐文件 add
git add bone-apps/synap-claim/src/main/java/com/bone/claim/domain/repository/BlindLabelTaskRepository.java \
  bone-apps/synap-claim/src/main/resources/sql/com/bone/claim/domain/repository/BlindLabelTaskRepository/findUnblindedPaired.sql \
  bone-apps/synap-claim/src/main/java/com/bone/claim/application/BlindLabelCalibrationSamples.java \
  bone-apps/synap-claim/src/test/java/com/bone/claim/application/BlindLabelCalibrationSamplesTest.java
git commit -m "feat(synap-claim): 盲标校准样本源——findUnblindedPaired 查询与样本映射纯函数"
```

---

### Task 2: 校准报表应用服务与只读端点（翻转 FLYWHEEL_DOWNSTREAM 的生产消费方）

**Files:**
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/application/AiCalibrationReportApplicationService.java`
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/adapter/web/controller/AiCalibrationReportController.java`
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/application/AiCalibrationReportApplicationServiceTest.java`

**Interfaces:**
- Consumes: `BlindLabelTaskRepository.findUnblindedPaired(int limit)`（Task 1）；`BlindLabelCalibrationSamples.from(...)`（Task 1，同包）；`AiConfidenceCalibration.evaluate(samples, currentThreshold, targetErrorRate, minSamples, minSamplesPerSlice)` 返回 `CalibrationReport`（域内核，已存在）。
- Produces: `AiCalibrationReportApplicationService.report(Integer minSamples, Integer minSamplesPerSlice)` 返回 `AiCalibrationReport(requestedLimit, fetchedTasks, sampleCount, unparseableSamples, calibration)`；常量 `MAX_LIMIT=500`、`DEFAULT_MIN_SAMPLES=300`、`DEFAULT_MIN_SAMPLES_PER_SLICE=30`。端点 `GET /api/v1/claim-ops/ai-calibration/report`（权限复用 `claim:claims:read`）。**本类是非测试代码中第一处 `AiConfidenceCalibration.evaluate(` 静态引用——复核器 calibration_wired 由此翻转。**

- [x] **Step 1: 写失败测试（空样本 / 样本不足 / 报告产出后零写操作 / 解析失败计数透传）**

创建 `AiCalibrationReportApplicationServiceTest.java`：

```java
package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.Verdict;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 校准报表应用服务：只读消费盲标成对样本；样本不足给 INSUFFICIENT_SAMPLES 不编造数值。 */
class AiCalibrationReportApplicationServiceTest {

  private BlindLabelTaskRepository repository;
  private AiCalibrationReportApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(BlindLabelTaskRepository.class);
    service = new AiCalibrationReportApplicationService(repository);
  }

  private static BlindLabelTask paired(double confidence, String aiDecision, String human) {
    BlindLabelTask t = BlindLabelTask.open(1L, 42L, "BL-1", 9001L, "annotator-1",
        "{\"claimNo\":\"C-1\",\"channel\":\"AUTO\"}", "a".repeat(64));
    t.submit("{\"decision\":\"" + human + "\"}", Instant.now());
    t.unblind("{\"aiConfidence\":" + confidence + ",\"decisionResult\":\"" + aiDecision + "\"}",
        BlindLabelTask.Agreement.PAIRED, Instant.now());
    return t;
  }

  @Test
  @DisplayName("无样本：NO_EVIDENCE，ECE/Brier 为 null（不是 0）")
  void noEvidence() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of());

    var report = service.report(null, null);

    assertThat(report.fetchedTasks()).isZero();
    assertThat(report.calibration().verdict()).isEqualTo(Verdict.NO_EVIDENCE);
    assertThat(report.calibration().expectedCalibrationError()).isNull();
    assertThat(report.calibration().brierScore()).isNull();
  }

  @Test
  @DisplayName("样本不足：INSUFFICIENT_SAMPLES，观测值保留但禁止重定阈值")
  void insufficientSamples() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of(paired(0.9, "APPROVE", "APPROVE")));

    var report = service.report(null, null);

    assertThat(report.calibration().verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLES);
    assertThat(report.calibration().recommendedThreshold()).isNull();
    assertThat(report.calibration().brierScore()).isNotNull();
    assertThat(report.calibration().permitsRetune()).isFalse();
  }

  @Test
  @DisplayName("minSamples/minSamplesPerSlice 为请求参数缺省（null → 300/30），显式值生效")
  void thresholdsAsRequestParams() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of(paired(0.9, "APPROVE", "APPROVE")));

    assertThat(service.report(null, null).calibration().verdict())
        .isEqualTo(Verdict.INSUFFICIENT_SAMPLES);
    assertThat(service.report(1, 1).calibration().verdict())
        .isEqualTo(Verdict.SLICE_INCOMPLETE);  // 总量够但 AUTO 切片 1 条 < perSlice=2
    assertThat(service.report(1, 1).calibration().sampleCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("报告产出后零写操作：仓储只有一次读，绝不改在线配置（AI 不碰钱）")
  void readOnlyAfterReport() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of());

    service.report(null, null);

    verify(repository).findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT);
    verifyNoMoreInteractions(repository);
  }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=AiCalibrationReportApplicationServiceTest`
Expected: 编译失败 `cannot find symbol: class AiCalibrationReportApplicationService`。

- [x] **Step 3: 写应用服务实现（唯一的生产消费方：`AiConfidenceCalibration.` 静态引用）**

创建 `AiCalibrationReportApplicationService.java`：

```java
package com.bone.claim.application;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 置信度校准报表（G4-1 数据飞轮下游的生产消费方，只读）。
 *
 * <p>此前域内核 {@link AiConfidenceCalibration} 只有单测引用、没有生产消费方——
 * 「阈值由数据决定」停在文字上。本服务把 UNBLINDED+PAIRED 盲标任务映射成校准样本，
 * 产出 ECE/Brier/切片明细与阈值建议：结论仅供人工决策（minSamples=300/perSlice=30
 * 为请求参数缺省），绝不自动改任何在线阈值/开关（AI 不碰钱）。
 *
 * <p>组件边界：解析与样本映射是纯函数（{@link BlindLabelCalibrationSamples}）；
 * 仓储查询是唯一 IO；本服务无事务写。样本不足输出 INSUFFICIENT_SAMPLES 不编造数值。
 */
@Service
@RequiredArgsConstructor
public class AiCalibrationReportApplicationService {

  /** 单次取样的任务数上限（SQL :limit 的钳制上界，防全量导出）。 */
  public static final int MAX_LIMIT = 500;

  static final BigDecimal DEFAULT_CURRENT_THRESHOLD = new BigDecimal("0.70");
  static final BigDecimal DEFAULT_TARGET_ERROR_RATE = new BigDecimal("0.05");
  static final int DEFAULT_MIN_SAMPLES = 300;
  static final int DEFAULT_MIN_SAMPLES_PER_SLICE = 30;

  private final BlindLabelTaskRepository blindLabelTaskRepository;
  private final ObjectMapper mapper = new ObjectMapper();

  /** 校准报表（{@code calibration} 为域内核报告原样透传，字段语义见域内核 javadoc）。 */
  public record AiCalibrationReport(
      int requestedLimit,
      int fetchedTasks,
      int sampleCount,
      int unparseableSamples,
      AiConfidenceCalibration.CalibrationReport calibration) {}

  @Transactional(readOnly = true)
  public AiCalibrationReport report(Integer minSamples, Integer minSamplesPerSlice) {
    List<BlindLabelTask> tasks =
        blindLabelTaskRepository.findUnblindedPaired(MAX_LIMIT);
    BlindLabelCalibrationSamples.Mapping mapping =
        BlindLabelCalibrationSamples.from(tasks, mapper);
    AiConfidenceCalibration.CalibrationReport calibration =
        AiConfidenceCalibration.evaluate(
            mapping.samples(),
            DEFAULT_CURRENT_THRESHOLD,
            DEFAULT_TARGET_ERROR_RATE,
            minSamples == null ? DEFAULT_MIN_SAMPLES : minSamples,
            minSamplesPerSlice == null ? DEFAULT_MIN_SAMPLES_PER_SLICE : minSamplesPerSlice);
    return new AiCalibrationReport(
        MAX_LIMIT, tasks.size(), calibration.sampleCount(), mapping.unparseableSamples(),
        calibration);
  }
}
```

- [x] **Step 4: 写只读端点 Controller（复用 AiCapabilityReadinessController 的风格与权限点）**

创建 `AiCalibrationReportController.java`：

```java
package com.bone.claim.adapter.web.controller;

import com.bone.claim.application.AiCalibrationReportApplicationService;
import com.bone.claim.application.AiCalibrationReportApplicationService.AiCalibrationReport;
import com.bone.core.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 校准报表只读端点（G4-1 数据飞轮下游）。
 *
 * <p>与 {@link AiCapabilityReadinessController} 同一治理形态：无请求体、无路径参数、不调用能力
 * 业务方法、不改开关——查一次不会触发任何阈值变更。verdict 为受控枚举
 * （NO_EVIDENCE/UNTRUSTED_EVIDENCE/INSUFFICIENT_SAMPLES/SLICE_INCOMPLETE/NO_SAFE_THRESHOLD/
 * THRESHOLD_PROPOSED），样本不足时 ECE/Brier 为 null（不是 0），绝不编造数值。
 */
@Tag(name = "智能理赔-AI治理", description = "置信度校准报表（只读，供人工阈值决策参考）")
@RestController
@RequestMapping("/api/v1/claim-ops")
@RequiredArgsConstructor
public class AiCalibrationReportController {

  private final AiCalibrationReportApplicationService aiCalibrationReportService;

  @Operation(
      summary = "AI 置信度校准报表（只读）",
      description =
          "基于独立盲标成对样本计算 ECE/Brier 与阈值建议。仅为建议，不自动生效；"
              + "样本不足时 verdict=INSUFFICIENT_SAMPLES 且不产数值性结论。")
  @PreAuthorize("hasAuthority('claim:claims:read')")
  @GetMapping("/ai-calibration/report")
  public ApiResponse<AiCalibrationReport> report(
      @RequestParam(required = false) Integer minSamples,
      @RequestParam(required = false) Integer minSamplesPerSlice) {
    return ApiResponse.success(aiCalibrationReportService.report(minSamples, minSamplesPerSlice));
  }
}
```

- [x] **Step 5: 运行测试与整体模块回归**

Run: `mvn -pl bone-apps/synap-claim test -Dtest='AiCalibrationReportApplicationServiceTest,BlindLabelCalibrationSamplesTest'`
Expected: `Tests run: 8, Failures: 0, Errors: 0`。

Run: `mvn -pl bone-apps/synap-claim test`
Expected: BUILD SUCCESS（既有 ArchUnit 分层测试 / SqlTemplateFirstLineTest / EntityTableDdlCoverageTest 全绿——新 SQL 文件不涉及 DDL 与实体变更，应直接通过）。

- [x] **Step 6: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-apps/synap-claim/src/main/java/com/bone/claim/application/AiCalibrationReportApplicationService.java \
  bone-apps/synap-claim/src/main/java/com/bone/claim/adapter/web/controller/AiCalibrationReportController.java \
  bone-apps/synap-claim/src/test/java/com/bone/claim/application/AiCalibrationReportApplicationServiceTest.java
git commit -m "feat(synap-claim): 校准报表应用服务与只读端点——AiConfidenceCalibration 生产消费方"
```

---

### Task 3: champion-challenger 离线对比评测（缺金标仅产差异清单）

**Files:**
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/application/ChampionChallengerComparator.java`
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/application/ChampionChallengerComparatorTest.java`

**Interfaces:**
- Consumes: 冻结集 `bone-apps/synap-claim/eval/ai-decision-evalset.json` 的形状（caseKey + decisionResult + 金标可选）。输入为两段 JSON 字符串（champion 快照 / challenger 运行产物），由离线 runner 提供——冻结集文件不进 classpath，比较器保持纯函数（evalset 只读、不回流训练）。
- Produces: `ChampionChallengerComparator.compare(String championJson, String challengerJson, ObjectMapper mapper)` 返回 `Comparison(totalCases, comparedCases, missingGold, disagreeCount, List<CaseDiff> diffs, conclusion, reason)`；`CaseDiff(caseKey, goldPresent, agree, reason)`。`conclusion` 仅在两侧金标齐备时给一致率汇总文本，缺金标时为 null + reason=`GOLD_MISSING`（不产优劣结论）。

- [x] **Step 1: 写失败测试（有金标 / 无金标 / 坏 JSON）**

创建 `ChampionChallengerComparatorTest.java`：

```java
package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** champion / challenger 逐案离线对比：缺金标只产差异清单，不产优劣结论。 */
class ChampionChallengerComparatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("金标齐备：逐案 agree 判定 + 一致率汇总结论")
  void withGold() {
    String champion =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"REVIEW\"}]";
    String challenger =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"REVIEW\",\"goldDecision\":\"REVIEW\"}]";

    var c = ChampionChallengerComparator.compare(champion, challenger, MAPPER);

    assertThat(c.totalCases()).isEqualTo(2);
    assertThat(c.missingGold()).isZero();
    assertThat(c.comparedCases()).isEqualTo(2);
    assertThat(c.disagreeCount()).isZero();
    assertThat(c.diffs()).allMatch(ChampionChallengerComparator.CaseDiff::goldPresent);
    assertThat(c.conclusion()).contains("100.00%");
  }

  @Test
  @DisplayName("缺金标：差异清单逐案给 NO_GOLD，conclusion=null 不产优劣结论")
  void missingGold() {
    String champion =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\"}]";
    String challenger =
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"REVIEW\",\"goldDecision\":\"APPROVE\"},"
            + "{\"caseKey\":\"C-2\",\"decisionResult\":\"APPROVE\"}]";

    var c = ChampionChallengerComparator.compare(champion, challenger, MAPPER);

    assertThat(c.missingGold()).isEqualTo(1);
    assertThat(c.comparedCases()).isEqualTo(1);
    assertThat(c.disagreeCount()).isEqualTo(1);
    assertThat(c.conclusion()).isNull();
    assertThat(c.reason()).isEqualTo("GOLD_MISSING");
    assertThat(c.diffs())
        .anySatisfy(d -> {
          assertThat(d.caseKey()).isEqualTo("C-2");
          assertThat(d.reason()).isEqualTo("NO_GOLD");
        });
  }

  @Test
  @DisplayName("坏 JSON / 两侧案件键不对齐：不猜不编，如实计数")
  void malformedAndUnaligned() {
    var bad = ChampionChallengerComparator.compare("not-json", "[]", MAPPER);
    assertThat(bad.reason()).isEqualTo("CHAMPION_UNPARSEABLE");
    assertThat(bad.totalCases()).isZero();

    var unaligned = ChampionChallengerComparator.compare(
        "[{\"caseKey\":\"C-1\",\"decisionResult\":\"APPROVE\",\"goldDecision\":\"APPROVE\"}]",
        "[]", MAPPER);
    assertThat(unaligned.reason()).isEqualTo("CASE_KEY_MISMATCH");
    assertThat(unaligned.comparedCases()).isZero();
  }
}
```

- [x] **Step 2: 运行确认失败**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=ChampionChallengerComparatorTest`
Expected: 编译失败 `cannot find symbol: class ChampionChallengerComparator`。

- [x] **Step 3: 写实现**

创建 `ChampionChallengerComparator.java`：

```java
package com.bone.claim.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * champion / challenger 逐案离线对比（纯函数，冻结集口径）。
 *
 * <p>输入为 champion 快照（冻结集 eval/ai-decision-evalset.json 的导出形状）与 challenger
 * 运行产物两段 JSON 数组（元素含 caseKey / decisionResult / 可选 goldDecision）。
 * 逐案对比 + 汇总一致率；<b>缺金标只产差异清单（reason=NO_GOLD），绝不产优劣结论</b>——
 * 金标不齐时任何「challenger 更优」的说法都是把偶然当规律。晋升判定走域内核
 * challengerAdmissible 全链判据，本类不做。
 */
public final class ChampionChallengerComparator {

  private ChampionChallengerComparator() {}

  /** 单案差异：goldPresent=false 时 agree 无意义（一律 false），reason=NO_GOLD。 */
  public record CaseDiff(String caseKey, boolean goldPresent, boolean agree, String reason) {}

  /**
   * 对比汇总：conclusion 仅在金标齐备时给一致率文本；缺金标/键不对齐/坏 JSON 时为 null，
   * reason 给受控成因码（GOLD_MISSING / CASE_KEY_MISMATCH / CHAMPION_UNPARSEABLE / CHALLENGER_UNPARSEABLE）。
   */
  public record Comparison(
      int totalCases,
      int comparedCases,
      int missingGold,
      int disagreeCount,
      List<CaseDiff> diffs,
      String conclusion,
      String reason) {

    public Comparison {
      diffs = List.copyOf(diffs);
    }
  }

  public static Comparison compare(
      String championJson, String challengerJson, ObjectMapper mapper) {
    JsonNode champion = parse(mapper, championJson);
    if (champion == null) {
      return empty("CHAMPION_UNPARSEABLE");
    }
    JsonNode challenger = parse(mapper, challengerJson);
    if (challenger == null) {
      return empty("CHALLENGER_UNPARSEABLE");
    }
    Map<String, JsonNode> byKey = new LinkedHashMap<>();
    for (JsonNode n : champion) {
      byKey.put(n.path("caseKey").asText(""), n);
    }
    Map<String, JsonNode> challengerByKey = new LinkedHashMap<>();
    for (JsonNode n : challenger) {
      challengerByKey.put(n.path("caseKey").asText(""), n);
    }
    if (!byKey.keySet().equals(challengerByKey.keySet())) {
      return empty("CASE_KEY_MISMATCH");
    }

    List<CaseDiff> diffs = new ArrayList<>();
    int missingGold = 0;
    int disagree = 0;
    for (Map.Entry<String, JsonNode> e : byKey.entrySet()) {
      String gold = e.getValue().path("goldDecision").asText("");
      if (gold.isBlank()) {
        missingGold++;
        diffs.add(new CaseDiff(e.getKey(), false, false, "NO_GOLD"));
        continue;
      }
      boolean agree =
          e.getValue().path("decisionResult").asText("")
              .equalsIgnoreCase(challengerByKey.get(e.getKey()).path("decisionResult").asText(""));
      if (!agree) {
        disagree++;
      }
      diffs.add(new CaseDiff(e.getKey(), true, agree, agree ? "AGREE" : "DISAGREE"));
    }
    int compared = byKey.size() - missingGold;
    if (missingGold > 0 || compared == 0) {
      return new Comparison(
          byKey.size(), compared, missingGold, disagree, diffs, null, "GOLD_MISSING");
    }
    double agreeRate = (byKey.size() - (double) disagree) / byKey.size() * 100;
    return new Comparison(
        byKey.size(),
        compared,
        0,
        disagree,
        diffs,
        "compared=" + compared + ", agreeRate=" + String.format(java.util.Locale.ROOT,
            "%.2f", agreeRate) + "%（仅汇总，优劣结论须走 challengerAdmissible 全链判据）",
        "OK");
  }

  private static JsonNode parse(ObjectMapper mapper, String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonNode node = mapper.readTree(json);
      return node.isArray() ? node : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private static Comparison empty(String reason) {
    return new Comparison(0, 0, 0, 0, List.of(), null, reason);
  }
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=ChampionChallengerComparatorTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

- [x] **Step 5: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-apps/synap-claim/src/main/java/com/bone/claim/application/ChampionChallengerComparator.java \
  bone-apps/synap-claim/src/test/java/com/bone/claim/application/ChampionChallengerComparatorTest.java
git commit -m "feat(synap-claim): champion-challenger 逐案离线对比（缺金标只产差异清单）"
```

---

### Task 4: 网关最小多模态扩展（LlmMessage.imageUrl + content-parts 序列化）

**Files:**
- Modify: `bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/domain/LlmMessage.java`
- Modify: `bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/infrastructure/OpenAiCompatibleClient.java:144-156`（buildBody 的 messages 循环）
- Test: `bone-ai/bone-ai-gateway/src/test/java/com/bone/ai/gateway/infrastructure/OpenAiCompatibleClientMultimodalTest.java`（新文件，避免动既有 OpenAiCompatibleClientTest）

**Interfaces:**
- Consumes: 现有 `LlmMessage(String role, String content)` record 与全部既有调用点（须零改动编译通过）；`LlmHttpTransport.post(url, headers, body)` 返回 String。
- Produces: `LlmMessage` 变为 3 组件 record `(role, content, imageUrl)`，新增 2 参兼容构造与 `LlmMessage.userImage(String content, String imageUrl)` 静态工厂；`OpenAiCompatibleClient` 对带 imageUrl 的消息序列化为 OpenAI content-parts 数组 `[{type:text},{type:image_url,image_url:{url}}]`，纯文本消息维持字符串 content（向后兼容）。

- [x] **Step 1: 写失败测试**

创建 `OpenAiCompatibleClientMultimodalTest.java`：

```java
package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 最小多模态扩展：带图消息序列化为 content-parts，纯文本消息保持字符串 content 不变。 */
class OpenAiCompatibleClientMultimodalTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 捕获请求体的假传输层（与生产 OpenAiCompatibleClient 的调用面一致）。 */
  static final class CapturingTransport implements LlmHttpTransport {
    String lastBody;

    @Override
    public String post(String url, Map<String, String> headers, String body) {
      this.lastBody = body;
      return "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
          + "\"usage\":{\"total_tokens\":1}}";
    }

    @Override
    public List<String> streamPost(String url, Map<String, String> headers, String body) {
      return List.of();
    }
  }

  @Test
  @DisplayName("带图 user 消息 → [{type:text},{type:image_url,image_url:{url}}]")
  void imageMessageSerializedAsContentParts() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(transport, "http://x", null, "m");

    client.chat(new LlmRequest("m",
        List.of(LlmMessage.system("sys"),
            LlmMessage.userImage("看图抽取字段", "https://example.com/1.png")),
        null, null, false, false, "t1", null));

    JsonNode root = MAPPER.readTree(transport.lastBody);
    JsonNode user = root.path("messages").get(1);
    assertThat(user.path("content").isArray()).isTrue();
    assertThat(user.path("content").get(0).path("type").asText()).isEqualTo("text");
    assertThat(user.path("content").get(0).path("text").asText()).isEqualTo("看图抽取字段");
    assertThat(user.path("content").get(1).path("type").asText()).isEqualTo("image_url");
    assertThat(user.path("content").get(1).path("image_url").path("url").asText())
        .isEqualTo("https://example.com/1.png");
  }

  @Test
  @DisplayName("向后兼容：2 参构造/纯文本消息 content 仍为字符串，imageUrl 为 null")
  void textMessageUnchanged() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(transport, "http://x", null, "m");

    assertThat(LlmMessage.user("hi").imageUrl()).isNull();
    client.chat(new LlmRequest("m",
        List.of(LlmMessage.system("sys"), LlmMessage.user("纯文本")),
        null, null, false, false, "t2", null));

    JsonNode root = MAPPER.readTree(transport.lastBody);
    assertThat(root.path("messages").get(1).path("content").isTextual()).isTrue();
    assertThat(root.path("messages").get(1).path("content").asText()).isEqualTo("纯文本");
  }
}
```

- [x] **Step 2: 运行确认失败**

Run: `mvn -pl bone-ai/bone-ai-gateway test -Dtest=OpenAiCompatibleClientMultimodalTest`
Expected: 编译失败 `cannot find symbol: method userImage(...)` / `imageUrl()`。

- [x] **Step 3: 改 LlmMessage（3 组件 record + 2 参兼容构造，既有调用点零改动）**

把 `LlmMessage.java` 的 record 声明与静态工厂改为：

```java
public record LlmMessage(String role, String content, String imageUrl) {

  /** OpenAI chat 协议受控角色集合（多轮对话的助手回包也走此契约）。 */
  private static final Set<String> ROLES = Set.of("system", "user", "assistant");

  public LlmMessage {
    if (role == null || !ROLES.contains(role)) {
      throw new IllegalArgumentException("message role 须为 system/user/assistant 之一: " + role);
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("message content 不得为空（role=" + role + "）");
    }
    if (imageUrl != null && imageUrl.isBlank()) {
      imageUrl = null; // 空白等价于无图，不把空串当图片 URL
    }
  }

  /** 兼容旧 2 参构造（纯文本消息，imageUrl=null）——既有调用点零改动。 */
  public LlmMessage(String role, String content) {
    this(role, content, null);
  }

  public static LlmMessage system(String content) {
    return new LlmMessage("system", content);
  }

  public static LlmMessage user(String content) {
    return new LlmMessage("user", content);
  }

  /** 带图 user 消息（最小多模态扩展：序列化为 text + image_url content-parts）。 */
  public static LlmMessage userImage(String content, String imageUrl) {
    return new LlmMessage("user", content, imageUrl);
  }

  /** 助手回包（多轮对话上下文回填用）。 */
  public static LlmMessage assistant(String content) {
    return new LlmMessage("assistant", content);
  }
}
```

（类头 javadoc 末尾追加一行：`多模态（2026-11 D2）：imageUrl 可选，带图消息由 OpenAiCompatibleClient 序列化为 content-parts；纯文本调用点零改动。`）

- [x] **Step 4: 改 OpenAiCompatibleClient.buildBody 的 messages 序列化**

把 `buildBody` 中 `List<Map<String, String>> messages = new ArrayList<>();` 及循环体替换为：

```java
      List<Map<String, Object>> messages = new ArrayList<>();
      for (LlmMessage m : request.messages()) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("role", m.role());
        if (m.imageUrl() != null) {
          // OpenAI content-parts：text + image_url（最小多模态扩展；纯文本消息维持字符串 content）
          mm.put(
              "content",
              List.of(
                  Map.of("type", "text", "text", m.content()),
                  Map.of("type", "image_url", "image_url", Map.of("url", m.imageUrl()))));
        } else {
          mm.put("content", m.content());
        }
        messages.add(mm);
      }
```

- [x] **Step 5: 运行网关全量测试（含既有回归）**

Run: `mvn -pl bone-ai/bone-ai-gateway test`
Expected: BUILD SUCCESS（新测试 2 项 + 既有 OpenAiCompatibleClientTest / LlmContractInvariantsTest / CascadeLlmClientTest 等全绿——record 组件追加保持二进制源兼容）。

- [x] **Step 6: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/domain/LlmMessage.java \
  bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/infrastructure/OpenAiCompatibleClient.java \
  bone-ai/bone-ai-gateway/src/test/java/com/bone/ai/gateway/infrastructure/OpenAiCompatibleClientMultimodalTest.java
git commit -m "feat(bone-ai-gateway): LlmMessage 可选 imageUrl 与 OpenAI content-parts 序列化"
```

---

### Task 5: 图像解析端口扩展、适配器与门控

**Files:**
- Modify: `bone-apps/synap-claim/src/main/java/com/bone/claim/application/port/out/DocumentParsePort.java`
- Modify: `bone-apps/synap-claim/src/main/resources/application.yml`（`bone.claim.document-parse` 块）
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/document/ImageDocumentParsePortAdapter.java`
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/infrastructure/document/ImageDocumentParsePortAdapterTest.java`

**Interfaces:**
- Consumes: Task 4 的 `LlmMessage.userImage(...)`；既有 `LlmGatewayApplicationService.invoke(LlmRequest)`、`PiiMasker.mask(String)`、`SafePromptBuilder.build(String, null)`、`LlmOutputGuard.filterWhitelist/requireFields`、`TextLayerDocumentParsePortAdapter.requiredFields(docType)`、`LlmDocumentParsePortAdapter.ALLOWED_FIELDS`。
- Produces: `DocumentParsePort` 新增 default 方法 `parseImage(DocType docType, String imageRef)`（缺省返回 available=false + reason=`IMAGE_NOT_SUPPORTED`，既有适配器零改动）；`ImageDocumentParsePortAdapter`（`@ConditionalOnProperty(name="bone.claim.document-parse.image.enabled", havingValue="true")`），公开归因码常量 `REASON_OK="IMAGE_PARSE_OK"` / `REASON_GATEWAY_UNAVAILABLE="IMAGE_GATEWAY_UNAVAILABLE"` / `REASON_OUTPUT_INVALID="IMAGE_OUTPUT_INVALID"` / `REASON_MOCK_REJECTED="IMAGE_MOCK_REJECTED"`；yml 新键 `bone.claim.document-parse.image.enabled: ${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:false}`。

- [x] **Step 1: DocumentParsePort 增 default parseImage**

在 `parse` 方法声明后追加：

```java
  /**
   * 图像件解析（D2 最小多模态扩展）：默认不支持。
   *
   * <p>既有文本层适配器零改动即继承本缺省，返回 available=false + 受控码
   * IMAGE_NOT_SUPPORTED（fail-closed，消费方转人工补录，绝不冒充解析成功）。
   * 图像通道由 {@code ImageDocumentParsePortAdapter} 在门控开启时覆盖。
   */
  default ParsedDocument parseImage(DocType docType, String imageRef) {
    return new ParsedDocument(
        false,
        false,
        BigDecimal.ZERO,
        List.of(),
        "{\"available\":false,\"reason\":\"IMAGE_NOT_SUPPORTED\"}",
        "IMAGE_NOT_SUPPORTED");
  }
```

- [x] **Step 2: application.yml 增门控（默认 false，注释写明诚实降级口径）**

在 `bone.claim.document-parse` 块的 `mode:` 行后追加：

```yaml
      image:
        # D2 图像感知通道：默认 false——图像件维持转人工补录，开与不开现网行为不变。
        # true 时 ImageDocumentParsePortAdapter 装配，图像件经网关多模态解析；
        # 网关异常/超时/契约违反/mock 一律落 FAILED + 归因码（IMAGE_GATEWAY_UNAVAILABLE /
        # IMAGE_OUTPUT_INVALID / IMAGE_MOCK_REJECTED）转人工，禁止文本层兜底冒充。
        enabled: ${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:false}
```

- [x] **Step 3: 写失败测试（门控装配三态 + 解析成功/契约违反/mock 拒绝/网关不可用）**

创建 `ImageDocumentParsePortAdapterTest.java`：

```java
package com.bone.claim.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/** 图像解析适配器：门控三态（默认关不装配）+ 解析成功/契约违反/mock 拒绝/网关不可用。 */
class ImageDocumentParsePortAdapterTest {

  private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
      .withUserConfiguration(ImageDocumentParsePortAdapter.class)
      .withBean(LlmGatewayApplicationService.class,
          () -> mock(LlmGatewayApplicationService.class));

  @Test
  @DisplayName("门控默认关：Bean 不装配（现网行为不变）")
  void gateOffByDefault() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean("imageDocumentParsePortAdapter"));
  }

  @Test
  @DisplayName("门控开：Bean 装配")
  void gateOnAssembles() {
    runner.withPropertyValues("bone.claim.document-parse.image.enabled=true")
        .run(ctx -> assertThat(ctx).hasSingleBean(ImageDocumentParsePortAdapter.class));
  }

  private static ImageDocumentParsePortAdapter adapterReturning(String content) {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse(content, "m", "stop", LlmUsage.empty()));
    return new ImageDocumentParsePortAdapter(gateway);
  }

  @Test
  @DisplayName("成功路径：available=true + 白名单字段 + 坐标 image@code")
  void success() {
    var adapter = adapterReturning(
        "{\"fields\":[{\"code\":\"diagnosis_code\",\"value\":\"J06.9\",\"confidence\":0.88}]}");

    var doc = adapter.parseImage(DocType.MEDICAL_RECORD, "oss://bucket/x.jpg");

    assertThat(doc.available()).isTrue();
    assertThat(doc.reason()).isEqualTo(ImageDocumentParsePortAdapter.REASON_OK);
    assertThat(doc.fields()).hasSize(1);
    assertThat(doc.fields().get(0).evidenceRef()).isEqualTo("image@diagnosis_code");
    assertThat(doc.extractedJson()).contains("\"mode\":\"image\"");
  }

  @Test
  @DisplayName("mock 响应拒绝：IMAGE_MOCK_REJECTED（fail-closed，不冒充解析成功）")
  void mockRejected() {
    var adapter = adapterReturning("{\"mock\":true}");

    var doc = adapter.parseImage(DocType.INVOICE, "oss://bucket/x.jpg");

    assertThat(doc.available()).isFalse();
    assertThat(doc.reason()).isEqualTo(ImageDocumentParsePortAdapter.REASON_MOCK_REJECTED);
  }

  @Test
  @DisplayName("网关异常 → IMAGE_GATEWAY_UNAVAILABLE；非 JSON/字段越界 → IMAGE_OUTPUT_INVALID；无 imageRef → IMAGE_REF_MISSING")
  void failureAttribution() {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any())).thenThrow(new IllegalStateException("boom"));
    ImageDocumentParsePortAdapter failing = new ImageDocumentParsePortAdapter(gateway);

    assertThat(failing.parseImage(DocType.INVOICE, "oss://b/x.jpg").reason())
        .isEqualTo(ImageDocumentParsePortAdapter.REASON_GATEWAY_UNAVAILABLE);
    assertThat(adapterReturning("not-json").parseImage(DocType.INVOICE, "oss://b/x.jpg").reason())
        .isEqualTo(ImageDocumentParsePortAdapter.REASON_OUTPUT_INVALID);
    assertThat(adapterReturning("{\"fields\":[]}").parseImage(DocType.INVOICE, "oss://b/x.jpg")
        .reason()).isEqualTo(ImageDocumentParsePortAdapter.REASON_OUTPUT_INVALID);
    assertThat(
            adapterReturning("{\"fields\":[]}")
                .parseImage(DocType.INVOICE, "oss://b/x.jpg")
                .fields())
        .isEmpty();
    assertThat(failing.parseImage(DocType.INVOICE, "  ").reason()).isEqualTo("IMAGE_REF_MISSING");
  }

  @Test
  @DisplayName("文本层 parse 委托确定性基线（图像通道不改变文本路径行为）")
  void textParseDelegatesToBaseline() {
    var adapter = adapterReturning("{}");

    var doc = adapter.parse(DocType.INVOICE, "发票号码:12345,合计金额:￥100.00");

    assertThat(doc.available()).isTrue();
    assertThat(doc.reason()).isEqualTo(TextLayerDocumentParsePortAdapter.REASON_OK);
  }
}
```

注：`LlmResponse`/`LlmUsage` 的构造/工厂签名以现有测试（`MockLlmClientTest`、`LlmDocumentParsePortAdapterTest`）中的用法为准；若 `LlmUsage.empty()` 不存在，用 `new LlmUsage(0, 0, 0)` 等既有测试同款写法。`TextLayerDocumentParsePortAdapter.REASON_OK` 若常量名不同，以该类源文件为准（文本层成功码）。

- [x] **Step 4: 运行确认失败**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=ImageDocumentParsePortAdapterTest`
Expected: 编译失败 `cannot find symbol: class ImageDocumentParsePortAdapter`。

- [x] **Step 5: 写适配器实现（复用三件套，mock 拒绝，失败归因码）**

创建 `ImageDocumentParsePortAdapter.java`：

```java
package com.bone.claim.infrastructure.document;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.security.LlmOutputGuard;
import com.bone.claim.domain.security.MaskedText;
import com.bone.claim.domain.security.PiiMasker;
import com.bone.claim.domain.security.SafePromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 图像件感知适配器（D2，{@code bone.claim.document-parse.image.enabled=true} 时装配）。
 *
 * <p>走 bone-ai-gateway 最小多模态扩展（LlmMessage.imageUrl → OpenAI content-parts），复用
 * AI 安全三件套：{@link PiiMasker} 出站前遮蔽、{@link SafePromptBuilder} 数据槽位、
 * {@link LlmOutputGuard} 字段白名单/必填校验。产物与文本层同构（ParsedDocument：
 * 字段 + 置信度 + 坐标 image@code + 完整性）。
 *
 * <p><b>fail-closed</b>：网关异常/超时 → IMAGE_GATEWAY_UNAVAILABLE；非 JSON/字段越界 →
 * IMAGE_OUTPUT_INVALID；mock 自报 → IMAGE_MOCK_REJECTED；无 imageRef → IMAGE_REF_MISSING。
 * 一律 available=false，由应用服务落 FAILED 转人工补录——<b>禁止文本层兜底冒充图像解析成功</b>。
 */
@Component
@ConditionalOnProperty(name = "bone.claim.document-parse.image.enabled", havingValue = "true")
public class ImageDocumentParsePortAdapter implements DocumentParsePort {

  public static final String MODEL = "synap-claim-doc-image-extract";
  public static final String SCENARIO = "claim-doc-image-extract";

  public static final String REASON_OK = "IMAGE_PARSE_OK";
  public static final String REASON_GATEWAY_UNAVAILABLE = "IMAGE_GATEWAY_UNAVAILABLE";
  public static final String REASON_OUTPUT_INVALID = "IMAGE_OUTPUT_INVALID";
  public static final String REASON_MOCK_REJECTED = "IMAGE_MOCK_REJECTED";
  public static final String REASON_REF_MISSING = "IMAGE_REF_MISSING";

  private static final BigDecimal ZERO = BigDecimal.ZERO;

  private final LlmGatewayApplicationService gateway;
  private final ObjectMapper mapper = new ObjectMapper();
  private final DocumentParsePort baseline = new TextLayerDocumentParsePortAdapter();

  public ImageDocumentParsePortAdapter(LlmGatewayApplicationService gateway) {
    this.gateway = gateway;
  }

  @Override
  public ParsedDocument parseImage(DocType docType, String imageRef) {
    if (imageRef == null || imageRef.isBlank()) {
      return unavailable(REASON_REF_MISSING);
    }
    DocType effective = docType == null ? DocType.INVOICE : docType;
    MaskedText masked = PiiMasker.mask("图像件解析：" + effective);
    LlmRequest request =
        new LlmRequest(
            MODEL,
            List.of(
                LlmMessage.system(
                    "你是保险理赔单证结构化抽取助手。用户消息附带一张单证图片，"
                        + "严格输出 JSON 对象："
                        + "{\"fields\":[{\"code\":字符串,\"value\":字符串,\"confidence\":0~1数字}]}。"
                        + "code 只允许 "
                        + LlmDocumentParsePortAdapter.ALLOWED_FIELDS.stream()
                            .sorted()
                            .collect(Collectors.joining(", "))
                        + "；看不清的字段输出空字符串，禁止臆造，禁止输出解释文字。"),
                LlmMessage.userImage(SafePromptBuilder.build(masked.masked(), null), imageRef)),
            null,
            null,
            true,
            false,
            "img-" + UUID.randomUUID(),
            SCENARIO);
    LlmResponse response;
    try {
      response = gateway.invoke(request);
    } catch (RuntimeException ex) {
      return unavailable(REASON_GATEWAY_UNAVAILABLE);
    }
    return parseResponse(effective, response);
  }

  /** 文本层 parse 与图像通道无关：委托确定性基线，行为与既有文本层实现一致。 */
  @Override
  public ParsedDocument parse(DocType docType, String rawText) {
    return baseline.parse(docType, rawText);
  }

  private ParsedDocument parseResponse(DocType docType, LlmResponse response) {
    JsonNode root;
    try {
      root = mapper.readTree(response.content());
    } catch (Exception ex) {
      return unavailable(REASON_OUTPUT_INVALID);
    }
    if (root.path("mock").asBoolean(false)) {
      return unavailable(REASON_MOCK_REJECTED);
    }
    JsonNode array = root.path("fields");
    if (!array.isArray()) {
      return unavailable(REASON_OUTPUT_INVALID);
    }
    Map<String, Object> modelValues = new LinkedHashMap<>();
    Map<String, BigDecimal> confidences = new LinkedHashMap<>();
    for (JsonNode field : array) {
      String code = field.path("code").asText("");
      String value = field.path("value").asText("");
      if (code.isBlank()) {
        continue;
      }
      modelValues.put(code, value);
      double raw = field.path("confidence").asDouble(0.0);
      confidences.put(code, BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, raw))));
    }
    Map<String, Object> filtered = LlmOutputGuard.filterWhitelist(modelValues,
        LlmDocumentParsePortAdapter.ALLOWED_FIELDS);
    if (filtered.isEmpty()) {
      return unavailable(REASON_OUTPUT_INVALID);
    }
    Set<String> missing = LlmOutputGuard.requireFields(filtered,
        TextLayerDocumentParsePortAdapter.requiredFields(docType));

    List<Field> fields = new ArrayList<>();
    BigDecimal min = null;
    for (Map.Entry<String, Object> entry : filtered.entrySet()) {
      BigDecimal confidence = confidences.getOrDefault(entry.getKey(), ZERO);
      if (min == null || confidence.compareTo(min) < 0) {
        min = confidence;
      }
      fields.add(new Field(entry.getKey(), String.valueOf(entry.getValue()), confidence,
          "image@" + entry.getKey()));
    }
    if (min == null) {
      min = ZERO;
    }
    boolean complete = missing.isEmpty();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", fields);
    payload.put("mode", "image");
    payload.put("complete", complete);
    payload.put("missing", missing.stream().sorted().toList());
    payload.put("reason", REASON_OK);
    return new ParsedDocument(true, complete, min, fields, toJson(payload), REASON_OK);
  }

  private ParsedDocument unavailable(String reason) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fields", List.of());
    payload.put("mode", "image");
    payload.put("available", false);
    payload.put("reason", reason);
    return new ParsedDocument(false, false, ZERO, List.of(), toJson(payload), reason);
  }

  private String toJson(Map<String, Object> payload) {
    try {
      return mapper.writeValueAsString(payload);
    } catch (Exception ex) {
      return "{\"fields\":[],\"reason\":\"SERIALIZE_FAILED\"}";
    }
  }
}
```

注：`LlmOutputGuard.requireFields(...)` 返回类型与 `TextLayerDocumentParsePortAdapter.requiredFields(docType)` 的确切签名以源文件为准（LlmDocumentParsePortAdapter 已有同款调用：`boolean complete = LlmOutputGuard.requireFields(filtered, requiredFields).isEmpty();`）——若返回的是 List 而非 Set，把 `Set<String> missing` 改为对应类型即可。

- [x] **Step 6: 运行测试确认通过**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=ImageDocumentParsePortAdapterTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`。

- [x] **Step 7: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-apps/synap-claim/src/main/java/com/bone/claim/application/port/out/DocumentParsePort.java \
  bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/document/ImageDocumentParsePortAdapter.java \
  bone-apps/synap-claim/src/main/resources/application.yml \
  bone-apps/synap-claim/src/test/java/com/bone/claim/infrastructure/document/ImageDocumentParsePortAdapterTest.java
git commit -m "feat(synap-claim): 图像解析适配器与门控（默认关，fail-closed 归因码）"
```

---

### Task 6: EvidenceApplicationService.parseImage 路由与参与口径如实

**Files:**
- Modify: `bone-apps/synap-claim/src/main/java/com/bone/claim/application/EvidenceApplicationService.java`（`parse` 方法后新增方法）
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/application/EvidenceImageParsingTest.java`

**Interfaces:**
- Consumes: Task 5 的 `DocumentParsePort.parseImage(DocType, String imageRef)`；`Evidence.load`（私有，测试经仓储 mock）；`Evidence` 实体的 fileRef 访问器（Lombok `@Getter`，若访问器名非 `getFileRef()` 以实体源文件为准）。
- Produces: `ParseOutcome parseImage(long evidenceId)`——状态映射与 `parse` 完全一致（!available → FAILED + confidence=null；!complete → MANUAL；否则 DONE），`reason` 原样透传归因码（参与口径如实，不重写为通用码）。图像解析不是决策图节点——五节点 DAG 拓扑不变，参与口径经 ParseOutcome.reason 落证据层审计。

- [x] **Step 1: 写失败测试**

创建 `EvidenceImageParsingTest.java`：

```java
package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 图像件解析入口：状态映射与文本层一致；归因码原样透传（参与口径如实反映）。 */
class EvidenceImageParsingTest {

  private EvidenceRepository evidenceRepository;
  private DocumentParsePort documentParsePort;
  private EvidenceApplicationService service;

  @BeforeEach
  void setUp() {
    evidenceRepository = mock(EvidenceRepository.class);
    documentParsePort = mock(DocumentParsePort.class);
    service =
        new EvidenceApplicationService(
            evidenceRepository,
            mock(TenantPort.class),
            mock(DomainEventPublisher.class),
            mock(ClaimCaseRepository.class),
            documentParsePort);
  }

  private static Evidence imageEvidence() {
    return Evidence.register(
        1L, 42L, 9001L, DocType.MEDICAL_RECORD, "oss://bucket/x.jpg", "hash-x", null);
  }

  @Test
  @DisplayName("门控关（缺省 IMAGE_NOT_SUPPORTED）：落 FAILED 转人工，不冒充解析成功")
  void gateOffFallsToFailed() {
    Evidence evidence = imageEvidence();
    when(evidenceRepository.findById(1L)).thenReturn(evidence);
    when(documentParsePort.parseImage(any(), any()))
        .thenReturn(
            new DocumentParsePort.ParsedDocument(
                false, false, BigDecimal.ZERO, List.of(),
                "{\"available\":false,\"reason\":\"IMAGE_NOT_SUPPORTED\"}",
                "IMAGE_NOT_SUPPORTED"));

    var outcome = service.parseImage(1L);

    assertThat(outcome.status()).isEqualTo("FAILED");
    assertThat(outcome.available()).isFalse();
    assertThat(outcome.reason()).isEqualTo("IMAGE_NOT_SUPPORTED");
    verify(evidenceRepository).save(evidence);
  }

  @Test
  @DisplayName("成功路径：DONE + minConfidence + 归因码 IMAGE_PARSE_OK 原样透传")
  void successMapsToDone() {
    Evidence evidence = imageEvidence();
    when(evidenceRepository.findById(1L)).thenReturn(evidence);
    when(documentParsePort.parseImage(any(), any()))
        .thenReturn(
            new DocumentParsePort.ParsedDocument(
                true, true, new BigDecimal("0.88"),
                List.of(new DocumentParsePort.Field(
                    "diagnosis_code", "J06.9", new BigDecimal("0.88"), "image@diagnosis_code")),
                "{}", "IMAGE_PARSE_OK"));

    var outcome = service.parseImage(1L);

    assertThat(outcome.status()).isEqualTo("DONE");
    assertThat(outcome.available()).isTrue();
    assertThat(outcome.minConfidence()).isEqualByComparingTo("0.88");
    assertThat(outcome.reason()).isEqualTo("IMAGE_PARSE_OK");
  }

  @Test
  @DisplayName("降级归因码（mock 拒绝/网关不可用/输出无效）逐字透传，不重写为通用码")
  void degradeCodesPassThrough() {
    for (String code : List.of(
        ImageDocumentParseCode.IMAGE_MOCK_REJECTED,
        ImageDocumentParseCode.IMAGE_GATEWAY_UNAVAILABLE,
        ImageDocumentParseCode.IMAGE_OUTPUT_INVALID)) {
      Evidence evidence = imageEvidence();
      when(evidenceRepository.findById(anyLong())).thenReturn(evidence);
      when(documentParsePort.parseImage(any(), any()))
          .thenReturn(
              new DocumentParsePort.ParsedDocument(
                  false, false, BigDecimal.ZERO, List.of(), "{\"reason\":\"" + code + "\"}", code));

      var outcome = service.parseImage(1L);

      assertThat(outcome.status()).isEqualTo("FAILED");
      assertThat(outcome.reason()).isEqualTo(code);
    }
  }

  /** 与 ImageDocumentParsePortAdapter 常量对齐（避免测试硬编码漂移）。 */
  private static final class ImageDocumentParseCode {
    static final String IMAGE_MOCK_REJECTED = "IMAGE_MOCK_REJECTED";
    static final String IMAGE_GATEWAY_UNAVAILABLE = "IMAGE_GATEWAY_UNAVAILABLE";
    static final String IMAGE_OUTPUT_INVALID = "IMAGE_OUTPUT_INVALID";
  }
}
```

注：`Evidence.register(...)` 参数序为 `(id, tenantId, claimId, docType, fileRef, contentHash, verifyValid)`（与 `EvidenceApplicationService.register` 内的调用一致）；若 `verifyValid` 形参类型不是 String 以实体为准。`EvidenceApplicationService` 构造参数序以源文件 `@RequiredArgsConstructor` 字段序为准（evidenceRepository, tenantPort, domainEventPublisher, claimCaseRepository, documentParsePort）。

- [x] **Step 2: 运行确认失败**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=EvidenceImageParsingTest`
Expected: 编译失败 `cannot find symbol: method parseImage(long)`。

- [x] **Step 3: 实现 parseImage（紧随 `parse` 方法之后）**

```java
  /**
   * 图像件解析（D2）：按 {@code fileRef} 路由到图像感知通道。
   *
   * <p>与 {@link #parse} 同一状态映射：available=false（门控关缺省码 IMAGE_NOT_SUPPORTED /
   * 网关不可用 / 契约违反 / mock）一律落 {@code FAILED} 转人工补录；绝不把图像件交给文本层
   * 兜底冒充解析成功。图像解析不是决策图节点——五节点 DAG 拓扑不变；模型参与与降级口径
   * 经 {@code ParseOutcome.reason}（受控归因码）原样落证据层审计，供健康度归因消费。
   */
  @Transactional
  public ParseOutcome parseImage(long evidenceId) {
    Evidence evidence = load(evidenceId);
    DocumentParsePort.ParsedDocument document =
        documentParsePort.parseImage(evidence.docTypeEnum(), evidence.getFileRef());

    OcrStatus status;
    BigDecimal confidence;
    if (!document.available()) {
      status = OcrStatus.FAILED;
      confidence = null;
    } else if (!document.complete()) {
      status = OcrStatus.MANUAL;
      confidence = document.minConfidence();
    } else {
      status = OcrStatus.DONE;
      confidence = document.minConfidence();
    }

    evidence.markParsed(status, confidence, document.extractedJson(), null);
    evidenceRepository.save(evidence);
    domainEventPublisher.publishFrom(evidence);
    return new ParseOutcome(
        evidence.ocrStatusEnum().name(),
        document.available(),
        document.complete(),
        document.minConfidence(),
        document.fields().size(),
        document.reason());
  }
```

- [x] **Step 4: 运行测试与模块回归**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=EvidenceImageParsingTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`。

Run: `mvn -pl bone-apps/synap-claim test`
Expected: BUILD SUCCESS（既有 EvidenceParsingTest / DecisionGraphWiringTest 等全绿——default 方法不破坏既有 mock 与适配器）。

- [x] **Step 5: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-apps/synap-claim/src/main/java/com/bone/claim/application/EvidenceApplicationService.java \
  bone-apps/synap-claim/src/test/java/com/bone/claim/application/EvidenceImageParsingTest.java
git commit -m "feat(synap-claim): EvidenceApplicationService.parseImage 按 fileRef 路由图像解析"
```

---

### Task 7: 受控 RAG 组合适配器与 KnowledgeBasis 二级归因

**前置确认（L3）：** pom 依赖变更需架构师审批，执行前先向用户确认。

**Files:**
- Modify: `bone-apps/synap-claim/pom.xml`（依赖，加在 bone-ai-rules 依赖之后）
- Modify: `bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/decision/KnowledgeBasis.java`（追加 3 个枚举值）
- Modify: `bone-apps/synap-claim/src/main/resources/application.yml`（`bone.claim.knowledge` 块）
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/knowledge/RagKnowledgeRetrievalPortAdapter.java`
- Create: `bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/knowledge/ClaimRagScanConfiguration.java`
- Test: `bone-apps/synap-claim/src/test/java/com/bone/claim/infrastructure/knowledge/RagKnowledgeRetrievalPortAdapterTest.java`

**Interfaces:**
- Consumes: `RagApplicationService.retrieve(RagQuery) → RagRetrieveOutcome(List<RagResult>, List<String> degradations)`（bone-ai-rag 自动配置 `com.bone.airag.config.RagAutoConfiguration` 经 `META-INF/spring/...AutoConfiguration.imports` 提供 Bean；`RagResult(ref, clauseRef, spanStart, spanEnd, score, sectionPath, text)`）；`CatalogKnowledgeRetrievalPortAdapter.match(ctx)`（目录准入，逻辑不变）；`EvidenceFieldReader.read(extractedJson)`。
- Produces: `RagKnowledgeRetrievalPortAdapter`（`@Primary` + `@ConditionalOnProperty(name="bone.claim.knowledge.rag.enabled", havingValue="true")`）实现 `KnowledgeRetrievalPort.match`：目录准入通过原样返回；仅「目录未收录（CATALOG_UNKNOWN）且有检索词」时进语义支路；结果必须带 `clauseRef`（缺失丢弃计数），全无命中 → `RAG_CORPUS_MISSING`，检索异常 → `RAG_RETRIEVAL_UNAVAILABLE`，命中 → `RAG_CLAUSE_CITED`。`KnowledgeBasis` 追加 `RAG_CLAUSE_CITED` / `RAG_CORPUS_MISSING` / `RAG_RETRIEVAL_UNAVAILABLE`。yml 新键 `bone.claim.knowledge.rag.enabled: ${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:false}`。

- [x] **Step 1: pom 增依赖（显式版本——bone-ai-rag 未纳入 bone-parent dependencyManagement，已实测确认）**

在 `bone-ai-rules` 依赖块后追加：

```xml
        <!-- D3 受控 RAG（bone-ai C6）：RagKnowledgeRetrievalPortAdapter 语义检索支路；
             bone-ai-rag 未纳入 bone-parent dependencyManagement（已实测），版本显式声明（与 bone-ai-app 同口径）。 -->
        <dependency><groupId>com.bone</groupId>
            <artifactId>bone-ai-rag</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
```

随后先装本地快照：`mvn -pl bone-ai/bone-ai-rag install -DskipTests`（bone-ai-rag 本任务不改代码；若本地仓库已有同版本快照可跳过）。

- [x] **Step 2: application.yml 增门控**

在 `bone.claim` 块内（`document-parse` 之后）追加：

```yaml
    knowledge:
      # D3 受控 RAG 语义检索支路：默认 false——知识层行为与现状（目录准入核验）完全一致。
      # true 时 RagKnowledgeRetrievalPortAdapter 装配为 KnowledgeRetrievalPort 主实现：
      # 目录准入逻辑不变，仅「目录未收录」且有检索词时走 bone-ai-rag 受控检索；
      # 结果必须带 clauseRef（引用强制），无语料/检索异常 → available=false + 受控成因码。
      rag:
        enabled: ${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:false}
```

- [x] **Step 3: KnowledgeBasis 追加二级归因枚举值**

在 `STUB_NOT_INTEGRATED` 之后、`UNRECOGNIZED` 之前插入：

```java
  /** D3 受控 RAG：语义检索命中且结果全部带条款引用（引用强制，无出处切片已丢弃）。 */
  RAG_CLAUSE_CITED(
      "RAG 检索命中并带条款引用", "引用锚点随检索事件留痕；生成侧使用时仍须接地校验，无出处不得生成。"),

  /** D3 受控 RAG：检索执行了但无命中/租户语料为空（fail-closed）。 */
  RAG_CORPUS_MISSING("RAG 无命中或租户语料缺失", "补租户语料 ingest（bone-ai-rag 写入路径）；不要把无命中解释为核验通过。"),

  /** D3 受控 RAG：检索支路异常（容量护栏/嵌入/ACL 等），维持目录准入的 fail-closed 结论。 */
  RAG_RETRIEVAL_UNAVAILABLE("RAG 检索不可用", "查 bone-ai-rag 检索链日志（嵌入/容量护栏/ACL）；恢复前知识层保持 fail-closed，不要临时放宽。"),
```

- [x] **Step 4: 写失败测试**

创建 `RagKnowledgeRetrievalPortAdapterTest.java`：

```java
package com.bone.claim.infrastructure.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.model.RagResult;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.domain.repository.EvidenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/** RAG 组合适配器：目录准入不变 + 语义支路三态（命中带引用/语料缺失/检索异常）+ 门控口径。 */
class RagKnowledgeRetrievalPortAdapterTest {

  private static final ClaimContextPort.ClaimContextView CTX =
      Mockito.mock(ClaimContextPort.ClaimContextView.class);

  static {
    when(CTX.claimId()).thenReturn(9001L);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> providerOf(
      CatalogKnowledgeRetrievalPortAdapter catalog) {
    ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> p = Mockito.mock(ObjectProvider.class);
    when(p.getIfAvailable()).thenReturn(catalog);
    return p;
  }

  private static CatalogKnowledgeRetrievalPortAdapter catalogReturning(
      boolean available, String basis) {
    CatalogKnowledgeRetrievalPortAdapter catalog =
        mock(CatalogKnowledgeRetrievalPortAdapter.class);
    when(catalog.match(any()))
        .thenReturn(new com.bone.claim.application.port.out.KnowledgeRetrievalPort.KnowledgeResult(
            available, available ? new java.math.BigDecimal("0.05") : java.math.BigDecimal.ZERO,
            basis, available));
    return catalog;
  }

  private static RagApplicationService ragReturning(List<RagResult> results) {
    RagApplicationService rag = mock(RagApplicationService.class);
    when(rag.retrieve(any()))
        .thenReturn(new RagApplicationService.RagRetrieveOutcome(results, List.of()));
    return rag;
  }

  @Test
  @DisplayName("目录准入已通过：原样返回，不调 RAG（准入是第一道闸）")
  void catalogPassThrough() {
    RagApplicationService rag = ragReturning(List.of());
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        providerOf(catalogReturning(true, "CATALOG_ADMISSION_VERIFIED")), rag,
        mock(EvidenceRepository.class));

    var result = adapter.match(CTX);

    assertThat(result.available()).isTrue();
    assertThat(result.basis()).isEqualTo("CATALOG_ADMISSION_VERIFIED");
    verify(rag, never()).retrieve(any());
  }

  @Test
  @DisplayName("命中带 clauseRef → RAG_CLAUSE_CITED；无引用结果丢弃计数")
  void hitWithCitation() {
    RagApplicationService rag = ragReturning(List.of(
        new RagResult("r1", "ML-4.2.1", 1, 9, 0.9, "s", "text"),
        new RagResult("r2", "", 1, 9, 0.8, "s", "no-ref")));
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        providerOf(catalogReturning(false, "CATALOG_UNKNOWN")), rag,
        mock(EvidenceRepository.class));

    var result = adapter.match(CTX);

    assertThat(result.available()).isTrue();
    assertThat(result.basis()).isEqualTo("RAG_CLAUSE_CITED");
  }

  @Test
  @DisplayName("检索空结果 → RAG_CORPUS_MISSING（available=false，fail-closed）")
  void corpusMissing() {
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        providerOf(catalogReturning(false, "CATALOG_UNKNOWN")), ragReturning(List.of()),
        mock(EvidenceRepository.class));

    var result = adapter.match(CTX);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo("RAG_CORPUS_MISSING");
  }

  @Test
  @DisplayName("检索异常 → RAG_RETRIEVAL_UNAVAILABLE（不抛出，fail-closed 转人工）")
  void retrievalUnavailable() {
    RagApplicationService rag = mock(RagApplicationService.class);
    when(rag.retrieve(any())).thenThrow(new IllegalStateException("scope over limit"));
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        providerOf(catalogReturning(false, "CATALOG_UNKNOWN")), rag,
        mock(EvidenceRepository.class));

    var result = adapter.match(CTX);

    assertThat(result.available()).isFalse();
    assertThat(result.basis()).isEqualTo("RAG_RETRIEVAL_UNAVAILABLE");
  }

  @Test
  @DisplayName("非 CATALOG_UNKNOWN 成因 / 无检索词：维持目录准入成因码，不进检索支路")
  void nonRetrievableCausesStayPut() {
    RagApplicationService rag = ragReturning(List.of());
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        providerOf(catalogReturning(false, "NO_PARSED_FACTS")), rag,
        mock(EvidenceRepository.class));

    assertThat(adapter.match(CTX).basis()).isEqualTo("NO_PARSED_FACTS");
    verify(rag, never()).retrieve(any());
  }

  @Test
  @DisplayName("目录准入适配器缺席（knowledge.mode=stub）：如实给 STUB_NOT_INTEGRATED")
  void catalogAbsentIsHonest() {
    @SuppressWarnings("unchecked")
    ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> absent = Mockito.mock(ObjectProvider.class);
    when(absent.getIfAvailable()).thenReturn(null);
    var adapter = new RagKnowledgeRetrievalPortAdapter(
        absent, ragReturning(List.of()), mock(EvidenceRepository.class));

    assertThat(adapter.match(CTX).basis()).isEqualTo("STUB_NOT_INTEGRATED");
  }
}
```

- [x] **Step 5: 运行确认失败**

Run: `mvn -pl bone-apps/synap-claim test -Dtest=RagKnowledgeRetrievalPortAdapterTest`
Expected: 编译失败 `cannot find symbol: class RagKnowledgeRetrievalPortAdapter`。

- [x] **Step 6: 写组合适配器与仓储扫描配置**

创建 `RagKnowledgeRetrievalPortAdapter.java`：

```java
package com.bone.claim.infrastructure.knowledge;

import com.bone.airag.application.RagApplicationService;
import com.bone.airag.domain.model.RagQuery;
import com.bone.airag.domain.model.RagResult;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.domain.model.decision.KnowledgeBasis;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.infrastructure.evidence.EvidenceFieldReader;
import com.bone.claim.domain.model.evidence.Evidence;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * D3 受控 RAG 组合适配器：目录准入核验逻辑不变 + 语义检索支路（bone-ai-rag）。
 *
 * <p>目录准入是第一道闸：准入已通过（available=true）原样返回，语义支路不改变结论；仅
 * 「目录未收录（CATALOG_UNKNOWN）且有可检索的临床编码」时进语义支路补查条款出处。
 * 检索词取自感知层结构化事实的 diagnosis_code / drug_code（与目录准入同一读取入口
 * {@link EvidenceFieldReader}），docScope 置 null（租户级检索）。
 *
 * <p><b>引用强制</b>：检索结果必须带 clauseRef，缺失引用的结果丢弃并计数（bone-ai-rag
 * 检索链已前置剔除无出处切片，此处为消费侧二道防线）；全无命中 → RAG_CORPUS_MISSING；
 * 检索异常 → RAG_RETRIEVAL_UNAVAILABLE；命中 → RAG_CLAUSE_CITED。引用明细暂随检索事件留痕
 * （RagEventSink），KnowledgeBasis 只记成因码——端口契约不变。
 *
 * <p>门控 {@code bone.claim.knowledge.rag.enabled} 默认 false：本 Bean 不装配，
 * 知识层行为与现状完全一致（默认配置不变现网行为）。
 */
@Primary
@Component
@ConditionalOnProperty(name = "bone.claim.knowledge.rag.enabled", havingValue = "true")
public class RagKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  private static final BigDecimal CONFIDENCE_DELTA = new BigDecimal("0.05");

  private final ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> catalog;
  private final RagApplicationService rag;
  private final EvidenceRepository evidenceRepository;

  public RagKnowledgeRetrievalPortAdapter(
      ObjectProvider<CatalogKnowledgeRetrievalPortAdapter> catalog,
      RagApplicationService rag,
      EvidenceRepository evidenceRepository) {
    this.catalog = catalog;
    this.rag = rag;
    this.evidenceRepository = evidenceRepository;
  }

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    CatalogKnowledgeRetrievalPortAdapter admission = catalog.getIfAvailable();
    if (admission == null) {
      return unavailable(KnowledgeBasis.STUB_NOT_INTEGRATED.code());
    }
    KnowledgeResult base = admission.match(ctx);
    if (base.available() || KnowledgeBasis.of(base.basis()) != KnowledgeBasis.CATALOG_UNKNOWN) {
      return base; // 准入通过 / 成因不是「目录未收录」：语义支路不改变结论
    }
    List<String> terms = queryTerms(ctx.claimId());
    if (terms.isEmpty()) {
      return base; // 无检索素材：维持目录准入成因码
    }
    com.bone.airag.application.RagApplicationService.RagRetrieveOutcome outcome;
    try {
      outcome = rag.retrieve(
          new RagQuery(String.join(" ", terms), null, RagQuery.DEFAULT_TOP_K));
    } catch (RuntimeException ex) {
      return unavailable(KnowledgeBasis.RAG_RETRIEVAL_UNAVAILABLE.code());
    }
    int droppedNoRef = 0;
    Set<String> citedRefs = new LinkedHashSet<>();
    for (RagResult r : outcome.results()) {
      if (r.clauseRef() == null || r.clauseRef().isBlank()) {
        droppedNoRef++; // 消费侧引用强制二道防线
        continue;
      }
      citedRefs.add(r.clauseRef());
    }
    if (citedRefs.isEmpty()) {
      return unavailable(KnowledgeBasis.RAG_CORPUS_MISSING.code());
    }
    return new KnowledgeResult(true, CONFIDENCE_DELTA,
        KnowledgeBasis.RAG_CLAUSE_CITED.code(), true);
  }

  /** 检索词：感知层结构化事实里的临床编码（诊断 + 药品），与目录准入同源。 */
  private List<String> queryTerms(long claimId) {
    Set<String> terms = new LinkedHashSet<>();
    for (Evidence evidence : evidenceRepository.findByClaimId(claimId)) {
      for (EvidenceFieldReader.ParsedField field : EvidenceFieldReader.read(
          evidence.getExtractedJson())) {
        if (field.value().isBlank()) {
          continue;
        }
        if ("diagnosis_code".equals(field.code()) || "drug_code".equals(field.code())) {
          terms.add(field.value());
        }
      }
    }
    return new ArrayList<>(terms);
  }

  private static KnowledgeResult unavailable(String basis) {
    return new KnowledgeResult(false, BigDecimal.ZERO, basis, false);
  }
}
```

创建 `ClaimRagScanConfiguration.java`（synap-claim 只扫 `com.bone.claim`，bone-ai-rag 的仓储/配置须显式登记——与 `MetadataSdkExtensionScanConfiguration` 同构；门控关时整类不装配）：

```java
package com.bone.claim.infrastructure.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * bone-ai-rag 装配登记（门控开时）：宿主只扫 com.bone.claim，bone-ai-rag 的仓储与
 * 自动配置须显式扫描（与 MetadataSdkExtensionScanConfiguration 同构，幂等——同名同类
 * bean 定义 Spring 判为兼容并跳过）。门控关时不扫描，类路径行为与现状一致。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bone.claim.knowledge.rag.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.bone.airag")
public class ClaimRagScanConfiguration {}
```

- [x] **Step 7: 装配冒烟验证**

Run: `mvn -pl bone-apps/synap-claim test -Dtest='RagKnowledgeRetrievalPortAdapterTest,CatalogKnowledgeRetrievalPortAdapterTest'`
Expected: 新测试 6 项全过 + **既有目录准入单测全绿（回归面保住）**。

Run: `mvn -pl bone-apps/synap-claim test -Dtest='ClaimE2EScenarioTest,LlmClientJsonContractTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS。
**若 H2 装配测试因 ChunkRepository 缺 Bean/SQL 模板失败**：检查 `bone-ai/bone-ai-rag` 的 SQL 资源目录与其 `RagAutoConfiguration` 的条件注解（可能有自带门控键），把对应属性加入测试属性；SQL 模板扫描问题则把 `ClaimRagScanConfiguration` 的 `@ComponentScan` 补充 bone-ai-rag SQL 模板所在的仓储包——修复方向是「登记扫描/补属性」，绝不改 bone-ai-rag 平台代码，也不改任何 `V*__*.sql`。

- [x] **Step 8: 格式化 + 提交**

```bash
mvn spotless:apply
git branch --show-current
git add bone-apps/synap-claim/pom.xml \
  bone-apps/synap-claim/src/main/java/com/bone/claim/domain/model/decision/KnowledgeBasis.java \
  bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/knowledge/RagKnowledgeRetrievalPortAdapter.java \
  bone-apps/synap-claim/src/main/java/com/bone/claim/infrastructure/knowledge/ClaimRagScanConfiguration.java \
  bone-apps/synap-claim/src/main/resources/application.yml \
  bone-apps/synap-claim/src/test/java/com/bone/claim/infrastructure/knowledge/RagKnowledgeRetrievalPortAdapterTest.java
git commit -m "feat(synap-claim): 受控 RAG 组合适配器（门控默认关）与 KnowledgeBasis 二级归因"
```

---

### Task 8: 复核器判据扩展 IMAGE_PARSE_WIRED / RAG_WIRED（含 --self-test 负向探针）

**Files:**
- Modify: `doc/ai-product/synapClaim/tools/_ai_native_readiness.py`

**Interfaces:**
- Consumes: 现有 `load_facts()` 的 `java_files` / `app_yml`；既有 `FLYWHEEL_DOWNSTREAM` 双向探针写法（第 280-281 行）。
- Produces: 两个新判据 `IMAGE_PARSE_WIRED` / `RAG_WIRED`（静态三要素：适配器类存在于 java_main 文件名列表 + application.yml 含门控键且默认 false + 适配器源文件含诚实降级码），5 个新探针进 `--self-test`。不改任何既有判据语义。

- [x] **Step 1: load_facts 增加事实（在 `calibration_wired` 计算之后、`e2e_raw` 之前插入）**

```python
    image_adapter = next((p for p in java_files if p.stem == "ImageDocumentParsePortAdapter"), None)
    rag_adapter = next((p for p in java_files if p.stem == "RagKnowledgeRetrievalPortAdapter"), None)
```

并在返回 dict 中追加四个键（放在 `"calibration_wired": calibration_wired,` 之后）：

```python
        "image_adapter_present": image_adapter is not None,
        "rag_adapter_present": rag_adapter is not None,
        "image_adapter_src": image_adapter.read_text(encoding="utf-8", errors="ignore") if image_adapter else "",
        "rag_adapter_src": rag_adapter.read_text(encoding="utf-8", errors="ignore") if rag_adapter else "",
```

- [x] **Step 2: 模块级辅助函数（放在 `read_text` 与 `load_facts` 之间）**

```python
def gate_default_false(app_yml: str, env_var: str) -> bool:
    """三要素之「门控默认 false」：yml 里 ${ENV:false} 占位缺省即成立（缺键/默认 true = 未接线或行为变更）。"""
    return bool(re.search(r"\$\{" + re.escape(env_var) + r":false\}", app_yml))
```

- [x] **Step 3: evaluate() 追加两个判据（在 FLYWHEEL_DOWNSTREAM 的 gates.append 之后、运行期证据段之前插入）**

```python
    # D2 图像感知：接线 + 门控默认关 + 降级诚实 三要素静态核验（任一缺失即 BLOCKED）。
    image_gate = gate_default_false(f["app_yml"], "BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED")
    image_degrade = all(
        code in f.get("image_adapter_src", "")
        for code in ("IMAGE_MOCK_REJECTED", "IMAGE_GATEWAY_UNAVAILABLE", "IMAGE_OUTPUT_INVALID"))
    if not f.get("image_adapter_present"):
        image_reason = "IMAGE_ADAPTER_MISSING"
    elif not image_gate:
        image_reason = "IMAGE_GATE_NOT_DEFAULT_OFF"
    elif not image_degrade:
        image_reason = "IMAGE_DEGRADE_CODES_MISSING"
    else:
        image_reason = "ADMITTED"
    gates.append(
        gate(
            "IMAGE_PARSE_WIRED",
            SATISFIED if image_reason == "ADMITTED" else BLOCKED,
            image_reason,
            "ImageDocumentParsePortAdapter + bone.claim.document-parse.image.enabled(默认 false) + 归因码",
        )
    )

    # D3 受控 RAG：接线 + 门控默认关 + 降级诚实 三要素静态核验。
    rag_gate = gate_default_false(f["app_yml"], "BONE_CLAIM_KNOWLEDGE_RAG_ENABLED")
    rag_degrade = all(
        code in f.get("rag_adapter_src", "")
        for code in ("RAG_CORPUS_MISSING", "RAG_RETRIEVAL_UNAVAILABLE", "clauseRef"))
    if not f.get("rag_adapter_present"):
        rag_reason = "RAG_ADAPTER_MISSING"
    elif not rag_gate:
        rag_reason = "RAG_GATE_NOT_DEFAULT_OFF"
    elif not rag_degrade:
        rag_reason = "RAG_DEGRADE_CODES_MISSING"
    else:
        rag_reason = "ADMITTED"
    gates.append(
        gate(
            "RAG_WIRED",
            SATISFIED if rag_reason == "ADMITTED" else BLOCKED,
            rag_reason,
            "RagKnowledgeRetrievalPortAdapter + bone.claim.knowledge.rag.enabled(默认 false) + 成因码",
        )
    )
```

- [x] **Step 4: self_test() 追加负向探针（在校准双向探针之后追加；gid 映射同步补齐）**

在 `probes.append(("校准生产消费方翻转（双向探针）", ...))` 之后：

```python
    probes.append(("图像适配器接线翻转（双向探针）", base["IMAGE_PARSE_WIRED"],
                   evaluate(dict(f, image_adapter_present=not f.get("image_adapter_present", False)))))
    probes.append(("图像门控默认值改 true（负向探针）", base["IMAGE_PARSE_WIRED"],
                   evaluate(dict(f, app_yml=f["app_yml"].replace(
                       "${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:false}",
                       "${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:true}")))))
    probes.append(("图像 mock 拒绝码被移除（降级诚实探针）", base["IMAGE_PARSE_WIRED"],
                   evaluate(dict(f, image_adapter_src=f["image_adapter_src"].replace(
                       "IMAGE_MOCK_REJECTED", "REDACTED")))))
    probes.append(("RAG 适配器接线翻转（双向探针）", base["RAG_WIRED"],
                   evaluate(dict(f, rag_adapter_present=not f.get("rag_adapter_present", False)))))
    probes.append(("RAG 门控默认值改 true（负向探针）", base["RAG_WIRED"],
                   evaluate(dict(f, app_yml=f["app_yml"].replace(
                       "${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:false}",
                       "${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:true}")))))
```

`self_test` 内的 `gid` 映射 dict 同步追加：

```python
            "图像适配器接线翻转（双向探针）": "IMAGE_PARSE_WIRED",
            "图像门控默认值改 true（负向探针）": "IMAGE_PARSE_WIRED",
            "图像 mock 拒绝码被移除（降级诚实探针）": "IMAGE_PARSE_WIRED",
            "RAG 适配器接线翻转（双向探针）": "RAG_WIRED",
            "RAG 门控默认值改 true（负向探针）": "RAG_WIRED",
```

- [x] **Step 5: 运行验证**

Run: `python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test`
Expected: 全部探针 `[OK]`（含既有 5 个），输出 `自测结果： PASS`，退出码 0。

Run: `python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py`
Expected: 判据数 12（原 10 + 2）；`IMAGE_PARSE_WIRED` / `RAG_WIRED` 为 SATISFIED（Task 5/7 已落地门控默认关 + 归因码）；`PROVIDER_CONFIGURED` 仍 BLOCKED（无 provider，如实）；`l3Claimable = false`（诚实结论）。

- [x] **Step 6: 提交**

```bash
git branch --show-current
git add doc/ai-product/synapClaim/tools/_ai_native_readiness.py
git commit -m "feat(readiness): 复核器新增 IMAGE_PARSE_WIRED/RAG_WIRED 判据与负向探针"
```

---

### Task 9: AI技术方案.md 回写与复核器主判据终跑存档

**Files:**
- Modify: `doc/ai-product/synapClaim/03-contract/AI技术方案.md`（§1.6 / §1.7）
- Create: `outputs/ai-native-readiness-2026-11-05.txt`（复核器输出存档产物）

**Interfaces:**
- Consumes: Task 8 复核器的实际输出（唯一事实源，文档只引用不复算）。
- Produces: §1.6/§1.7 按「实测态 vs 目标态」分线的记录；存档文件被文档引用。

- [x] **Step 1: 跑复核器并存档输出**

```bash
mkdir -p outputs
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py | tee outputs/ai-native-readiness-2026-11-05.txt
```
Expected: 判据 12 项计数行 + `l3Claimable = false`；存档文件落盘。

- [x] **Step 2: 回写 §1.6（实测态分线）**

读 `AI技术方案.md` §1.6/§1.7 现文（先 Read 全节再编辑），在 §1.6 的能力清单/成熟度处按「实测态 vs 目标态」追加（保留原有内容，不改历史结论）：

```markdown
### 1.6.x AI 原生收口实测态（2026-11-05，复核器 outputs/ai-native-readiness-2026-11-05.txt）

- 实测态（可编码缺口已补齐，SATISFIED）：FLYWHEEL_DOWNSTREAM（校准生产消费方 AiCalibrationReportApplicationService +
  GET /api/v1/claim-ops/ai-calibration/report）、IMAGE_PARSE_WIRED（ImageDocumentParsePortAdapter + 门控默认关 + 归因码）、
  RAG_WIRED（RagKnowledgeRetrievalPortAdapter + 门控默认关 + 成因码）。
- 实测态（外部前置，如实 BLOCKED/RUNTIME_ONLY）：真实 provider/GPU 未配置（PROVIDER_CONFIGURED）、
  ≥300 张独立盲标票据未达标（校准 verdict=INSUFFICIENT_SAMPLES）、医学条款语料未 ingest（RAG verdict 走 CORPUS_MISSING）、
  票据验真通道（发票验真外部依赖）。
- 目标态（不得写成现状）：l3Claimable=true 须全部判据 SATISFIED——当前 l3Claimable=false 为诚实结论；
  阈值建议仅在盲标样本 ≥300 且签核后才允许进入配置（AI 不碰钱）。
```

- [x] **Step 3: 回写 §1.7（复核器判据对照）**

在 §1.7 判据表/清单处追加两行（格式对齐既有条目）：

```markdown
- IMAGE_PARSE_WIRED：图像感知接线 + 门控默认关 + 降级诚实三要素静态核验（复核器 --self-test 负向探针覆盖变异翻转）。
- RAG_WIRED：受控 RAG 接线 + 门控默认关 + 成因码静态核验（同上）。
```

- [x] **Step 4: 一致性校验**

Run: `grep -c "l3Claimable" doc/ai-product/synapClaim/03-contract/AI技术方案.md outputs/ai-native-readiness-2026-11-05.txt`
Expected: 文档与存档均含 `l3Claimable`，且文档中不得出现「l3Claimable=true」表述（目标态一律显式标注「目标态」）。

- [x] **Step 5: 提交**

```bash
git branch --show-current
git add doc/ai-product/synapClaim/03-contract/AI技术方案.md outputs/ai-native-readiness-2026-11-05.txt
git commit -m "docs(synap-claim): AI技术方案 §1.6/§1.7 实测态回写与复核器终跑存档"
```

---

### Task 10: E2E 增量断言（校准报表端点 + 门控默认关行为面）

**Files:**
- Modify: `bone-frontend/e2e/synap-claim-e2e.cjs`

**Interfaces:**
- Consumes: Task 2 端点 `GET /api/v1/claim-ops/ai-calibration/report`；既有 `check()` / `result.runtime` 结构；既有 ai-readiness 响应。
- Produces: 新增 2 条 API 断言 + `result.runtime.calibrationVerdict` 产物字段（口径与复核器一致；无 provider 时 aiParticipated 如实，不 mock 任何响应）。门控默认值的静态核验由复核器（Task 8）承担，E2E 只断言运行行为一致性。

- [x] **Step 1: 插入断言（在运行期健康度 `check('运行期健康度 31 天窗口 200'...)` 与 `result.runtime = {...}` 赋值之后、`noAuth` 负向探针之前插入）**

```js
  // D1：校准报表端点（只读；样本不足时 verdict 如实、ECE/Brier 为 null，不编造数值）
  const calib = await ctx.get('/api/v1/claim-ops/ai-calibration/report', { headers: auth });
  const calibBody = await calib.json();
  const calibVerdict = calibBody?.data?.calibration?.verdict;
  check('校准报表端点 200 且 verdict 为受控枚举（不编造数值）',
    calib.status() === 200 && calibBody?.success === true
      && ['NO_EVIDENCE', 'UNTRUSTED_EVIDENCE', 'INSUFFICIENT_SAMPLES', 'SLICE_INCOMPLETE',
          'NO_SAFE_THRESHOLD', 'THRESHOLD_PROPOSED'].includes(calibVerdict),
    `status=${calib.status()} verdict=${calibVerdict} samples=${calibBody?.data?.calibration?.sampleCount}`);

  // D2/D3：门控默认关的行为面断言——就绪清单仍如实报未通过（行为与门控引入前一致，防假绿）。
  check('门控默认关：就绪清单行为与现状一致（如实报未通过）',
    readyBody?.data?.deploymentGatesPassed === false,
    `blockers=${readyBody?.data?.blockers?.length ?? 'n/a'}`);
```

- [x] **Step 2: runtime 段补充校准口径（在 `result.runtime = {...}` 对象的 `capabilityMissing: d.capabilityMissing ?? null,` 之后加一行）**

```js
    calibrationVerdict: calibVerdict ?? null,
```

注：`calib` 断言段必须在 `result.runtime` 赋值之前执行（插入点已保证）；若把断言移到赋值后，需同步把 `result.runtime.calibrationVerdict` 改为赋值后补写 `result.runtime.calibrationVerdict = calibVerdict ?? null;`。

- [x] **Step 3: 运行 E2E（需真实服务栈）**

前置（仓库内常驻脚本）：

```bash
bash bone-frontend/e2e/start-services.sh &
bash bone-frontend/e2e/start-frontend.sh &
node bone-frontend/e2e/synap-claim-e2e.cjs
```

Expected: 全部 API 断言 `[PASS]`（含 2 条新增），末行 `== 结果：PASS (n/n) ==`，退出码 0；产物 `outputs/claims-e2e-result.json` 的 `runtime.calibrationVerdict` 与复核器对校准的口径一致（无盲标数据时为 `NO_EVIDENCE`/`INSUFFICIENT_SAMPLES`，如实）。无 provider 时 `aiParticipated`/`participationRate` 如实为 0/低值，产物标 `mode: real-services-no-mock`，复核器 RUNTIME_MODEL_COVERAGE 相应判 BLOCKED（如实，不造假）。

- [x] **Step 4: 提交**

```bash
git branch --show-current
git add bone-frontend/e2e/synap-claim-e2e.cjs
git commit -m "test(e2e): synap-claim 校准报表端点与门控默认关行为面断言"
```

---

### Task 11: 全量质量门禁与循环复核收口

**Files:** 无新改动（只跑门禁 + 终轮复核；如门禁暴露问题，回对应任务修复后重跑本任务）。

**Interfaces:**
- Consumes: 全部前置任务产物。
- Produces: 三条门禁命令退出码 0；终轮复核报告（复核器主判据 + --self-test + E2E 三者结论一致，差异归零或如实登记）。

- [x] **Step 1: 格式化**

Run: `mvn spotless:apply`
Expected: BUILD SUCCESS，无未格式化差异（再跑 `git status --porcelain` 确认无意外文件改动）。

- [x] **Step 2: 模块测试**

Run: `mvn -pl bone-apps/synap-claim test`
Expected: BUILD SUCCESS，退出码 0（含 ArchUnit 分层、既有目录准入/盲标/决策图单测全绿——回归面全保留）。

- [x] **Step 3: 仓库自检脚本**

Run: `./scripts/check.sh`
Expected: 退出码 0（无输出拦截/失败项）。

- [x] **Step 4: 终轮循环复核（三判据一致）**

```bash
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py | tee outputs/ai-native-readiness-2026-11-05.txt
```
Expected: `--self-test` PASS（12 项探针全翻转）；主判据：FLYWHEEL_DOWNSTREAM / IMAGE_PARSE_WIRED / RAG_WIRED = SATISFIED，外部前置 RUNTIME_ONLY/BLOCKED 如实保留，`l3Claimable = false`。

- [x] **Step 5: 一致性核对与差异登记**

核对三处结论：① 复核器主判据（上一步输出）；② --self-test 全翻转；③ E2E 产物 `outputs/claims-e2e-result.json`（`passed=true`、`runtime.calibrationVerdict` 与校准实测一致、`runtime.aiParticipated` 如实）。
若发现差异：把差异逐条如实登记进 `doc/ai-product/synapClaim/03-contract/AI技术方案.md` §1.6 实测态小节（「差异登记」小标题），**不得改判据语义或造数据使其归零**。无新增可编码缺口即终轮收口（外部前置差异属如实登记，不阻塞）。

- [x] **Step 6: 收口提交（如有差异登记改动）**

```bash
git branch --show-current
git add doc/ai-product/synapClaim/03-contract/AI技术方案.md outputs/ai-native-readiness-2026-11-05.txt
git commit -m "docs(synap-claim): 循环复核收口——终轮复核报告与差异登记"
```

---

## Self-Review 记录（计划自检）

1. **Spec 覆盖**：tasks.md 13 项 ↔ 计划 11 个任务全对位（见「设计文档与任务对位」表）；设计文档 D1–D5 全部落地（D1→任务 1/2/3，D2→任务 4/5/6，D3→任务 7，D4→任务 8，D5→任务 9/10/11）。tasks.md 1.1 所述「实现 Brier/ECE 校准计算服务」由域内核 `AiConfidenceCalibration` 承担（已在仓库实测存在：ECE/Brier/阈值扫描/challenger 判定齐全），本计划落的是其缺失的生产消费方——与设计文档 §2 结论一致。
2. **占位符扫描**：无 TBD/TODO；两处「以源文件为准」注（Task 5 `LlmOutputGuard.requireFields` 返回类型、Task 6 `Evidence.register` 形参）均为已给出主方案 + 明确回退规则的编译期校验点，不是留白。
3. **类型一致性**：`findUnblindedPaired(int limit)` ↔ SQL `LIMIT :limit`；`BlindLabelCalibrationSamples.Mapping(samples, unparseableSamples)` ↔ Task 2 消费；`parseImage(DocType, String imageRef)` ↔ `EvidenceApplicationService.parseImage(long)` 调用；`userImage(String, String)` ↔ Task 5 适配器调用；归因码常量 ↔ Task 6 测试逐字透传断言 ↔ Task 8 复核器三要素字符串 ↔ Task 10 E2E verdict 枚举，均已交叉对齐。
4. **红线核对**：无 DDL、无 `V*__*.sql` 改动；两个门控默认 false 且 yml/注解/复核器三处同键（连字符形式）；决策图 DAG 未扩；校准/对比均为只读；pom 依赖变更已标 L3 审批提示。
