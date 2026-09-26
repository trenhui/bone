package com.bone.masterdata.application;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.RequestSubscriptionCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.model.subscription.EntitySubscription;
import com.bone.masterdata.domain.repository.EntitySubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 消费订阅应用服务（G10，UC-C1/C3）：申请 → Owner 批准 → 生效 / 撤销。 */
@Service
@RequiredArgsConstructor
public class SubscriptionApplicationService {

  private final EntitySubscriptionRepository subscriptionRepository;
  private final CurrentUserPort currentUserPort;

  @Transactional
  public Long request(RequestSubscriptionCommand cmd) {
    if (subscriptionRepository.countByEntityAppAndMode(
            cmd.getMasterDataEntityId(), cmd.getAppId(), cmd.getSubscribeMode())
        > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.SUBSCRIPTION_DUPLICATE, "该订阅关系已存在");
    }
    EntitySubscription subscription =
        EntitySubscription.request(
            DistributedIdGenerator.generateLongId(),
            cmd.getMasterDataEntityId(),
            cmd.getAppId(),
            cmd.getSubscribeMode(),
            currentUserPort.requireUserId());
    return subscriptionRepository.insert(subscription);
  }

  @Transactional
  public void approve(Long id) {
    EntitySubscription subscription = requireSubscription(id);
    subscription.approve(currentUserPort.requireUserId());
    subscriptionRepository.update(subscription);
  }

  @Transactional
  public void revoke(Long id) {
    EntitySubscription subscription = requireSubscription(id);
    subscription.revoke();
    subscriptionRepository.update(subscription);
  }

  @Transactional(readOnly = true)
  public List<EntitySubscription> byEntity(Long masterDataEntityId) {
    return subscriptionRepository.findByEntityId(masterDataEntityId);
  }

  private EntitySubscription requireSubscription(Long id) {
    EntitySubscription subscription = subscriptionRepository.findById(id);
    if (subscription == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.SUBSCRIPTION_STATE_INVALID, "订阅不存在: " + id);
    }
    return subscription;
  }
}
