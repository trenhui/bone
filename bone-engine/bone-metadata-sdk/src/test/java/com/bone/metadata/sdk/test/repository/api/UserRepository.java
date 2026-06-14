package com.bone.metadata.sdk.test.repository.api;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.test.domain.User;
import com.bone.metadata.sdk.test.domain.query.UserQuery;
import java.util.List;

public interface UserRepository extends Repository<User, Long> {

  //    PageResult<UserRoleDTO> queryUerPermPage(UserPageQuery userPageQuery);
  //
  //    PageResult<UserRoleDTO> queryUerPermPageOrderBy(UserPageQuery userQuery);
  //
  //    List<UserRoleDTO> queryUerPermOrderBy(UserQuery userQuery);

  PageResult<User> queryUsers(UserQuery query);

  List<User> queryWithFragment(
      @Param("tableName") String tableName, @Param("status") Integer status);
}
