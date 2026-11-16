package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.dispatch.MasterDataRecordDispatchedEvent;
import java.util.List;

/**
 * 消费侧幂等投影端口（Published Language 的消费端契约）。
 *
 * <p>三项职责对应 P0-4 的三条硬约束：{@link #isAuthorized} 守「投递必须校验订阅授权/租户绑定」， {@link #applyIfNew}
 * 守「同一幂等键只投影一次」（N7），{@link #lastVersion} 守版本单调（N8 / 陈旧事件丢弃）。
 * 生产实现须在<b>同一本地事务</b>内写幂等键与投影版本，否则进程崩溃仍会重复投影。
 */
public interface MasterDataProjectionPort {

  boolean isAuthorized(MasterDataRecordDispatchedEvent event, String consumerId);

  /** 返回 true 表示首次落投影；重复键或旧版本返回 false 且不得产生副作用。 */
  boolean applyIfNew(MasterDataRecordDispatchedEvent event);

  long lastVersion(long tenantId, String partyId);

  /** 联调断言用；返回已落投影事件快照。 */
  List<MasterDataRecordDispatchedEvent> projections();
}
