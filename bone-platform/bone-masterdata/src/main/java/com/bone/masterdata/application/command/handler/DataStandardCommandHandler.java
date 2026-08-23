package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateDataStandardCommand;
import com.bone.masterdata.application.command.cmd.DeleteDataStandardCommand;
import com.bone.masterdata.application.command.cmd.UpdateDataStandardCommand;
import com.bone.masterdata.domain.repository.DataStandardRepository;
import com.bone.masterdata.domain.standard.DataStandard;
import com.bone.masterdata.domain.standard.vo.StandardFieldCode;
import com.bone.masterdata.domain.standard.vo.StandardRuleType;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "ManageDataStandard",
    description = "数据标准增删改",
    inputSchema =
        "{\"entityCode\": \"string\", \"fieldCode\": \"string\", \"ruleType\": \"int\", \"pattern\": \"string\", \"refCode\": \"string\", \"description\": \"string\"}",
    outputSchema = "{\"standardId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 5)
@Component
@RequiredArgsConstructor
@Transactional
public class DataStandardCommandHandler {
  private final DataStandardRepository dataStandardRepository;

  public Long create(CreateDataStandardCommand cmd) {
    StandardFieldCode fieldCode = StandardFieldCode.of(cmd.getFieldCode());
    long count =
        dataStandardRepository.countByCriteria(
            Criteria.<DataStandard>create()
                .entityClass(DataStandard.class)
                .eq("entityCode", cmd.getEntityCode())
                .eq("fieldCode", fieldCode));
    if (count > 0) {
      throw BizException.of("该字段已存在数据标准: " + cmd.getEntityCode() + "/" + cmd.getFieldCode());
    }
    Long id = DistributedIdGenerator.generateLongId();
    DataStandard standard =
        DataStandard.create(
            id,
            cmd.getEntityCode(),
            fieldCode,
            StandardRuleType.of(cmd.getRuleType() == null ? 1 : cmd.getRuleType()),
            cmd.getPattern(),
            cmd.getRefCode(),
            cmd.getDescription());
    dataStandardRepository.save(standard);
    return standard.getId();
  }

  public void update(UpdateDataStandardCommand cmd) {
    DataStandard standard = dataStandardRepository.findById(cmd.getId());
    if (standard == null) {
      throw BizException.of("数据标准不存在: " + cmd.getId());
    }
    standard.update(
        StandardRuleType.of(cmd.getRuleType() == null ? 1 : cmd.getRuleType()),
        cmd.getPattern(),
        cmd.getRefCode(),
        cmd.getDescription());
    dataStandardRepository.save(standard);
  }

  public void delete(DeleteDataStandardCommand cmd) {
    DataStandard standard = dataStandardRepository.findById(cmd.getId());
    if (standard != null) {
      dataStandardRepository.deleteById(standard.getId());
    }
  }
}
