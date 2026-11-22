package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;

/**
 * 受理落库端口：把「报案 + 首案」作为一个原子落库单元写入。
 *
 * <p><b>为什么需要这个端口</b>：报案（FNOL，事故告知）与赔案（赔付处理）是两个聚合根，而「首次提交」必须在同一次事务里 建出两行 ——
 * 只有报案没有赔案是合法业务态（报案未立案），但<b>提交失败留下的残留不是</b>：用户看到「提交失败」， 系统里却多出一条已报案记录，客服与对账两侧都要为这条幽灵数据做额外判断。
 *
 * <p>R9（一事务一聚合）无法自己表达这种「创建型跨聚合原子操作」：它按<b>聚合类型数</b>计数，不区分「同事务创建两个新聚合」
 * 与「同事务修改两个既有聚合」。后者是真违规（两个一致性边界被强行合并，放大锁竞争且把最终一致伪装成强一致）， 前者则是本次业务事实的客观形态。把这件事收成一个端口，有两个效果：
 *
 * <ul>
 *   <li>全模块的跨聚合写点<b>唯一且显式</b>——一眼能数清有几处，而不是散落在各业务方法里；
 *   <li>R9 对其余场景保持严格：任何「更新两个聚合」的写法仍然被门禁拦下。
 * </ul>
 *
 * <p><b>实现约束</b>：实现必须保证两行同事务（后写失败时前写回滚）。并发双写时败者触发 {@code uk_tenant_idem}
 * 冲突，事务整体回滚——不会留下「有报案无赔案」的中间态。
 */
public interface ClaimIntakePort {

  /**
   * 落库一次受理：先写报案（事故上下文 + 锁定的计划版本），再写挂靠它的首案。
   *
   * @param report 已通过领域校验的报案（{@code reportNo} 已由 id 派生）
   * @param firstCase 挂靠该报案的首个赔案（{@code reportId} 已指向 {@code report.getId()}）
   */
  void persistIntake(ClaimReport report, ClaimCase firstCase);
}
