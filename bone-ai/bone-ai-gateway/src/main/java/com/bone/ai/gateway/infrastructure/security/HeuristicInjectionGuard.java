package com.bone.ai.gateway.infrastructure.security;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.gateway.PromptInjectionGuard;
import com.bone.core.exception.BizException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 提示注入防护（默认启发式实现）：轻量关键词命中检测，零依赖。
 *
 * <p><b>误伤与默认关闭</b>：关键词规则必然有误伤（保险条款文本里偶含「忽略 / 系统提示」等词），所以装配层默认不启用 （{@code
 * bone.ai.gateway.prompt-injection-guard-enabled=false}）；需要语义级检测时以 {@code @Bean} 覆盖为模型分类器 /
 * 云端护栏（属外部依赖，AGENTS L3）。启用方需按自身语料审视 {@link #DEFAULT_PATTERNS} 是否过宽。
 *
 * <p><b>检测在脱敏之前</b>：门面在出口脱敏前对 user 原文检测，避免掩码改写影响指令特征命中。
 *
 * <p>归一化：小写 + 压缩连续空白后子串匹配；模式本身也先归一化，调用方可传任意大小写 / 空白风格的自定义模式。
 */
public class HeuristicInjectionGuard implements PromptInjectionGuard {

  /** 默认注入特征（中英文常见劫持指令；措辞偏具体以降低误伤，如「忽略之前的」而非单「忽略」）。 */
  public static final List<String> DEFAULT_PATTERNS =
      List.of(
          // 英文
          "ignore previous",
          "ignore all previous",
          "ignore the above",
          "disregard previous",
          "disregard the above",
          "forget your instructions",
          "forget everything above",
          "new instructions",
          "system prompt",
          "system prompt is",
          "developer mode",
          "jailbreak",
          "do anything now",
          "act as if you",
          // 中文（措辞偏具体，规避「系统提示音」「无视风险」等业务文本误伤）
          "忽略之前的",
          "忽略上述",
          "忽略前面",
          "忽略你之前",
          "无视之前的",
          "无视你的",
          "忘记你的",
          "忘记之前",
          "新的指令",
          "你的系统提示",
          "越狱",
          "现在你是",
          "重新设定",
          "扮演成");

  private final Set<String> patterns;

  public HeuristicInjectionGuard() {
    this(DEFAULT_PATTERNS);
  }

  public HeuristicInjectionGuard(Collection<String> customPatterns) {
    if (customPatterns == null || customPatterns.isEmpty()) {
      throw new IllegalArgumentException("注入特征集合不可为空");
    }
    this.patterns =
        customPatterns.stream()
            .filter(p -> p != null && !p.isBlank())
            .map(p -> normalize(p))
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void check(String userMessage, String traceId) {
    if (userMessage == null || userMessage.isBlank()) {
      return;
    }
    String norm = normalize(userMessage);
    for (String pattern : patterns) {
      if (norm.contains(pattern)) {
        throw new BizException(
            400,
            "疑似提示注入（命中特征: " + pattern + "）: traceId=" + traceId,
            AiGatewayErrorCodes.INJECTION_SUSPECTED);
      }
    }
  }

  private static String normalize(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }
}
