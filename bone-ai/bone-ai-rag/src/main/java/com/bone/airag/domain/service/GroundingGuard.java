package com.bone.airag.domain.service;

import com.bone.airag.domain.model.GroundingVerdict;
import com.bone.airag.domain.model.RagResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成侧接地守卫（「无出处不得生成」的运行时校验）。
 *
 * <p><b>为什么必须有它</b>：检索侧的引用强制只保证上下文可溯源（无 {@code clauseRef}/span 的切片直接丢弃）， 但生成阶段 LLM
 * 仍可以凭自身知识写出上下文里没有的金额、期限、责任认定——强监管场景（保险条款问答、合同审阅） 这是合规红线，不是质量问题。本守卫在生成之后、返回用户之前做确定性校验，{@code
 * grounded=false} 即拒答。
 *
 * <p><b>四条校验（全部确定性、零外部依赖）</b>：
 *
 * <ol>
 *   <li><b>上下文非空</b>：无上下文却给出答案 ⇒ {@code NO_CONTEXT}（无据可依必然编造）。
 *   <li><b>至少一处引用</b>：答案不含任何 {@code [n]} 标记 ⇒ {@code NO_CITATION}（强制引用是契约，不是建议）。
 *   <li><b>引用序号有效</b>：{@code [n]} 必须落在本次上下文条数内，越界即幻觉引用 ⇒ {@code INVALID_CITATION}。
 *   <li><b>逐句可支撑</b>：每句先剥掉引用标记，按 {@link CjkTokenizer} 分词后计算「句内词被所选上下文覆盖的比例」；
 *       同时校验句中的**数字**必须能在上下文中找到（金额/比例/期限是最容易被编造的字段，词面相似但数字不同的句子 往往正是幻觉）。任一条不满足即该句无支撑。
 * </ol>
 *
 * <p><b>已知边界（不要高估它）</b>：这是<b>词面级</b>接地校验，不是语义蕴含判定——改写过的同义句可能被判无支撑
 * （假阴，方向安全：宁可拒答），而大量复用上下文词汇的拼接句可能蒙混过关（假阳）。终局须叠加 NLI / LLM 判定 （faithfulness 指标，见 {@code
 * eval/README.md}）；中文数字（「千分之一」）当前不做归一化，故上下文与答案的 中文数字差异不会被本守卫拦下，需靠词面比与后续 NLI 兜底。
 *
 * <p>无第三方依赖；判定逻辑属领域规则，置于 domain 层（应用服务不得承载，ArchUnit 门禁约束）。
 */
public class GroundingGuard {

  /** 判定通过。 */
  public static final String REASON_PASS = "PASS";

  /** 无上下文（或答案为空）——无据可依，fail-closed。 */
  public static final String REASON_NO_CONTEXT = "NO_CONTEXT";

  /** 答案不含任何引用标记——违反强制引用契约。 */
  public static final String REASON_NO_CITATION = "NO_CITATION";

  /** 引用序号越界/不存在（幻觉引用）。 */
  public static final String REASON_INVALID_CITATION = "INVALID_CITATION";

  /** 存在无上下文支撑的句子（编造陈述或编造数字）。 */
  public static final String REASON_UNSUPPORTED_CLAIM = "UNSUPPORTED_CLAIM";

  /**
   * 生成链路故障（非接地判定失败）：provider 不可用 / 超时 / 配额或注入治理拒绝等，守卫未实际执行。
   *
   * <p>与五个接地原因码并列但语义不同：它是「没判」而非「判了不过」——编排层把上游故障收敛为拒答结果时携带，
   * 便于监控区分「模型编造被拦」与「模型不可用」两种拒答（前者是守卫在工作，后者是生成链路故障）。
   */
  public static final String REASON_GENERATION_UNAVAILABLE = "GENERATION_UNAVAILABLE";

  /** 引用标记：{@code [1]}、{@code [1,3]}、{@code [1，3]}、{@code [1、3]} 视为一组多引用。 */
  private static final Pattern CITATION = Pattern.compile("\\[(\\d+(?:\\s*[,，、]\\s*\\d+)*)]");

  /** 阿拉伯数字（含小数）；中文数字不做归一化（见类注释「已知边界」）。 */
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

  /** 句末切分：中英文句号/问号/叹号/分号/换行（条款文本常见分号并列，按句处理更严格）。 */
  private static final Pattern SENTENCE_BREAK = Pattern.compile("[。！？!?；;\\n]");

  /** 无支撑句子在判定结果中的截断长度（避免把整段答案塞进日志）。 */
  private static final int MAX_SNIPPET = 40;

  private final double minSupportRatio;
  private final double maxUnsupportedRatio;

  /**
   * @param minSupportRatio 单句「词被上下文覆盖的比例」下限，低于即判该句无支撑（默认 0.6）
   * @param maxUnsupportedRatio 允许的无支撑句占比上限，超过即整体拒答（默认 0 = 零容忍）
   */
  public GroundingGuard(double minSupportRatio, double maxUnsupportedRatio) {
    this.minSupportRatio = clamp(minSupportRatio);
    this.maxUnsupportedRatio = clamp(maxUnsupportedRatio);
  }

