package com.bone.workflow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 审批流流程定义：一次「发布」产出的不可变快照。
 *
 * <p>纯数据载体，不含任何 IO / Spring 依赖，便于发布期校验与运行期求值各自单测。
 *
 * <p><b>与平台元数据的关系</b>：流程定义本身也是平台实体（{@code wf_process_def}，整图快照存 {@code
 * definition_json}；节点/边/指派规则不拆物理表——拆表只增加 join 与版本一致性负担，见设计文档 §3），本类只是它的<b>内存投影</b>——把一次运行所需的全部定义
 * 聚成一个不可变对象，避免运行期反复查库、也避免运行途中定义被改动导致同一单据前后走不同分支。
 *
 * <p><b>为什么 {@code ignoreUnknown = true} 必须写在这里，而不是依赖 ObjectMapper 的默认配置</b>： 定义 JSON 除引擎字段外还承载
 * <b>设计器专用段</b> {@code ui}（画布坐标、折叠状态；先例是 BPMN 的 {@code BPMNDiagram} 段——与语义同存一个
 * artifact、引擎解析时忽略，二者同版本同回滚）。若不在此显式容忍，本类的可读性就取决于「容器注入了哪个 ObjectMapper」：Spring Boot 默认的 {@code
 * FAIL_ON_UNKNOWN_PROPERTIES=false} 能读，任何人 {@code new ObjectMapper()}（严格模式）就集体读不回来——2026-10-27
 * 在测试上下文里真实复现过（{@code Unrecognized field "ui"}），而报错会出现在「发起流程」而不是「改了配置」，极难定位。把容忍写在类型上，配置就再也改不坏它。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDefinition(
    String key,
    String name,
    int version,
    Integer engineSchemaVersion,
    List<Node> nodes,
    List<Edge> edges,
    Map<String, List<AssignmentRule>> assignments) {

  /**
   * 当前引擎能够正确解释、求值的流程定义「格式版本」。
   *
   * <p><b>为什么需要它（诊断 P2-3）</b>：流程定义以整图 JSON 落库，引擎随版本演进可能改变 JSON 结构语义。
   * 若定义不携带它所能表达的「格式版本」，引擎升级后旧实例加载时结构不兼容会<b>静默炸</b>——而审批是低频关键路径， 静默炸 =
   * 单据无法审批且无明确报错。故定义内嵌格式版本，加载/校验时做兼容判定，不支持则 {@code fail-loud}（WF-017），把「悄悄坏掉」变成「明确拒绝」。
   * 业界做法同源：Camunda 将引擎版本与流程定义版本分离管理。
   */
  public static final int CURRENT_ENGINE_SCHEMA_VERSION = 1;

  /** 引擎仍保证向后兼容的最低定义格式版本。 */
  public static final int MIN_SUPPORTED_ENGINE_SCHEMA_VERSION = 1;

  public WorkflowDefinition {
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
    edges = edges == null ? List.of() : List.copyOf(edges);
    assignments = assignments == null ? Map.of() : Map.copyOf(assignments);
    // 缺省（历史定义 JSON 未带该字段）按当前版本处理，避免存量定义集体失效；
    // 显式写出比依赖 Jackson 默认更稳，也保证 record 不可变分量恒非空。
    if (engineSchemaVersion == null) {
      engineSchemaVersion = CURRENT_ENGINE_SCHEMA_VERSION;
    }
  }

  /**
   * 便捷构造：展示名缺省与标识同值，格式版本取 {@link #CURRENT_ENGINE_SCHEMA_VERSION}。
   *
   * <p>流程定义的展示名在多数场景就等于它的标识（报销、采购……），单独再传一遍是噪音；但保留该分量是因为建模界面 允许给流程起一个更友好的中文名。
   */
  public WorkflowDefinition(
      String key,
      int version,
      List<Node> nodes,
      List<Edge> edges,
      Map<String, List<AssignmentRule>> assignments) {
    this(key, key, version, CURRENT_ENGINE_SCHEMA_VERSION, nodes, edges, assignments);
  }

  /**
   * 便捷构造：显式展示名，格式版本取 {@link #CURRENT_ENGINE_SCHEMA_VERSION}。
   *
   * <p>与历史 6 参构造（{@code key,name,version,nodes,edges,assignments}）签名一致，保证既有调用方零改动。
   */
  public WorkflowDefinition(
      String key,
      String name,
      int version,
      List<Node> nodes,
      List<Edge> edges,
      Map<String, List<AssignmentRule>> assignments) {
    this(key, name, version, CURRENT_ENGINE_SCHEMA_VERSION, nodes, edges, assignments);
  }

  /** 节点类型。 */
  public enum NodeType {
    /** 发起（流程入口，唯一）。 */
    START,
    /** 审批（单人 / 会签 / 或签）。 */
    APPROVE,
    /** 条件分支（按业务数据选择唯一后继）。 */
    CONDITION,
    /** 抄送（不阻塞流程）。 */
    CC,
    /** 结束（终态，无后继）。 */
    END
  }

  /** 审批节点的完成判定方式。 */
  public enum ApprovalMode {
    /** 任一人通过即通过（单签）。 */
    SINGLE,
    /** 全部通过才通过（会签）。 */
    ALL,
    /** 任一通过即通过（或签）。 */
    ANY,
    /**
     * 比例会签（N/M）：同一节点至少 {@code N} 人审批通过即通过，{@code N} 由节点 {@code quorum} 指定。
     *
     * <p>语义要点（钉钉/飞书「人数会签」同源）：推进条件 = 已通过人数 ≥ {@code quorum}；终止条件 = 即便剩余待办全部通过也凑不齐 {@code quorum}（已通过
     * + 待决 < {@code quorum}）⇒ 整单驳回。单条驳回不立即终止（只要法定人数仍可达），容忍「少数人反对」。
     */
    RATIO
  }

  /**
   * 流程节点。
   *
   * @param code 节点编码（流程内唯一）
   * @param type 节点类型
   * @param mode 审批完成判定方式（仅 {@link NodeType#APPROVE} 有意义，其余类型须为 {@code null}）
   * @param label 展示名
   * @param quorum 比例会签法定人数（仅 {@link ApprovalMode#RATIO} 有意义，其余模式为 {@code null}）：同一节点至少 {@code
   *     quorum} 人通过即通过
   */
  public record Node(String code, NodeType type, ApprovalMode mode, String label, Integer quorum) {
    @JsonCreator
    public Node(
        @JsonProperty("code") String code,
        @JsonProperty("type") NodeType type,
        @JsonProperty("mode") ApprovalMode mode,
        @JsonProperty("label") String label,
        @JsonProperty("quorum") Integer quorum) {
      this.code = code;
      this.type = type;
      this.mode = mode;
      this.label = label;
      this.quorum = quorum;
    }

    /** 便捷构造：不含法定人数（非比例会签节点），保证既有 4 参调用零改动、历史 JSON 缺 {@code quorum} 可反序列化。 */
    public Node(String code, NodeType type, ApprovalMode mode, String label) {
      this(code, type, mode, label, null);
    }
  }

  /**
   * 节点出边（transition）。
   *
   * @param from 源节点编码
   * @param to 目标节点编码
   * @param when 生效条件（结构化 JSON，与平台 UI 协议规则同源）；{@code null}/空对象 = <b>默认边（else）</b>
   * @param label 分支名（展示与审计用）
   */
  public record Edge(String from, String to, JsonNode when, String label) {

    /**
     * JSON 往返稳定性归一：把 Jackson 读回的 {@link com.fasterxml.jackson.databind.node.NullNode} 统一收敛为 {@code
     * null}。
     *
     * <p><b>为什么必须</b>：手写构造边时「无条件」传的是 {@code null}，而经 {@code definition_json} 落库再读回时， Jackson 会把
     * JSON 的 {@code null} 反序列化成 {@code NullNode}（不是 {@code null}）。两者在 record 的 {@code equals}
     * 下<b>不相等</b>，于是「发布时构造的定义」与「读回的定义」判为不同对象—— 表现为定义校验 / 去重 / 版本比对静默失效，且打印出来的两条边一模一样，极难定位。
     *
     * <p>{@link #isDefault()} 已把两种形态都判为默认边，故语义不受影响；此处归一是为让<b>结构相等性</b>也稳定。
     */
    public Edge {
      if (when != null && when.isNull()) {
        when = null;
      }
    }

    /**
     * 是否默认边（else）。
     *
     * <p><b>必须 {@code @JsonIgnore}</b>：这是派生判定（由 {@code when} 推导），不是数据分量。不加注解时 Jackson 会按 getter
     * 约定把它序列化成 {@code "default"} 字段，而反序列化找不到对应构造参数 ⇒ {@code UnrecognizedPropertyException}——经
     * {@code definition_json} 落库的定义<b>全部读不回来</b>（start 必崩）。该缺陷只在「序列化 → 存库 →
     * 读回」往返时现形，内存构造的测试永远绿，故在此钉死。
     */
    @JsonIgnore
    public boolean isDefault() {
      return when == null
          || when.isNull()
          || when.isMissingNode()
          || (when.isObject() && when.isEmpty());
    }
  }

  /**
   * 审批人指派规则。
   *
   * @param strategy 指派策略（见 {@link #SUPPORTED_STRATEGIES}）
   * @param params 策略参数（如 {@code level} / {@code roleCode} / {@code fieldCode}）
   */
  public record AssignmentRule(String strategy, Map<String, Object> params) {}

  /** 允许的指派策略。 */
  public static final List<String> SUPPORTED_STRATEGIES =
      List.of("LINE_MANAGER", "DEPT_HEAD", "ROLE", "USER", "FIELD", "INITIATOR_SELECT", "LEVEL_UP");

  /** 按编码取节点。 */
  public java.util.Optional<Node> node(String code) {
    return nodes.stream().filter(n -> n.code().equals(code)).findFirst();
  }

  /** 取某节点的出边（保持定义顺序）。 */
  public List<Edge> outEdges(String nodeCode) {
    return edges.stream().filter(e -> e.from().equals(nodeCode)).toList();
  }

  /** 取某节点的入边。 */
  public List<Edge> inEdges(String nodeCode) {
    return edges.stream().filter(e -> e.to().equals(nodeCode)).toList();
  }
}
