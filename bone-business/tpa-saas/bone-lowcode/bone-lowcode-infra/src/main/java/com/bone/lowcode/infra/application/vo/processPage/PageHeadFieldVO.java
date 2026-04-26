package com.bone.lowcode.infra.application.vo.processPage;

import com.bone.lowcode.infra.domain.model.SelectDatasource;
import lombok.Data;

@Data
public class PageHeadFieldVO {

    private String fieldId;

    private String bizCode;

    private String bizName;

    private Integer sequence;

    private String dataBinding;

    private String componentType;

    private SelectDatasource selectDatasource;

    private Byte dateFormatType;

}
