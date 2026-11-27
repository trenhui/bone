package com.bone.claim.domain.model.evidence.valueobject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 影像材料自动分类（分层证据融合，业界常见的 classifier ensemble 简化版）。
 *
 * <p><b>为什么不再只靠文件名</b>：TPA 签名影像只有 hash 文件名（如 {@code 1CA9AC...jpg}），文件名零语义；
 * 而「汕头口腔医疗中心-收费单」被旧规则按"收费单"命中发票——实际是费用清单。单源 first-match 会稳定误判， 因此升级为多源加权投票：
 *
 * <ol>
 *   <li><b>内容版式信号</b>（前端 canvas 提取 inkRatio/集中度/表格线）：签名=低墨迹+高集中+无表格，权重最高；
 *   <li><b>文件名语义</b>：专名（身份证/结算单/票据）高权重，宽泛词（单/明细）低权重；
 *   <li><b>业务元数据</b>：TPA imageType 作为弱证据，且仅在没有更强证据时采信——实测 019001 的 imageType
 *       标成发票但内容是签名，故其权重必须低于内容信号；
 *   <li><b>OCR 文本</b>：可选，命中单证标题关键词加权。
 * </ol>
 *
 * <p>得分低于阈值或头名并列时落 {@link DocType#OTHER}（fail-closed，不冒充发票）。 全程确定性、可解释：{@link
 * #explain(ClassificationInput)} 输出各证据得分供界面展示"为什么这样归类"。 绝不臆造单证字段值——分类只决定材料类型，字段抽取仍走 {@code
 * DocumentParsePort}。
 */
public final class DocTypeClassifier {

  /** 决策阈值：最高得分需 ≥ 该值，且领先第二名至少 1 分，才采纳；否则落 OTHER。 */
  private static final double MIN_SCORE = 2.0;

  private static final double MIN_MARGIN = 1.0;

  /** 影像版式信号（前端 canvas 在 160×120 灰度缩略图上提取，见 docTypeSignals.ts）。 */
  public record ImageSignals(
      Double inkRatio, Double inkConcentration, Double tableLineRatio, Integer aspectPercent) {
    public static final ImageSignals EMPTY = new ImageSignals(null, null, null, null);

    public boolean available() {
      return inkRatio != null && inkConcentration != null;
    }
  }

  /** 分类输入：文件名 + 内容信号 + 可选元数据/OCR 文本。 */
  public record ClassificationInput(
      String originalName, String imageType, ImageSignals signals, String ocrText) {
    public static ClassificationInput ofName(String originalName) {
      return new ClassificationInput(originalName, null, ImageSignals.EMPTY, null);
    }

    public static ClassificationInput of(String originalName, ImageSignals signals) {
      return new ClassificationInput(originalName, null, signals, null);
    }

    public ImageSignals signals() {
      return signals == null ? ImageSignals.EMPTY : signals;
    }
  }

  /** 分类结果：类型 + 置信分 + 可解释证据。 */
  public record ClassificationResult(
      DocType docType, double score, double margin, String explain) {}

  private record KeywordRule(DocType docType, Pattern pattern, double weight, String reason) {}

  /**
   * 文件名关键词，按权重而非顺序累加。专名（结算单/病历/票据）高于宽泛词（费用/明细/单）。
   *
   * <p>注意「收费单」刻意不给 INVOICE 权重：它是费用明细单，不是发票；真正发票用「票据/发票/invoice/receipt」。
   */
  private static final List<KeywordRule> NAME_RULES =
      List.of(
          new KeywordRule(
              DocType.ID_CARD, Pattern.compile("身份证|身份|idcard|shenfenzheng"), 3.0, "身份证/身份凭证"),
          new KeywordRule(DocType.BANK_CARD, Pattern.compile("银行卡|bankcard"), 3.0, "银行卡/账户凭证"),
          new KeywordRule(
              DocType.DISCHARGE_SUMMARY, Pattern.compile("出院小结|出院记录|出院证明|discharge"), 3.0, "出院小结"),
          new KeywordRule(
              DocType.MEDICAL_SETTLEMENT,
              Pattern.compile("结算单|医保|住院费用|settlement"),
              3.0,
              "医保/住院结算单"),
          new KeywordRule(DocType.PRESCRIPTION, Pattern.compile("处方|prescription"), 3.0, "处方"),
          new KeywordRule(
              DocType.MEDICAL_RECORD, Pattern.compile("病历|诊疗|就诊记录|medicalrecord"), 3.0, "病历/诊疗记录"),
          new KeywordRule(DocType.POLICY, Pattern.compile("保单|保险单|policy"), 3.0, "保单/保险单"),
          new KeywordRule(
              DocType.SIGNATURE, Pattern.compile("签名|签字|签章|手写|signature|sign"), 3.0, "签名/签字"),
          // 票据/发票才是 INVOICE；「收费单」归 LIST（费用清单），避免稳定误判
          new KeywordRule(
              DocType.INVOICE, Pattern.compile("发票|票据|invoice|receipt|fapiao"), 2.6, "发票/收费票据"),
          new KeywordRule(
              DocType.LIST, Pattern.compile("清单|费用明细|收费单|收费明细|felist|detail"), 2.4, "费用清单/收费单"));

