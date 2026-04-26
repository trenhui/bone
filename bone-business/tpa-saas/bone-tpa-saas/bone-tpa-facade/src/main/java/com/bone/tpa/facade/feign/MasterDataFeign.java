package com.bone.tpa.facade.feign;

import com.bone.core.result.Result;
import com.bone.tpa.facade.FeignTpaConfig;
import com.bone.tpa.facade.request.MasterDataQueryRequest;
import com.bone.tpa.facade.vo.MasterDataData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

//url = "http://api.test0.pukangpay.com.cn/mock/504",
@FeignClient(name = "pk-masterdata-server",configuration = FeignTpaConfig.class)
public interface MasterDataFeign {

    @PostMapping(value = "/dict/enum/list", consumes = "application/json")
    Result<List<MasterDataData>> queryData(@RequestBody MasterDataQueryRequest request);
}
