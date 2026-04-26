package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserCheckVO {

    /**
     * userId : 1
     * userName : 张三
     * groupList : [{"groupId":1,"groupName":"录入组"}]
     */

    private String userId;
    private String userName;
    /**
     * groupId : 1
     * groupName : 录入组
     */

    private List<GroupListBean> groupList;

    @Data
    public static class GroupListBean {
        private Integer groupId;
        private String groupName;
    }
}
