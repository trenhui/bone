package com.bone.module.system.api.dict;

import com.bone.base.core.pojo.CommonResult;
import com.bone.module.system.api.dict.dto.DictDataRespDTO;
import com.bone.module.system.convert.dict.DictDataConvert;
import com.bone.module.system.dal.dataobject.dict.DictDataDO;
import com.bone.module.system.service.dict.DictDataService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;

import static com.bone.base.core.pojo.CommonResult.success;
import static com.bone.module.system.enums.ApiConstants.VERSION;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@DubboService(version = VERSION) // 提供 Dubbo RPC 接口，给 Dubbo Consumer 调用
@Validated
public class DictDataApiImpl implements DictDataApi {

    @Resource
    private DictDataService dictDataService;

    @Override
    public CommonResult<Boolean> validateDictDatas(String dictType, Collection<String> values) {
        dictDataService.validateDictDataList(dictType, values);
        return success(true);
    }

    @Override
    public CommonResult<DictDataRespDTO> getDictData(String dictType, String value) {
        DictDataDO dictData = dictDataService.getDictData(dictType, value);
        return success(DictDataConvert.INSTANCE.convert02(dictData));
    }

    @Override
    public CommonResult<DictDataRespDTO> parseDictData(String dictType, String label) {
        DictDataDO dictData = dictDataService.parseDictData(dictType, label);
        return success(DictDataConvert.INSTANCE.convert02(dictData));
    }

}
