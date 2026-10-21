package com.bone.metadata.catalog.domain.gateway;

/**
 * 字典绑定渲染指标端口（界面设计器字典打通 v1.1 §8「三个可观测指标」之二：降级率）。
 *
 * <p><b>为什么是端口而不是直接调 Micrometer</b>：应用层不得依赖基础设施（HC-001 分层纪律）， 且指标后端会换（Micrometer / OTLP /
 * 自建），指标语义不该跟着后端走。实现见 {@code infrastructure/observability}。
 *
 * <p><b>口径（必须与文档一致，否则度量变成自说自话）</b>：分母 = <b>服务端发起的真源解析次数</b>（不是前端渲染次数——纯快照渲染服务端不可见，不计入）； 分子 =
 * 其中解析失败、回退到快照的次数。因此「降级率」是「解析链路里有多少次落到兜底」， 而不是「页面上有多少个下拉走了快照」。
 */
public interface DictRenderMetricsPort {

  /**
   * 记录一次真源解析尝试。
   *
   * @param fallbackToSnapshot true = 本次解析失败、回退到快照（降级）；false = 解析成功
   */
  void recordResolve(boolean fallbackToSnapshot);

  /** 累计解析尝试次数（进程内自启动起）。 */
  long totalResolves();

  /** 累计降级次数（进程内自启动起）。 */
  long fallbackResolves();
}
