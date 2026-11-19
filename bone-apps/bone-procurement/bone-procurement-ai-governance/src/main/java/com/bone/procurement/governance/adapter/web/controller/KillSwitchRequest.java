package com.bone.procurement.governance.adapter.web.controller;

/** kill switch 操作请求体：操作人不接受自报，由登录态注入。 */
public record KillSwitchRequest(String reason, String drillRef) {}
