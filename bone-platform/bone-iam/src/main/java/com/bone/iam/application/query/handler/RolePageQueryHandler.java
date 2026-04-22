package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.RoleDTO;
import com.bone.iam.application.query.qry.RolePageQry;
import com.bone.iam.domain.model.role.Role;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RolePageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<RoleDTO> handle(RolePageQry qry) {
        QueryBuilder<Role> queryBuilder = QueryBuilder.from(Role.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            queryBuilder.where("name").like(qry.getKeyword())
                       .or("description").like(qry.getKeyword());
        }
        
        if (qry.getTenantId() != null) {
            queryBuilder.where("tenantId").eq(qry.getTenantId());
        }
        
        return queryBuilder.orderBy("createTime", "desc")
                          .page(qry.getPageNum(), qry.getPageSize())
                          .mapTo(RoleDTO.class);
    }
}