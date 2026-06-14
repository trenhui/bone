package com.bone.masterdata.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQuery;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordStatus;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataRecordListQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<MasterDataRecordDTO> handle(MasterDataRecordListQuery qry) {
    FluentQuery<MasterDataRecord> query = QueryBuilder.from(MasterDataRecord.class);

    if (qry.getMasterDataEntityId() != null) {
      query.where(MasterDataRecord::getMasterDataEntityId).eq(qry.getMasterDataEntityId());
    }

    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      query.where(MasterDataRecord::getStatus).eq(MasterDataRecordStatus.valueOf(qry.getStatus()));
    }

    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(MasterDataRecord::getData).like(qry.getKeyword());
    }

    com.bone.core.model.PageResult<MasterDataRecord> result =
        query.orderByDesc(MasterDataRecord::getCreatedAt).page(qry.getPageNum(), qry.getPageSize());

    List<MasterDataRecordDTO> dtoList =
        result.getRecords().stream().map(this::toDto).collect(Collectors.toList());

    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  private MasterDataRecordDTO toDto(MasterDataRecord record) {
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
