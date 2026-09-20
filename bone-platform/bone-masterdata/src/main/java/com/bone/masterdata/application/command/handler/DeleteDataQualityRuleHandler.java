package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.quality.DataQualityRule;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "DeleteDataQualityRule",
    description = "删除数据质量规则",
    inputSchema = "{\"id\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 1,
    retryable = true,
    timeout = 15)
@Component
@RequiredArgsConstructor
public class DeleteDataQualityRuleHandler {
  private final DataQualityRuleRepository dataQualityRuleRepository;

  @Transactional
  public void handle(Long id) {
    DataQualityRule rule = dataQualityRuleRepository.findById(id);
    if (rule == null) {
      throw NotFoundException.of("数据质量规则不存在");
    }
    dataQualityRuleRepository.deleteById(id);
  }
}
