package com.bone.lowcode.infra.infrastructure.feign.util;

import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.infrastructure.feign.TpaSaasBusinessFeignClient;
import com.bone.lowcode.infra.infrastructure.feign.bean.EnumEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Slf4j
public class TpaSaasBusinessFeignUtil {

    @Autowired
    private TpaSaasBusinessFeignClient tpaSaasBusinessFeignClient;

    public List<EnumEntry> getEnumList() {
        Result<List<EnumEntry>> result = null;
        try {
            result = tpaSaasBusinessFeignClient.getEnumList();
        } catch (Exception e) {
            log.error("查询业务端枚举列表数据发生异常", e);
            throw new ServiceException(500, "查询业务端枚举列表数据发生异常");
        }

        if (!Result.DEFAULT_SUCCESS_CODE.equals(result.getCode())) {
            log.error("查询业务端枚举列表数据发生异常, 响应:{}", JSON.toJSONString(result));
            throw new ServiceException(500, "查询业务端枚举列表数据发生异常");
        }
        return result.getData();
    }

    public List<EnumEntry> getEnumDetail(String enumCode) {
        Result<List<EnumEntry>> result = null;
        try {
            result = tpaSaasBusinessFeignClient.getEnumDetail(enumCode);
        } catch (Exception e) {
            log.error("查询业务端枚举详情数据发生异常,参数:" + enumCode, e);
            throw new ServiceException(500, "查询业务端枚举详情数据发生异常");
        }

        if (!Result.DEFAULT_SUCCESS_CODE.equals(result.getCode())) {
            log.error("查询业务端枚举详情数据发生异常, 参数:{}, 响应:{}", enumCode, JSON.toJSONString(result));
            throw new ServiceException(500, "查询业务端枚举详情数据发生异常");
        }
        return result.getData();
    }
}
