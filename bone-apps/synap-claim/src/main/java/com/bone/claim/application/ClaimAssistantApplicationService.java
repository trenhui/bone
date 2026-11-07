package com.bone.claim.application;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.AssistantQuery;
import com.bone.claim.application.port.out.AiGatewayPort.Completion;
import com.bone.claim.application.port.out.PolicyQueryPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 智能理赔助手（WS-B P1 自然语言入口 + 受控工具面）应用层。
 *
 * <p><b>分层纪律</b>：仅依赖出站 port 防腐层（{@link AiGatewayPort} / {@link PolicyQueryPort}），不直触
 * infrastructure（DDD P0-1 / E-13.3）。
 *
 * <p><b>诚实边界</b>：网关未配置 / 不可用时 {@link Completion#available()} 为 false，服务如实透传「AI 未就绪」， 绝不拿空文本假装已回答（与
 * assess 的 fail-closed 同源）。受控工具面（{@link #listTools} / {@link #runTool}）是<b>确定性数据访问</b>， 不依赖 LLM，L0
 * 即可用，用于把真实业务数据作为助手的事实底座。
 */
@Service
@RequiredArgsConstructor
public class ClaimAssistantApplicationService {

  private final AiGatewayPort aiGatewayPort;
  private final PolicyQueryPort policyQueryPort;

  /** 以自然语言提问，返回结构化答复（含 AI 是否就绪与诚实说明）。 */
  public AssistantReply assist(String question, String claimId) {
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question 不得为空");
    }
    Completion completion = aiGatewayPort.answer(new AssistantQuery(question, claimId));
    String honestNote =
        completion.available() ? "" : "AI 网关未配置或未就绪，无法生成自然语言答复；请联系运维接入 bone.ai.gateway.base-url。";
    return new AssistantReply(
        completion.text(),
        completion.available(),
        completion.modelVersion(),
        completion.sources(),
        honestNote);
  }

  /** 助手答复：answer=正文；aiAvailable=网关是否就绪；honestNote=未就绪时的如实说明。 */
  public record AssistantReply(
      String answer,
      boolean aiAvailable,
      String modelVersion,
      List<String> sources,
      String honestNote) {}

  /**
   * 受控工具面：列出助手可调用的确定性工具（WS-B P1 Tool Use）。
   *
   * <p>这些工具直连本地数据 port（保单查询），无需 LLM，L0 即可用；是「把真实业务数据作为助手事实底座」的受控入口。 网关不可用时工具仍可用，二者解耦。
   */
  public List<ToolDescriptor> listTools() {
    return List.of(
        new ToolDescriptor(
            "policy-lookup",
            "保单生效校验",
            "按平台保单号查询「此刻生效中」的保单快照（生效区间、被保人集合、计划版本）。",
            true,
            List.of(
                new ToolDescriptor.ToolParam("policyNo", "string", "平台保单号", true),
                new ToolDescriptor.ToolParam("planId", "long", "计划 ID（可选，用于消歧）", false))),
        new ToolDescriptor(
            "policy-lookup-by-insurer",
            "按保司保单号查询",
            "按保司侧保单号查询可能多条生效中保单（团险/续保场景），最终记哪张保单须按被保人收敛。",
            true,
            List.of(
                new ToolDescriptor.ToolParam("insurerPolicyNo", "string", "保司侧保单号", true),
                new ToolDescriptor.ToolParam("planId", "long", "计划 ID（可选）", false))));
  }

  /** 确定性执行受控工具，返回结构化结果（不依赖 LLM）。未知 toolId 抛 {@link IllegalArgumentException}。 */
  public ToolExecutionResult runTool(String toolId, Map<String, String> args) {
    return switch (toolId) {
      case "policy-lookup" -> runPolicyLookup(args);
      case "policy-lookup-by-insurer" -> runPolicyLookupByInsurer(args);
      default -> throw new IllegalArgumentException("未知工具: " + toolId);
    };
  }

  private ToolExecutionResult runPolicyLookup(Map<String, String> args) {
    String policyNo = args.get("policyNo");
    if (policyNo == null || policyNo.isBlank()) {
      return new ToolExecutionResult(false, "", List.of(), "缺少必填参数 policyNo");
    }
    Long planId = parseLong(args.get("planId"));
    Optional<PolicyQueryPort.PolicySnapshot> snap =
        policyQueryPort.findActivePolicy(policyNo, planId);
    if (snap.isEmpty()) {
      return new ToolExecutionResult(false, "", List.of(), "未查到生效中保单（已过期或不存在）");
    }
    PolicyQueryPort.PolicySnapshot s = snap.get();
    String text =
        String.format(
            "保单 %s（保司号 %s，计划 %s v%s）生效区间 %s ~ %s，承保被保人 %d 人。",
            s.policyNo(),
            s.insurerPolicyNo(),
            s.planId(),
            s.planVersion(),
            s.effectiveFrom(),
            s.effectiveTo(),
            s.insuredIdHashes().size());
    return new ToolExecutionResult(true, text, List.of(), "");
  }

  private ToolExecutionResult runPolicyLookupByInsurer(Map<String, String> args) {
    String insurerNo = args.get("insurerPolicyNo");
    if (insurerNo == null || insurerNo.isBlank()) {
      return new ToolExecutionResult(false, "", List.of(), "缺少必填参数 insurerPolicyNo");
    }
    Long planId = parseLong(args.get("planId"));
    List<PolicyQueryPort.PolicySnapshot> list =
        policyQueryPort.findActivePoliciesByInsurerNo(insurerNo, planId);
    if (list.isEmpty()) {
      return new ToolExecutionResult(false, "", List.of(), "未查到生效中保单");
    }
    String text =
        String.format(
            "命中 %d 张生效中保单，需按被保人清单收敛：%s",
            list.size(),
            list.stream()
                .map(PolicyQueryPort.PolicySnapshot::policyNo)
                .collect(Collectors.joining(", ")));
    return new ToolExecutionResult(true, text, List.of(), "多条命中，最终记账保单须由应用层按被保人收敛，禁止静默取首条");
  }

  private static Long parseLong(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /** 工具描述：id/展示名/说明/是否可用/入参清单。 */
  public record ToolDescriptor(
      String id,
      String displayName,
      String description,
      boolean available,
      List<ToolParam> params) {
    /** 工具入参：name/类型/说明/是否必填。 */
    public record ToolParam(String name, String type, String description, boolean required) {}
  }

  /** 工具执行结果：success=是否成功；text=正文；sources=引用；honestNote=失败/注意事项说明。 */
  public record ToolExecutionResult(
      boolean success, String text, List<String> sources, String honestNote) {}
}
