package com.bone.lowcode.infra.infrastructure.persistence.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OptionSet;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OptionSetMapper extends BaseMapper<OptionSet> {

    Integer deleteByParentIdAndCode(@Param("parentId") Long parentId, @Param("codeList") List<String> codeList);

    Integer batchSaveWithId(@Param("optionSetList") List<OptionSet> optionSetList);
}
