

-- 创建 users 表
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    role_id BIGINT,
    created_at DATETIME,
    created_by BIGINT,
    updated_at DATETIME,
    updated_by BIGINT,
    deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);
ALTER TABLE roles ALTER COLUMN id RESTART WITH 10000;

-- 用户-角色关联表
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
);

-- 系统权限表
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID',
    biz_identity_code VARCHAR(50) NOT NULL COMMENT '业务身份',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    perm_code VARCHAR(100) NOT NULL COMMENT '权限标识',
    perm_type TINYINT NOT NULL COMMENT '权限类型(1:菜单 2:按钮 3:接口)',
    parent_id BIGINT COMMENT '父权限ID',
    path VARCHAR(200) COMMENT '访问路径',
    component VARCHAR(200) COMMENT '前端组件',
    icon VARCHAR(50) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_perm_code UNIQUE (perm_code)
);

-- 角色-权限关联表
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    perm_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, perm_id)
);

-- 销售记录测试表（与 mysql/schema.sql 对齐）
CREATE TABLE sales_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    region VARCHAR(50),
    product_name VARCHAR(100),
    quantity INT,
    is_deleted BOOLEAN DEFAULT FALSE
);