  /** 校验答案是否接地于给定上下文；任何不确定情形一律 fail-closed。 */
  public GroundingVerdict verify(String answer, List<RagResult> contexts) {
    if (contexts == null || contexts.isEmpty() || isBlank(answer)) {
      return verdict(false, REASON_NO_CONTEXT, 0, 0, 0.0, List.of(), List.of());
    }

    List<String> invalidCitations = new ArrayList<>();
    Set<Integer> citedIndexes = new LinkedHashSet<>();
    collectCitations(answer, contexts.size(), citedIndexes, invalidCitations);

    // 无引用即无出处：强制引用是契约，不是「有更好」的加分项
    if (citedIndexes.isEmpty() && invalidCitations.isEmpty()) {
      return verdict(false, REASON_NO_CITATION, 0, 0, 0.0, List.of(), List.of());
    }

    List<String> unsupported = new ArrayList<>();
    int sentenceCount = 0;
    double supportSum = 0.0;
    for (String raw : SENTENCE_BREAK.split(answer)) {
      String stripped = CITATION.matcher(raw).replaceAll(" ").trim();
      if (countMeaningfulChars(stripped) < 2) {
        continue;
      }
      sentenceCount++;
      Set<Integer> localCited = new LinkedHashSet<>();
      collectCitations(raw, contexts.size(), localCited, new ArrayList<>());
      List<RagResult> scope = localCited.isEmpty() ? contexts : pick(contexts, localCited);
      double support = supportRatio(stripped, scope);
      supportSum += support;
      if (support < minSupportRatio || hasUngroundedNumber(stripped, scope)) {
        unsupported.add(snippet(stripped));
      }
    }

    double meanSupport = sentenceCount == 0 ? 0.0 : supportSum / sentenceCount;
    if (!invalidCitations.isEmpty()) {
      return verdict(
          false,
          REASON_INVALID_CITATION,
          sentenceCount,
          unsupported.size(),
          meanSupport,
          invalidCitations,
          unsupported);
    }
    // 全部句子都被过滤（如答案只有引用标记）：仍按「无实质陈述」fail-closed
    if (sentenceCount == 0) {
      return verdict(false, REASON_NO_CITATION, 0, 0, meanSupport, List.of(), List.of());
    }
    double unsupportedRatio = (double) unsupported.size() / sentenceCount;
    boolean grounded = unsupportedRatio <= maxUnsupportedRatio;
    String reason = grounded ? REASON_PASS : REASON_UNSUPPORTED_CLAIM;
    return verdict(
        grounded, reason, sentenceCount, unsupported.size(), meanSupport, List.of(), unsupported);
  }

  /** 解析引用：合法序号进 {@code cited}，越界序号进 {@code invalid}（去重保序）。 */
  private static void collectCitations(
      String text, int contextCount, Set<Integer> cited, List<String> invalid) {
    Matcher m = CITATION.matcher(text);
    while (m.find()) {
      for (String part : m.group(1).split("\\s*[,，、]\\s*")) {
        int idx;
        try {
          idx = Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
          continue;
        }
        if (idx >= 1 && idx <= contextCount) {
          cited.add(idx);
        } else {
          String marker = "[" + idx + "]";
          if (!invalid.contains(marker)) {
            invalid.add(marker);
          }
        }
      }
    }
  }

  /** 句内词被上下文覆盖的比例（去重后按词计，重复词不放大权重）。 */
  private static double supportRatio(String sentence, List<RagResult> scope) {
    Set<String> tokens = new LinkedHashSet<>(CjkTokenizer.tokenize(sentence));
    if (tokens.isEmpty()) {
      return 0.0;
    }
    Set<String> contextTokens = new LinkedHashSet<>();
    for (RagResult r : scope) {
      if (r != null && r.text() != null) {
        contextTokens.addAll(CjkTokenizer.tokenize(r.text()));
      }
    }
    int hit = 0;
    for (String t : tokens) {
      if (contextTokens.contains(t)) {
        hit++;
      }
    }
    return (double) hit / tokens.size();
  }

  /** 句中任一数字在所选上下文中找不到 ⇒ 视为编造（金额/比例/期限的典型幻觉形态）。 */
  private static boolean hasUngroundedNumber(String sentence, List<RagResult> scope) {
    StringBuilder corpus = new StringBuilder();
    for (RagResult r : scope) {
      if (r != null && r.text() != null) {
        corpus.append(r.text()).append('\n');
      }
    }
    String haystack = corpus.toString();
    Matcher m = NUMBER.matcher(sentence);
    while (m.find()) {
      if (!haystack.contains(m.group())) {
        return true;
      }
    }
    return false;
  }

  private static List<RagResult> pick(List<RagResult> contexts, Set<Integer> indexes) {
    List<RagResult> out = new ArrayList<>(indexes.size());
    for (int i : indexes) {
      out.add(contexts.get(i - 1));
    }
    return out;
  }

  /** 计有效字符（字母/数字/汉字），用于过滤「好的。」这类无实质内容的碎片。 */
  private static int countMeaningfulChars(String s) {
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean alnum = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
      boolean cjk = c >= 0x4e00 && c <= 0x9fa5;
      if (alnum || cjk) {
        n++;
      }
    }
    return n;
  }

  private static String snippet(String s) {
    return s.length() <= MAX_SNIPPET ? s : s.substring(0, MAX_SNIPPET) + "…";
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static double clamp(double v) {
    if (Double.isNaN(v) || v < 0.0) {
      return 0.0;
    }
    return Math.min(v, 1.0);
  }

  private static GroundingVerdict verdict(
      boolean grounded,
      String reason,
      int sentenceCount,
      int unsupportedCount,
      double meanSupport,
      List<String> invalidCitations,
      List<String> unsupportedSentences) {
    return new GroundingVerdict(
        grounded,
        reason,
        sentenceCount,
        unsupportedCount,
        meanSupport,
        List.copyOf(invalidCitations),
        List.copyOf(unsupportedSentences));
  }
}
