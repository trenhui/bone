package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.model.user.vo.UserId;
import com.bone.metadata.sdk.Repository;

import java.util.Optional;

public interface UserRepository extends Repository<User, UserId> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}