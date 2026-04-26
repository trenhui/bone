package com.bone.lowcode.infra.domain.model;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.ProcessDetailPageDO;
import lombok.Data;

import java.util.List;

@Data
public class ProcessDetailPage {

    private ProcessDetailPageDO detailPageDO;

    private List<CfgModelDO> modelDOList;

    private List<CfgFieldDO> fieldDOList;
}
