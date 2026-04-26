package com.bone.lowcode.infra.infrastructure.feign;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.infrastructure.feign.bean.AddressInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.GroupDataParam;
import com.bone.lowcode.infra.infrastructure.feign.bean.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

//  http://localhost:8886   http://47.104.17.249:8886
//@FeignClient(url = "http://47.104.17.249:8886", name = "masterData-server")
@FeignClient("pk-masterdata-server")
public interface GroupDataFeignClient {

    @PostMapping("/dict/enum/page")
    Result<PageResult<AddressInfo>> getAddressData(@RequestBody GroupDataParam param);
}
