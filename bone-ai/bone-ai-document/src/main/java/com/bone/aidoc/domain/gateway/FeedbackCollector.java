package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.ReviewFeedback;

/**
 * 人工复核反馈采集端口（NIST AI RMF Human-in-the-loop + 标定数据回流）。
 *
 * <p>可信 OCR 的核心闭环：引擎给出决策后，<b>人工最终裁定</b>（确认/纠正）必须被采集， 才能回答「自动放行的字段到底对不对」——否则「可信」永远只是宣称，无法验证。
 * 采集到的反馈既是生产准确率度量，也是下一轮阈值标定的真值来源。
 *
 * <p>骨架只定义契约与 no-op 默认实现；真实实现（写入反馈表 / 消息队列）以同类型 Bean 覆盖即可。 应用层通过 {@code
 * TrustedOcrApplicationService.recordFeedback} 调用本端口。
 */
public interface FeedbackCollector {

  /** 记录一条人工最终裁定（字段级）。 */
  void record(ReviewFeedback feedback);
}
