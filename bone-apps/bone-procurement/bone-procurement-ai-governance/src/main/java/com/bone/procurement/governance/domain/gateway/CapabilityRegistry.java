package com.bone.procurement.governance.domain.gateway;

import com.bone.procurement.governance.domain.model.AiCapability;
import com.bone.procurement.governance.domain.model.CapabilityCode;
import java.util.List;
import java.util.Optional;

/** 能力登记仓储端口（walking skeleton 用受控内存实现，生产持久化端口就绪前不注册生产 bean）。 */
public interface CapabilityRegistry {

  void save(AiCapability capability);

  Optional<AiCapability> find(CapabilityCode code);

  /** 是否存在（用于区分「未登记」与「已登记但不放行」）。 */
  boolean exists(CapabilityCode code);

  /** 清单查询：供能力账本与披露页展示。 */
  List<AiCapability> listAll();
}
