package com.bone.metadata.auth.domain.service.permission;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bone.metadata.auth.domain.entity.permission.RoleMenuDO;
import com.bone.metadata.auth.domain.mapper.permission.RoleMenuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenuDO> implements RoleMenuService{
}
