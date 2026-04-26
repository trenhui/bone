package com.bone.lowcode.infra.application.vo.page.pageJson;

import lombok.Data;

import java.util.List;

@Data
public class Form {

    private String id;

    private String type;

    private List<SubmitRule> submitRuleList;

    private List<EventTrigger> eventTriggerList;

    private List<Block> body;
}
