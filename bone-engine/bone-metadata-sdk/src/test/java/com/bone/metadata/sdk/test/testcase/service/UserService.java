package com.bone.metadata.sdk.test.testcase.service;

import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import com.bone.metadata.sdk.test.repository.impl.UserRepositoryImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepositoryImpl userRepository;

    public UserService(UserRepositoryImpl userRepository) {
        this.userRepository = userRepository;
    }

    public PageResult<User> queryUsers(String name, Integer status, List<String> roleNames) {
        UserQuery query = UserQuery.builder()
                .userName(name)
                .sortOrder(status)
                .build();
        // query.getParams().put("tableName", tableMetadataResolver.resolve(User.class).tableName());
        return userRepository.queryUsers(query);
    }

    public List<User> queryByStatus(Integer status) {
        String tableName=TableMetadataResolver.load(User.class).getName();
        return userRepository.queryWithFragment(tableName,status);
    }
}