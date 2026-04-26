package com.bone.lowcode.infra.application.vo.page.pageJson;

import lombok.Data;

import java.util.List;

@Data
public class Block {

    private String id;

    private String type;

    private String name;

    private List body;

    private Byte sequenceNumber;

    private List<EventTrigger> eventTriggerList;
}
