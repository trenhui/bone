package com.bone.lowcode.infra.infrastructure.feign;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.infrastructure.feign.bean.EnumEntry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@FeignClient(url = "tpasaas.dev6.pukangbao.com", name = "bone-tpa-server")
//@FeignClient("bone-tpa-server")
public interface TpaSaasBusinessFeignClient {

    /**
     * 查询业务端定义的枚举类型
     */
    @GetMapping("/tpa/enums/list")
    Result<List<EnumEntry>> getEnumList();

    /**
     * 查询业务端定义的枚举内容
     */
    @GetMapping("/tpa/enums/optionSet")
    Result<List<EnumEntry>> getEnumDetail(@RequestParam("enumCode") String enumCode);
}
