package com.bone.tpa.claim.infrastructure.external;


import com.bone.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;


//@FeignClient(name = "infraClient", url = "http://192.168.201.56:38081")
@Component
@FeignClient(value = "pageConfig-server",contextId = "getUploadComponent")//@FeignClient(url = "localhost:38081", name = "infraClient")
//@FeignClient(url = "localhost:38081", name = "infraClient")
public interface InfraClient {

    @GetMapping("/cfg/upload/getUploadComponent")
    Result<Map<String, Object>> getUploadComponent(@RequestParam("type") Byte type, @RequestParam("id") Long id);

}
