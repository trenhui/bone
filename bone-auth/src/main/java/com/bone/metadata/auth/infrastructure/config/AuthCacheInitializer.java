package com.bone.metadata.auth.infrastructure.config;

import com.bone.metadata.auth.domain.service.permission.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
