package com.bone.metadata.sdk.test.repository.impl;

import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.dto.UserRoleDTO;
import com.bone.metadata.sdk.test.domain.query.UserPageQuery;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.repository.api.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserRepositoryImpl extends BaseRepository<User, Long> implements UserRepository {
    /**
     * 构造函数，初始化数据库操作相关组件。
     *
     * @param sqlBuilder  SQL 构建引擎，用于生成查询语句
     * @param sqlExecutor SQL 查询执行器，处理命名查询
     */
    @Autowired
    public UserRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, User.class, extensionCoordinator);
    }

    @Override
    public PageResult<UserRoleDTO> queryUerPermPage(UserPageQuery user_page_query) {
        return super.executePagedNamedStatement("user_perm_page", user_page_query);
    }

    @Override
    public PageResult<UserRoleDTO> queryUerPermPageOrderBy(UserPageQuery userQuery) {
        return super.executePagedNamedStatement("user_perm_page_orderby", userQuery);
    }

    @Override
    public List<UserRoleDTO> queryUerPermOrderBy(UserQuery userQuery) {
        return super.executeNamedStatement("user_perm_query_orderby", userQuery);
    }

    @Override
    public PageResult<User> queryUsers(UserQuery query) {
        return null;
    }

    @Override
    public List<User> queryWithFragment(String tableName, Integer status) {
        return null;
    }


}
