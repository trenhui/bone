package com.bone.masterdata.domain.service.record;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.record.MasterDataRecord;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 主数据记录领域服务
 */
@RequiredArgsConstructor
public class MasterDataRecordService {
    private final MasterDataRecordRepository recordRepository;

    public MasterDataRecord createRecord(Long id, Long masterDataEntityId, String data) {
        return MasterDataRecord.create(id, masterDataEntityId, data);
    }

    public void publishRecord(Long recordId) {
        MasterDataRecord record = recordRepository.findById(recordId);
        if (record == null) {
            throw new DomainException("主数据记录不存在");
        }
        record.publish();
        recordRepository.save(record);
    }

    public void updateRecord(Long recordId, String data) {
        MasterDataRecord record = recordRepository.findById(recordId);
        if (record == null) {
            throw new DomainException("主数据记录不存在");
        }
        record.update(data);
        recordRepository.save(record);
    }

    public List<MasterDataRecord> createRecords(Long masterDataEntityId, List<Long> ids, List<String> dataList) {
        if (ids.size() != dataList.size()) {
            throw new DomainException("记录 ID 与数据条数不一致");
        }
        return java.util.stream.IntStream.range(0, dataList.size())
                .mapToObj(i -> MasterDataRecord.create(ids.get(i), masterDataEntityId, dataList.get(i)))
                .toList();
    }
}
