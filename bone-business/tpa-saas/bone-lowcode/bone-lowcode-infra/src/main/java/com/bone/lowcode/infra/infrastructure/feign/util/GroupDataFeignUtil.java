package com.bone.lowcode.infra.infrastructure.feign.util;


import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.infrastructure.feign.GroupDataFeignClient;
import com.bone.lowcode.infra.infrastructure.feign.bean.AddressInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.GroupDataParam;
import com.bone.lowcode.infra.infrastructure.feign.bean.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GroupDataFeignUtil {

    @Autowired
    private GroupDataFeignClient groupDataFeignClient;

    public PageResult<AddressInfo> getAddressList(GroupDataParam param) {
        Result<PageResult<AddressInfo>> result = null;
        try {
            result = groupDataFeignClient.getAddressData(param);
        } catch (Exception e) {
            log.error("查询省市区数据发生异常, 参数:" + param, e);
            throw new ServiceException(500, "查询省市区数据发生异常");
        }
        
        if (result.getCode() != 200) {
            log.error("查询省市区数据发生异常, 参数:{}, 响应:{}", param, JSON.toJSONString(result));
            throw new ServiceException(500, "查询省市区数据发生异常");
        }
        return result.getData();
    }
}
