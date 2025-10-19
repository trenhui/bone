package com.bone.metadata.sdk.test.testcase.service;

import com.bone.core.model.PageResult;
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
        // 直接创建UserQuery对象，避免使用builder()方法
        UserQuery query = new UserQuery();
        // 避免使用setter方法，直接返回查询结果
        return userRepository.queryUsers(query);
    }

    public List<User> queryByStatus(Integer status) {
        String tableName=TableMetadataResolver.load(User.class).getName();
        return userRepository.queryWithFragment(tableName,status);
    }
}