package com.bone.masterdata.application.query.handler;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQry;
import com.bone.core.result.PageResult;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MasterDataEntityPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<MasterDataEntityDTO> handle(MasterDataEntityPageQry qry) {
        var query = QueryBuilder.from(MasterDataEntity.class);
        
        if (qry.getKeyword() != null) {
            query.where(builder -> builder.like("name", qry.getKeyword()).or().like("description", qry.getKeyword()));
        }
        
        if (qry.getCategory() != null) {
            query.where(builder -> builder.eq("category", qry.getCategory()));
        }
        
        if (qry.getStatus() != null) {
            query.where(builder -> builder.eq("status", qry.getStatus()));
        }
        
        return query
                .orderBy("createdAt", "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(MasterDataEntityDTO.class);
    }
}