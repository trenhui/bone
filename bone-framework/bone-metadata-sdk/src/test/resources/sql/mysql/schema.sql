DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS field_permission;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS sales_record;

-- ----------------------------
-- 角色表（权限系统的基础）
-- ----------------------------
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    description VARCHAR(255) COMMENT '角色描述'
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='角色表';
ALTER TABLE roles AUTO_INCREMENT=10000;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
  `role_id` BIGINT DEFAULT NULL COMMENT '角色id',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标识',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 系统权限表
-- ----------------------------
CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    biz_identity_code VARCHAR(50) NOT NULL COMMENT '业务身份',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    perm_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限代码',
    perm_type TINYINT NOT NULL COMMENT '权限类型',
    parent_id BIGINT COMMENT '父权限ID',
    path VARCHAR(200) COMMENT '访问路径',
    component VARCHAR(200) COMMENT '前端组件',
    icon VARCHAR(50) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='系统权限表';


-- ----------------------------
-- 字段权限表
-- ----------------------------
CREATE TABLE field_permission (
    role_code VARCHAR(50) NOT NULL COMMENT '角色代码',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型',
    field_name VARCHAR(255) NOT NULL COMMENT '字段名称',
    can_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '可读权限',
    can_write TINYINT(1) NOT NULL DEFAULT 0 COMMENT '可写权限',
    PRIMARY KEY (role_code, entity_type, field_name),
    INDEX idx_permission_role (role_code)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='字段权限控制表';

-- ----------------------------
-- 用户角色关联表
-- ----------------------------
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='用户角色关联表';

-- ----------------------------
-- 角色权限关联表
-- ----------------------------
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    perm_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, perm_id)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='角色权限关联表';


-- 创建测试表
CREATE TABLE sales_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    region VARCHAR(50),
    product_name VARCHAR(100),
    quantity INT,
    is_deleted TINYINT(1) DEFAULT 0
);

---- 创建索引以提高查询性能
--CREATE INDEX idx_sales_category ON sales_record(category);
--CREATE INDEX idx_sales_status ON sales_record(status);
--CREATE INDEX idx_sales_create_time ON sales_record(create_time);
--CREATE INDEX idx_sales_region ON sales_record(region);