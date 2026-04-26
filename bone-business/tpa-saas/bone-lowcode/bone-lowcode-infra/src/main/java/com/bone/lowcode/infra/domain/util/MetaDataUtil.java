package com.bone.lowcode.infra.domain.util;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.adapter.SecurityService;
import com.bone.lowcode.infra.application.dto.field.CreateExclusiveFieldDTO;
import com.bone.lowcode.infra.domain.valueobject.ComponentTypeEnum;
import com.bone.lowcode.infra.infrastructure.feign.MetaBizIdentityFieldOperateFeignClient;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.metadata.sdk.enums.ExtendFieldModelCode;
import com.bone.metadata.sdk.enums.FieldType;
import com.bone.metadata.sdk.metafeign.request.CreateBizIdentityFieldRequest;
import com.bone.metadata.sdk.metafeign.request.MetaTableFieldQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetaDataUtil {

    @Value("${app.code:tpa}")
    private String appCode;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private MetaBizIdentityFieldOperateFeignClient metaFeignClient;

    public Boolean addBizField(CreateExclusiveFieldDTO dto, String tableName, String modelCode) {
        CreateBizIdentityFieldRequest request = new CreateBizIdentityFieldRequest();
        request.setTableName(tableName);
        request.setBizIdentityCode(dto.getBizIdentityCode());
        request.setFieldName(dto.getBizCode());
        if (ComponentTypeEnum.INPUT_NUM.getType().equals(dto.getComponentType())) {
            request.setFieldType(FieldType.DECIMAL);
        } else {
            request.setFieldType(FieldType.VARCAHR);
        }
        request.setModelCode(ExtendFieldModelCode.getEnumByCode(modelCode));
        request.setFieldRemark(dto.getTitle());

        request.setAppCode(appCode);
        request.setToken(securityService.getToken(appCode));
        Result<MetaFieldDTO> result = metaFeignClient.addBizField(request);
        return result.getSuccess();
    }

    public List<MetaFieldDTO> getFieldMetaByEntityCode(String entityCode) {
        MetaTableFieldQueryRequest request = new MetaTableFieldQueryRequest();
        request.setTableName(entityCode);
        request.setAppCode(appCode);
        request.setToken(securityService.getToken(appCode));

        Result<List<MetaFieldDTO>> result = metaFeignClient.getTableFieldList(request);
        List<MetaFieldDTO> fieldDTOS = result.getData();
        return fieldDTOS;
    }
}
