package com.bone.claim.application;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.AssistantQuery;
import com.bone.claim.application.port.out.AiGatewayPort.Completion;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 智能理赔助手（WS-B P1 自然语言入口）应用层。
 *
 * <p><b>分层纪律</b>：仅依赖 {@link AiGatewayPort} 防腐层，不直触 infrastructure（DDD P0-1 / E-13.3）。
 *
 * <p><b>诚实边界</b>：网关未配置 / 不可用时 {@link Completion#available()} 为 false，服务如实透传「AI 未就绪」， 绝不拿空文本假装已回答（与
 * assess 的 fail-closed 同源）。本服务不写库、不参与金额或资金判定。
 */
@Service
@RequiredArgsConstructor
public class ClaimAssistantApplicationService {

  private final AiGatewayPort aiGatewayPort;

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
}
