package com.bone.integration.application.dto;

import java.util.List;

public class MockDTO {
    private Long id;
    private String name;
    private String appCode;
    private String remark;
    private String url;
    private List<MockCondition> conditions;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<MockCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<MockCondition> conditions) {
        this.conditions = conditions;
    }

    public static class MockCondition {
        private String conditionName;
        private String expression;
        private String conditionType;
        private MockResponse response;

        public String getConditionName() {
            return conditionName;
        }

        public void setConditionName(String conditionName) {
            this.conditionName = conditionName;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public String getConditionType() {
            return conditionType;
        }

        public void setConditionType(String conditionType) {
            this.conditionType = conditionType;
        }

        public MockResponse getResponse() {
            return response;
        }

        public void setResponse(MockResponse response) {
            this.response = response;
        }
    }

    public static class MockResponse {
        private String body;
        private String responseType;
        private List<ResponseHeader> responseHeader;

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getResponseType() {
            return responseType;
        }

        public void setResponseType(String responseType) {
            this.responseType = responseType;
        }

        public List<ResponseHeader> getResponseHeader() {
            return responseHeader;
        }

        public void setResponseHeader(List<ResponseHeader> responseHeader) {
            this.responseHeader = responseHeader;
        }
    }

    public static class ResponseHeader {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}