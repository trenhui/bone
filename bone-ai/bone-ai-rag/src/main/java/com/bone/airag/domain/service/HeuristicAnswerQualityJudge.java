package com.bone.airag.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 确定性回答质量裁判（零外部依赖，CI 构建内门禁用）。
 *
 * <p><b>算法</b>：字符二元组（CJK 友好，中英混排均可）包含度——
 *
 * <ul>
 *   <li>答案按句读与逗号（{@code 。！？；!?;\\n，,}）切为<b>断言</b>（逗号亦切分，使「未支撑断言」归因粒度更细）； 每个断言取与各上下文的最大二元组包含度， ≥
 *       {@link #supportThreshold} 计为「有据支撑」；
 *   <li>faithfulness = 有据断言数 / 有效断言数（无据 fail-closed 记 0，见 {@link AnswerQuality}）；
 *   <li>answerRelevance = 查询二元组被答案覆盖的比例（答非所问检测）；
 *   <li>contextPrecision = 真正支撑了断言的上下文占比（检索噪音检测）。
 * </ul>
 *
 * <p><b>确定性判官的结构性盲区（诚实声明，非缺陷隐瞒）</b>：二元组包含度是<b>词汇级</b>支撑，
 * 不理解否定反转（「无需支付违约金」与「应支付违约金」词汇高度重叠）、同义改写与跨句归纳。 因此分层：本实现守 CI 底线（检索回归 / 话题漂移幻觉），<b>语义级裁判由 LLM 判官</b>
 * （app 组合根经网关 {@code JudgePort} 计量）在离线校准与大规模回归中承担。两层齐备才是 2026 业界 RAG 评测的完整形态（RAGAS 指标族同样以 LLM
 * 裁判为主、确定性代理为辅）。
 */
public final class HeuristicAnswerQualityJudge implements AnswerQualityJudge {

  /** 断言被计为「有据支撑」的最小包含度（词汇级支撑的经验水位）。 */
  public static final double DEFAULT_SUPPORT_THRESHOLD = 0.25;

  private static final String CLAIM_DELIMITERS = "[。！？；!?;\\n，,]";

  private final double supportThreshold;

  /** 默认支撑阈值（{@value #DEFAULT_SUPPORT_THRESHOLD}）。 */
  public HeuristicAnswerQualityJudge() {
    this(DEFAULT_SUPPORT_THRESHOLD);
  }

  /**
   * @param supportThreshold 支撑阈值，须在 (0,1]——0 会使任意词汇重叠都计为有据（幻觉放行）， 1 要求逐字复述（同义表述误杀）
   */
  public HeuristicAnswerQualityJudge(double supportThreshold) {
    if (supportThreshold <= 0.0 || supportThreshold > 1.0) {
      throw new IllegalArgumentException("支撑阈值须在 (0,1]: " + supportThreshold);
    }
    this.supportThreshold = supportThreshold;
  }

  @Override
  public AnswerQuality judge(String query, List<String> contexts, String answer) {
    // fail-closed：无据（上下文空）或无产出（答案空）不计为「忠」
    if (contexts == null || contexts.isEmpty() || answer == null || answer.isBlank()) {
      return AnswerQuality.failClosed();
    }
    List<Set<String>> contextBigrams = new ArrayList<>(contexts.size());
    for (String ctx : contexts) {
      contextBigrams.add(bigrams(ctx == null ? "" : ctx));
    }

    List<String> claims = splitClaims(answer);
    int validClaims = 0;
    int supportedClaims = 0;
    boolean[] contextUsed = new boolean[contexts.size()];
    List<String> unsupported = new ArrayList<>();

    for (String claim : claims) {
      Set<String> claimBigrams = bigrams(claim);
      if (claimBigrams.isEmpty()) {
        continue; // 纯标点/虚词断言不计入分母（如「。。」）
      }
      validClaims++;
      double best = 0.0;
      int bestIdx = -1;
      for (int i = 0; i < contextBigrams.size(); i++) {
        double support = containment(claimBigrams, contextBigrams.get(i));
        if (support > best) {
          best = support;
          bestIdx = i;
        }
      }
      if (best >= supportThreshold) {
        supportedClaims++;
        if (bestIdx >= 0) {
          contextUsed[bestIdx] = true;
        }
      } else {
        unsupported.add(claim.strip());
      }
    }

    double faithfulness = validClaims == 0 ? 0.0 : (double) supportedClaims / validClaims;
    Set<String> queryBigrams = bigrams(query == null ? "" : query);
    Set<String> answerBigrams = bigrams(answer);
    double answerRelevance =
        queryBigrams.isEmpty() ? 0.0 : containment(queryBigrams, answerBigrams);
    long used = 0;
    for (boolean u : contextUsed) {
      if (u) {
        used++;
      }
    }
    double contextPrecision = (double) used / contexts.size();

    return new AnswerQuality(
        faithfulness, answerRelevance, contextPrecision, List.copyOf(unsupported));
  }

  /** 按句读切分答案断言（中文句读 + 西文句末符 + 换行）。 */
  static List<String> splitClaims(String answer) {
    List<String> claims = new ArrayList<>();
    for (String part : answer.split(CLAIM_DELIMITERS)) {
      String trimmed = part.strip();
      if (!trimmed.isEmpty()) {
        claims.add(trimmed);
      }
    }
    return claims;
  }

  /** 归一化取字符二元组：仅保留 CJK 与字母数字（小写化），其余（标点 / 空白 / emoji）剔除； 归一化后仅剩单字符时退化为单元组（短答案不丢信号）。 */
  static Set<String> bigrams(String text) {
    StringBuilder normalized = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isCjk(c) || Character.isLetterOrDigit(c)) {
        normalized.append(Character.toLowerCase(c));
      }
    }
    String s = normalized.toString();
    Set<String> result = new LinkedHashSet<>();
    if (s.length() == 1) {
      result.add(s);
      return result;
    }
    for (int i = 0; i + 1 < s.length(); i++) {
      result.add(s.substring(i, i + 2));
    }
    return result;
  }

  private static boolean isCjk(char c) {
    return c >= '\u4e00' && c <= '\u9fff';
  }

  /** 包含度：|a ∩ b| / |a|（a 被 b 覆盖的比例；a 为空返回 0）。 */
  static double containment(Set<String> a, Set<String> b) {
    if (a.isEmpty()) {
      return 0.0;
    }
    int hit = 0;
    for (String gram : a) {
      if (b.contains(gram)) {
        hit++;
      }
    }
    return (double) hit / a.size();
  }

  /** 支撑阈值（包内可见性供测试与文档引用）。 */
  public double supportThreshold() {
    return supportThreshold;
  }

  @Override
  public String toString() {
    return String.format(
        Locale.ROOT, "HeuristicAnswerQualityJudge{supportThreshold=%.2f}", supportThreshold);
  }
}
