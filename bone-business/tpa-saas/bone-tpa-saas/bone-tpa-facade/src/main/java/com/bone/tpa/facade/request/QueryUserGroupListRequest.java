package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class QueryUserGroupListRequest {

    private String stage ;

    private Long claimNumber;

    private String userName;
}
