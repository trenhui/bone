package com.bone.airag.domain.gateway;

import com.bone.airag.domain.model.GroundingJudgeVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.List;

/**
 * 语义接地裁判端口（faithfulness 判定；R5，2026-10-24 落地）。
 *
 * <p><b>为什么在确定性守卫之外还要它</b>：{@code GroundingGuard} 是词面级校验——改写过的同义句可能误判（假阴，方向安全），
 * 而「大量复用上下文词汇的拼接句」可能蒙混过关（假阳，方向危险）：模型用上下文里的实体词拼出一句上下文并没给出的结论， 词覆盖率高、数字也全对，词面守卫放行，强监管场景（保险条款 /
 * 合同审阅）这就是合规红线。语义裁判以 NLI 口径逐句判定「这句话上下文是否支撑」，补的正是这一刀。
 *
 * <p><b>端口契约（三态，见 {@link GroundingJudgeVerdict}）</b>：
 *
 * <ul>
 *   <li>实现方判「有陈述无支撑」⇒ {@code supported=false, reason=UNSUPPORTED}，调用方拒答；
 *   <li>实现方自身故障（provider 不可用 / 治理拒绝 / 输出不可解析）⇒ {@code reason=JUDGE_UNAVAILABLE}，
 *       <b>不得抛异常</b>——裁判故障把回答链打断等于可用性事故；也不得返回 {@code UNSUPPORTED} 冒充判定——那是用可用性故障伪造合规拒答，监控会失真；
 *   <li>提示词里不确定时一律判无支撑（裁判自身的 fail-closed），实现方不得「猜着放行」。
 * </ul>
 *
 * <p>组合根默认 {@code NoOpGroundingJudge}（恒放行，语义层关闭）；接入方以 {@code @Bean} 覆盖为 网关卡面适配（{@code
 * LlmGroundingJudgeAdapter}，模型调用唯一入站，继承红线 / 配额 / 注入检测 / 脱敏治理链）后生效。
 *
 * @param question 用户原始问题（裁判需要知道「答的是什么」）
 * @param answer 待判定的模型输出（尚未返回用户）
 * @param contexts 本次检索实际喂给模型的上下文（编号即引用序号 1..n）
 */
public interface GroundingJudgePort {

  GroundingJudgeVerdict judge(String question, String answer, List<RagResult> contexts);
}
