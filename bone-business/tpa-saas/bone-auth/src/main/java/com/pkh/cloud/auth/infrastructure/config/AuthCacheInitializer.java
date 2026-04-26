package com.pkh.cloud.auth.infrastructure.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.core.result.PageResult;
import com.pkh.cloud.auth.application.enums.CommonStatusEnum;
import com.pkh.cloud.auth.application.vo.user.UserPageReqVO;
import com.pkh.cloud.auth.domain.entity.permission.MenuDO;
import com.pkh.cloud.auth.domain.entity.permission.RoleDO;
import com.pkh.cloud.auth.domain.entity.permission.RoleMenuDO;
import com.pkh.cloud.auth.domain.entity.permission.UserRoleDO;
import com.pkh.cloud.auth.domain.entity.user.AdminUserDO;
import com.pkh.cloud.auth.domain.mapper.permission.RoleMenuMapper;
import com.pkh.cloud.auth.domain.service.permission.MenuService;
import com.pkh.cloud.auth.domain.service.permission.PermissionService;
import com.pkh.cloud.auth.domain.service.permission.RoleService;
import com.pkh.cloud.auth.domain.service.user.AdminUserService;
import com.pkh.cloud.auth.sdk.constant.AuthRedisConstant;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.bone.core.util.CollectionUtils.convertSet;


@Component
@Order(100)
public class AuthCacheInitializer implements ApplicationRunner {
   @Resource
   PermissionService permissionService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initUserToRedis();
    }

    private void initUserToRedis() {
        permissionService.refreshUserToRedis();
    }

}
