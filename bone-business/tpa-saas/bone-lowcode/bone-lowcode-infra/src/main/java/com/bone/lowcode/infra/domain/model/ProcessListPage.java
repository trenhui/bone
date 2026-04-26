package com.bone.lowcode.infra.domain.model;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.ProcessListPageDO;
import lombok.Data;

import java.util.List;

@Data
public class ProcessListPage {

    private ProcessListPageDO listPageDO;

    private List<CfgTableDO> tableDOList;

    private List<CfgModelDO> modelDOList;

    private List<CfgFieldDO> fieldDOList;
}
