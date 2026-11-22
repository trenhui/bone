package com.bone.claim.domain.model.claim.valueobject;

/**
 * 报案状态（只表达「还能否追加赔案」，不镜像赔案进度）。
 *
 * <p><b>为什么只有两值</b>：赔案的处理进度（受理/初审/核赔/结案）属于赔案聚合，报案若也落一份就等于同一事实两处真源 ——
 * 一个报案下所有赔案结案时「报案状态该不该变」会变成永久的对账负担。故「是否全部结案」一律由名下赔案派生， 本枚举只回答「这个报案还能不能再立新案」。
 *
 * <p><b>DB 存 name</b>（{@code VARCHAR}），与 {@code t_claim_case.status} 同口径；不落数字码，避免「枚举带 code 落
 * varchar 列 ⇒ 建得进读不出」那类历史坑。
 */
public enum ReportStatus {
  /** 已报案：可以追加赔案。 */
  REPORTED("已报案"),
  /** 已关闭：不再受理新赔案（已立案的赔案不受影响）。 */
  CLOSED("已关闭");

  private final String displayName;

  ReportStatus(String displayName) {
    this.displayName = displayName;
  }

  /** 中文名（日志与导出用；对外接口仍用 name）。 */
  public String displayName() {
    return displayName;
  }

  /** DB name → 枚举（SDK String 持久化口径）。 */
  public static ReportStatus ofName(String name) {
    for (ReportStatus v : values()) {
      if (v.name().equals(name)) {
        return v;
      }
    }
    throw new IllegalArgumentException("未知 ReportStatus: " + name);
  }
}
