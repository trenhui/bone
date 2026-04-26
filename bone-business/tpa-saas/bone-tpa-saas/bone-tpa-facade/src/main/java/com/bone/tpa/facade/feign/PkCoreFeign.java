package com.bone.tpa.facade.feign;

import com.bone.tpa.facade.FeignTpaConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "pkcore", url = "${pkcore.url}", configuration = FeignTpaConfig.class)
public interface PkCoreFeign {
    /**
     * 获取oss权限
     *
     *@param
     *@return
     */
    @PostMapping("oss/getToken")
    Map<String, Object> getToken(@RequestParam Map map);

}
