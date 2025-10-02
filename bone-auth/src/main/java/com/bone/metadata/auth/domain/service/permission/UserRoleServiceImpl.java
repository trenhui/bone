package com.bone.metadata.auth.domain.service.permission;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bone.metadata.auth.domain.entity.permission.UserRoleDO;
import com.bone.metadata.auth.domain.mapper.permission.UserRoleMapper;
import org.springframework.stereotype.Service;

@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRoleDO> implements UserRoleService{

}
