package com.bone.lowcode.infra.application.vo.processPage.newSignPage;

import com.bone.lowcode.infra.application.vo.page.pageJson.BizIdentity;
import lombok.Data;

import java.util.List;

/**
 * 适用于新批次签收页页面
 */
@Data
public class PageNewSign {

    private String id;

    private String type;

    private String displayMode;

    private String code;

    private String name;

    private Byte pageType;

    private BizIdentity bizInfo;

    private List<FormNewSign> body;
}
