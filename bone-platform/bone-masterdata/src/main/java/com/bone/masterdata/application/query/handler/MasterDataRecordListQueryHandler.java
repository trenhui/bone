package com.bone.masterdata.application.query.handler;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.masterdata.application.query.dto.MasterDataRecordDTO;
import com.bone.masterdata.application.query.qry.MasterDataRecordListQry;
import com.bone.core.result.PageResult;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.record.MasterDataRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataRecordListQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<MasterDataRecordDTO> handle(MasterDataRecordListQry qry) {
        var query = QueryBuilder.from(MasterDataRecord.class);
        
        if (qry.getMasterDataEntityId() != null) {
            query.where(MasterDataRecord::getMasterDataEntityId, id -> id.eq(MasterDataEntityId.of(qry.getMasterDataEntityId())));
        }
        
        if (qry.getStatus() != null) {
            query.where(MasterDataRecord::getStatus, status -> status.eq(qry.getStatus()));
        }
        
        if (qry.getKeyword() != null) {
            query.where(MasterDataRecord::getData, data -> data.like(qry.getKeyword()));
        }
        
        return query
                .orderBy(MasterDataRecord::getCreatedAt, "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(MasterDataRecordDTO.class);
    }
}