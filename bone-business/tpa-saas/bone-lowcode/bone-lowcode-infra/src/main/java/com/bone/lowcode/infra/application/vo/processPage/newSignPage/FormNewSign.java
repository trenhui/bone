package com.bone.lowcode.infra.application.vo.processPage.newSignPage;

import com.bone.lowcode.infra.application.vo.page.pageJson.EventTrigger;
import com.bone.lowcode.infra.application.vo.page.pageJson.SubmitRule;
import lombok.Data;

import java.util.List;

/**
 * 适用于新批次签收页页面，form下层是单block
 */
@Data
public class FormNewSign {

    private String id;

    private String type;

    private List<SubmitRule> submitRuleList;

    private List<EventTrigger> eventTriggerList;

    private List<Object> body;
}
