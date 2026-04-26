package com.bone.lowcode.infra.infrastructure.feign;

import com.bone.core.result.Result;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.metadata.sdk.metafeign.request.CreateBizIdentityFieldRequest;
import com.bone.metadata.sdk.metafeign.request.MetaTableFieldQueryRequest;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * @Author fhmdf
 *
 * @create 2024/7/29 15:07
 */
@FeignClient(name = "metadata-server", url = "${metadata.sdk.url}")
public interface MetaBizIdentityFieldOperateFeignClient {

    @PostMapping("/meta/biz/addField")
    Result<MetaFieldDTO> addBizField(@RequestBody CreateBizIdentityFieldRequest createBizIdentityFieldRequest);

    @PostMapping(value = "/meta/query/getTableFieldList")
    Result<List<MetaFieldDTO>> getTableFieldList(@RequestBody MetaTableFieldQueryRequest request);
}
