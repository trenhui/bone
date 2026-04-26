package com.pkh.cloud.auth.domain.service.permission;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pkh.cloud.auth.domain.entity.permission.RoleMenuDO;
import com.pkh.cloud.auth.domain.mapper.permission.RoleMenuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenuDO> implements RoleMenuService{
}
