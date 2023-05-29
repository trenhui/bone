package com.bone.module.bpm.framework.rpc.config;

import com.bone.module.system.api.dept.DeptApi;
import com.bone.module.system.api.dept.PostApi;
import com.bone.module.system.api.dict.DictDataApi;
import com.bone.module.system.api.permission.RoleApi;
import com.bone.module.system.api.sms.SmsSendApi;
import com.bone.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {RoleApi.class, DeptApi.class, PostApi.class, AdminUserApi.class, SmsSendApi.class, DictDataApi.class})
public class RpcConfiguration {
}
