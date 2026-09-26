package com.bone.masterdata.application;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.SubmitFeedbackCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.model.feedback.DataFeedback;
import com.bone.masterdata.domain.repository.DataFeedbackRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 下游反馈应用服务（G17，UC-C4）：订阅应用反向纠错，Steward 受理。 */
@Service
@RequiredArgsConstructor
public class FeedbackApplicationService {

  private final DataFeedbackRepository feedbackRepository;
  private final CurrentUserPort currentUserPort;

  @Transactional
  public Long submit(SubmitFeedbackCommand cmd) {
    return feedbackRepository.insert(
        DataFeedback.submit(
            DistributedIdGenerator.generateLongId(),
            cmd.getMasterDataEntityId(),
            cmd.getRecordId(),
            cmd.getAppId(),
            cmd.getFeedbackType(),
            cmd.getContent(),
            cmd.getSuggestedData(),
            currentUserPort.requireUserId()));
  }

  @Transactional
  public void accept(Long id) {
    DataFeedback feedback = requireFeedback(id);
    feedback.accept(currentUserPort.requireUserId());
    feedbackRepository.update(feedback);
  }

  @Transactional
  public void reject(Long id, String result) {
    DataFeedback feedback = requireFeedback(id);
    feedback.reject(currentUserPort.requireUserId(), result);
    feedbackRepository.update(feedback);
  }

  @Transactional
  public void complete(Long id, String result) {
    DataFeedback feedback = requireFeedback(id);
    feedback.complete(currentUserPort.requireUserId(), result);
    feedbackRepository.update(feedback);
  }

  @Transactional(readOnly = true)
  public List<DataFeedback> byEntity(Long masterDataEntityId, String status) {
    return feedbackRepository.findByEntityIdAndStatus(masterDataEntityId, status);
  }

  private DataFeedback requireFeedback(Long id) {
    DataFeedback feedback = feedbackRepository.findById(id);
    if (feedback == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.FEEDBACK_NOT_FOUND, "反馈不存在: " + id);
    }
    return feedback;
  }
}
