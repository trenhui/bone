package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.UserDTO;
import com.bone.iam.application.query.qry.UserPageQry;
import com.bone.iam.domain.model.user.User;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserPageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<UserDTO> handle(UserPageQry qry) {
        QueryBuilder<User> queryBuilder = QueryBuilder.from(User.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            queryBuilder.where("username").like(qry.getKeyword())
                       .or("email").like(qry.getKeyword());
        }
        
        if (qry.getStatus() != null && !qry.getStatus().isEmpty()) {
            queryBuilder.where("status").eq(qry.getStatus());
        }
        
        if (qry.getTenantId() != null) {
            queryBuilder.where("tenantId").eq(qry.getTenantId());
        }
        
        return queryBuilder.orderBy("createTime", "desc")
                          .page(qry.getPageNum(), qry.getPageSize())
                          .mapTo(UserDTO.class);
    }
}