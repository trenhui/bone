package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.UserDTO;
import com.bone.iam.application.query.qry.UserPageQry;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.model.user.vo.UserStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserPageQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<UserDTO> handle(UserPageQry qry) {
        FluentQuery<User> query = QueryBuilder.from(User.class);

        if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
            query.where(User::getUsername).like(qry.getKeyword())
                       .or(User::getEmail).like(qry.getKeyword());
        }

        if (qry.getStatus() != null && !qry.getStatus().isEmpty()) {
            query.where(User::getStatus).eq(UserStatus.valueOf(qry.getStatus()));
        }

        if (qry.getTenantId() != null) {
            query.where(User::getTenantId).eq(qry.getTenantId());
        }

        PageResult<User> result = query.orderByDesc(User::getCreateTime)
                          .page(qry.getPage(), qry.getSize());

        List<UserDTO> dtoList = result.getRecords().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private UserDTO convertToDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId().value());
        dto.setUsername(user.getUsername().value());
        dto.setEmail(user.getEmail().value());
        dto.setStatus(user.getStatus());
        dto.setTenantId(user.getTenantId());
        dto.setCreateTime(user.getCreateTime());
        dto.setUpdateTime(user.getUpdateTime());
        return dto;
    }
}