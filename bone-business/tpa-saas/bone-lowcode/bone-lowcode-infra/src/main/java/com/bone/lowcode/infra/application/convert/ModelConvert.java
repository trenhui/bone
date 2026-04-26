package com.bone.lowcode.infra.application.convert;


import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.dto.table.ModelOfTableChange;
import com.bone.lowcode.infra.application.dto.model.UpdateModelDTO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;

import java.util.ArrayList;
import java.util.List;

public class ModelConvert {

    public static CfgModelDO updateModelDTOToDO(UpdateModelDTO dto) {
        CfgModelDO modelDO = new CfgModelDO();
        modelDO.setId(dto.getModelId());
        modelDO.setStatus(dto.getStatus());
        return modelDO;
    }

    public static List<CfgModelDO> modelChangeListToDOList(List<ModelOfTableChange> modelChangeList) {
        List<CfgModelDO> modelDOList = new ArrayList<>();
        for (ModelOfTableChange change : modelChangeList) {
            if (change.getModelId() == null || change.getUsed() == null)
                throw new ServiceException(500, "待更新的model的id和启用状态不能为空");
            CfgModelDO modelDO = new CfgModelDO();
            modelDO.setId(change.getModelId());
            modelDO.setStatus(change.getUsed());
            modelDOList.add(modelDO);
        }
        return modelDOList;
    }
}
