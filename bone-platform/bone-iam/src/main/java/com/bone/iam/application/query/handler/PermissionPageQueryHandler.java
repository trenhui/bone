package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.PermissionDTO;
import com.bone.iam.application.query.qry.PermissionPageQry;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PermissionPageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<PermissionDTO> handle(PermissionPageQry qry) {
        QueryBuilder<Permission> queryBuilder = QueryBuilder.from(Permission.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            queryBuilder.where("name").like(qry.getKeyword())
                       .or("code").like(qry.getKeyword())
                       .or("description").like(qry.getKeyword());
        }
        
        if (qry.getType() != null) {
            queryBuilder.where("type").eq(qry.getType());
        }
        
        if (qry.getParentId() != null) {
            queryBuilder.where("parentId").eq(qry.getParentId());
        }
        
        return queryBuilder.orderBy("createTime", "desc")
                          .page(qry.getPageNum(), qry.getPageSize())
                          .mapTo(PermissionDTO.class);
    }
}