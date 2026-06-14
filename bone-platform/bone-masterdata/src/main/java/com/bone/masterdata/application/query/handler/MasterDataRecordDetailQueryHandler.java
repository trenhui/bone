package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordByIdQuery;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataRecordDetailQueryHandler {
  private final MasterDataRecordRepository masterDataRecordRepository;

  @Transactional(readOnly = true)
  public MasterDataRecordDTO handle(MasterDataRecordByIdQuery qry) {
    MasterDataRecord record = masterDataRecordRepository.findById(qry.getId());
    if (record == null) {
      throw new NotFoundException("主数据记录不存在: " + qry.getId());
    }

    return MasterDataRecordDTO.builder()
        .id(record.getId())
        .masterDataEntityId(record.getMasterDataEntityId())
        .data(record.getData())
        .status(record.getStatus().name())
        .createdAt(record.getCreatedAt())
        .updatedAt(record.getUpdatedAt())
        .publishTime(record.getPublishTime())
        .build();
  }
}
