package com.bone.lowcode.infra.application.vo.processPage;

import lombok.Data;

import java.util.List;

@Data
public class ProcessHeadPageVO {

    private String pageId;

    private String modelName;

    private String param;

    private List<OptionSetInfo> optionSetList;

}