  /** OCR 文本关键词（可选证据，权重低于内容版式与文件名专名）。 */
  private static final List<KeywordRule> TEXT_RULES =
      List.of(
          new KeywordRule(DocType.SIGNATURE, Pattern.compile("签名|签字|本人确认|投保人签名"), 1.5, "文本含签名字样"),
          new KeywordRule(
              DocType.MEDICAL_SETTLEMENT, Pattern.compile("医保结算|统筹支付|个人自付"), 1.5, "文本含结算要素"),
          new KeywordRule(DocType.INVOICE, Pattern.compile("发票代码|发票号码|价税合计|开票日期"), 1.5, "文本含发票要素"),
          new KeywordRule(DocType.LIST, Pattern.compile("费用明细|项目名称|数量|单价|金额合计"), 1.0, "文本含明细要素"));

  private DocTypeClassifier() {}

  /** 仅按文件名分类（兼容旧调用与未带内容信号的客户端）。 */
  public static DocType classify(String originalName) {
    return classifyDetailed(ClassificationInput.ofName(originalName)).docType();
  }

  /** 带内容信号的完整分类，返回可解释结果。 */
  public static ClassificationResult classifyDetailed(ClassificationInput input) {
    java.util.EnumMap<DocType, Double> scores = new java.util.EnumMap<>(DocType.class);
    List<String> why = new ArrayList<>();
    String name = normalize(input.originalName());
    String text = normalize(input.ocrText());

    // 1) 文件名语义（多命中累加，专名自然压过宽泛词）
    addNameEvidence(name, scores, why);
    // 2) OCR 文本（若有）
    addTextEvidence(text, scores, why);
    // 3) 内容版式信号（最关键：能识别 hash 文件名的签名图）
    addSignalEvidence(input.signals(), scores, why);
    // 4) TPA 元数据（弱证据，仅无强证据时补位）
    addMetaEvidence(input.imageType(), scores, why);

    DocType best = DocType.OTHER;
    double top = 0;
    double second = 0;
    for (var entry : scores.entrySet()) {
      if (entry.getValue() > top) {
        second = top;
        top = entry.getValue();
        best = entry.getKey();
      } else if (entry.getValue() > second) {
        second = entry.getValue();
      }
    }
    double margin = top - second;
    boolean accepted = top >= MIN_SCORE && margin >= MIN_MARGIN;
    DocType result = accepted ? best : DocType.OTHER;
    String explain =
        why.isEmpty() ? "无有效证据，归为其他" : String.join("；", why) + (accepted ? "" : "（证据不足/并列，归为其他）");
    return new ClassificationResult(result, top, margin, explain);
  }

  /** 返回命中原因（兼容旧签名：仅文件名）。 */
  public static String explain(String originalName) {
    return classifyDetailed(ClassificationInput.ofName(originalName)).explain();
  }

  /** 返回完整解释（带内容信号）。 */
  public static String explain(ClassificationInput input) {
    return classifyDetailed(input).explain();
  }

  private static void addNameEvidence(
      String name, java.util.EnumMap<DocType, Double> scores, List<String> why) {
    if (name.isEmpty()) {
      return;
    }
    for (KeywordRule rule : NAME_RULES) {
      if (rule.pattern().matcher(name).find()) {
        scores.merge(rule.docType(), rule.weight(), Double::sum);
        why.add("文件名命中" + rule.reason());
      }
    }
  }

  private static void addTextEvidence(
      String text, java.util.EnumMap<DocType, Double> scores, List<String> why) {
    if (text.isEmpty()) {
      return;
    }
    for (KeywordRule rule : TEXT_RULES) {
      if (rule.pattern().matcher(text).find()) {
        scores.merge(rule.docType(), rule.weight(), Double::sum);
        why.add("内容命中" + rule.reason());
      }
    }
  }

  private static void addSignalEvidence(
      ImageSignals s, java.util.EnumMap<DocType, Double> scores, List<String> why) {
    if (!s.available()) {
      return;
    }
    double ink = s.inkRatio();
    double conc = s.inkConcentration();
    double table = s.tableLineRatio() == null ? 0 : s.tableLineRatio();
    // 签名版式：低墨迹(<0.16) + 高集中(>=0.5) + 无表格(<0.04)。实测签名 fg=0.097/conc=0.60/table=0。
    if (ink < 0.16 && conc >= 0.50 && table < 0.04) {
      scores.merge(DocType.SIGNATURE, 4.0, Double::sum);
      why.add("版式=低墨迹高集中无表格(签名)");
      return;
    }
    // 票据/清单版式：表格线明显 + 墨迹分散 → 给票据/清单各加弱证据（具体由文件名/OCR 拉开差距）
    if (table >= 0.05 && conc < 0.50) {
      scores.merge(DocType.INVOICE, 1.2, Double::sum);
      scores.merge(DocType.LIST, 1.2, Double::sum);
      why.add("版式=含表格线且墨迹分散(票据/清单)");
    }
  }

  private static void addMetaEvidence(
      String imageType, java.util.EnumMap<DocType, Double> scores, List<String> why) {
    if (imageType == null || imageType.isBlank()) {
      return;
    }
    // TPA imageType=5 是通用影像、3017 是发票，但实测存在标错（签名图被标发票），只给 1 分弱证据。
    if ("3017".equals(imageType.trim())) {
      scores.merge(DocType.INVOICE, 1.0, Double::sum);
      why.add("TPA imageType=3017(发票)弱证据");
    }
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-—.]+", "");
  }
}
