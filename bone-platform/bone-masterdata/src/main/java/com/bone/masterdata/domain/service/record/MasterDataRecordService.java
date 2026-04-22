package com.bone.masterdata.domain.service.record;

import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 主数据记录领域服务
 * 处理主数据记录相关的业务逻辑
 */
@RequiredArgsConstructor
public class MasterDataRecordService {
    private final MasterDataRecordRepository recordRepository;

    public MasterDataRecord createRecord(MasterDataEntityId masterDataEntityId, String data) {
        return MasterDataRecord.create(masterDataEntityId, data);
    }

    public void publishRecord(MasterDataRecordId recordId) {
        MasterDataRecord record = recordRepository.findById(recordId);
        if (record == null) {
            throw new DomainException("主数据记录不存在");
        }
        record.publish();
        recordRepository.save(record);
    }

    public void updateRecord(MasterDataRecordId recordId, String data) {
        MasterDataRecord record = recordRepository.findById(recordId);
        if (record == null) {
            throw new DomainException("主数据记录不存在");
        }
        record.update(data);
        recordRepository.save(record);
    }

    public List<MasterDataRecord> createRecords(MasterDataEntityId masterDataEntityId, List<String> dataList) {
        return dataList.stream()
                .map(data -> MasterDataRecord.create(masterDataEntityId, data))
                .toList();
    }
}