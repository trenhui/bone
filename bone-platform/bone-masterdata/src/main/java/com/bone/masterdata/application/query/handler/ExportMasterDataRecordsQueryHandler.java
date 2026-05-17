package com.bone.masterdata.application.query.handler;

import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExportMasterDataRecordsQueryHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MasterDataEntityRepository entityRepository;
    private final MasterDataRecordRepository recordRepository;

    @Transactional(readOnly = true)
    public String handle(Long masterDataEntityId) {
        if (entityRepository.findById(masterDataEntityId) == null) {
            throw NotFoundException.of("主数据实体不存在: " + masterDataEntityId);
        }
        List<MasterDataRecord> records =
                recordRepository.findByCriteria(
                        Criteria.<MasterDataRecord>create().eq("masterDataEntityId", masterDataEntityId));
        try {
            return MAPPER.writeValueAsString(records);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导出序列化失败", e);
        }
    }
}
