package com.pkh.cloud.auth.application.dto;

/**
 * 登录参数
 */
public class LoginDTO {

    String userName;

    String password;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public LoginDTO() {
    }

    public LoginDTO(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginDTO{" +
                "userName='" + userName + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
