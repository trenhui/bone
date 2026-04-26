package com.bone.lowcode.infra.application.vo.page.pageJson;

import lombok.Data;

import java.util.List;

@Data
public class PageVO {

    private String id;

    private String type;

    private String displayMode;

    private String code;

    private String name;

    private Byte pageType;

    private BizIdentity bizInfo;

    private List<Form> body;
}
