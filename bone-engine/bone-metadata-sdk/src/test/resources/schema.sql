-- 创建角色表
CREATE TABLE roles (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    code VARCHAR(50)
);

-- 创建用户表
CREATE TABLE user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100),
    password VARCHAR(100),
    email VARCHAR(255),
    role_id BIGINT,
    age INT,
    status INT,
    FOREIGN KEY (role_id) REFERENCES roles(id)
);