package com.bone.aiapp.config;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.airag.domain.gateway.AnswerGenerationException;
import com.bone.airag.domain.gateway.AnswerGenerationPort;
import com.bone.core.exception.BizException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RAG 回答链生成端口适配器（R1「接 G」组合根接线）：rag 的 {@link AnswerGenerationPort} → 网关门面。
 *
 * <p><b>为什么必须走门面而非直连 {@code LlmClient}</b>（与 {@code GatewayJudgeAdapter} 同构）：生成也是模型调用，直连 SPI 会绕过
 * 成本红线、不发证据、不进 trace 落盘——在「模型调用唯一入站」不变式上开侧门。经门面后，RAG 生成自动继承网关治理链： 红线短路 → 租户配额 → <b>提示注入检测（user
 * 原文，即数据围栏包裹的检索上下文——R1 子项④ 间接注入纵深防护随本适配器接线而生效）</b> → 场景档位路由 → 出口脱敏 → 缓存 → 输出内容安全。
 *
 * <p><b>故障收敛</b>：网关治理拒绝（注入疑似 / 配额 / 红线 / 内容安全）或 provider 故障抛 {@link BizException}，此处保真透传 HTTP 语义与
 * errorCode 后以 {@link AnswerGenerationException} 上抛——由 {@code RagAnswerService} 收敛为可控拒答（{@code
 * RagAnswerOutcome.unavailable}），不把「这次答不了」升级成业务流程中断。适配器不重做治理（两套策略会漂移），也不裁剪模型输出 （接地校验必须针对模型真实输出）。
 */
@Component
public class RagAnswerGenerationAdapter implements AnswerGenerationPort {

  private final LlmGatewayApplicationService gatewayService;
  private final BoneAiProperties properties;

  public RagAnswerGenerationAdapter(
      LlmGatewayApplicationService gatewayService, BoneAiProperties properties) {
    this.gatewayService = gatewayService;
    this.properties = properties;
  }

  @Override
  public String generate(String systemPrompt, String userPrompt) {
    try {
      LlmResponse response =
          gatewayService.invoke(
              LlmRequest.of(
                  properties.getRagAnswerModel(),
                  List.of(LlmMessage.system(systemPrompt), LlmMessage.user(userPrompt))));
      return response.content();
    } catch (BizException e) {
      throw AnswerGenerationException.from(e);
    }
  }
}
