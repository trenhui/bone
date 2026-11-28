package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.evaluation.AiModelCard;
import java.math.BigDecimal;
import java.util.List;

/** MRM 模型卡只读出口：治理服务只认这个端口，不感知配置 Registry 的实现细节。 */
public interface MrmModelCardPort {

  /** 清单装载状态：只有真正登记了卡才是 LOADED。 */
  enum LoadStatus {
    LOADED,
    NO_MODEL_CARDS,
    CONFIG_UNAVAILABLE
  }

  /** PSI 漂移观测：psi 为 null 表示没有观测，不能显示成「在容差内」。 */
  record DriftObservation(BigDecimal psi, BigDecimal watchRatio, BigDecimal threshold) {}

  List<AiModelCard.ModelCard> cards();

  DriftObservation drift();

  LoadStatus status();
}
