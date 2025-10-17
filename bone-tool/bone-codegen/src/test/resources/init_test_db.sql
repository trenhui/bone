-- 创建数据源表
CREATE TABLE IF NOT EXISTS `codegen_datasource` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `url` VARCHAR(500) NOT NULL COMMENT '数据库连接URL',
  `username` VARCHAR(100) NOT NULL COMMENT '数据库用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '数据库密码',
  `driver_class_name` VARCHAR(200) NOT NULL COMMENT '数据库驱动类名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置表';

-- 创建代码生成表配置表
CREATE TABLE IF NOT EXISTS `codegen_table` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `datasource_id` BIGINT(20) NOT NULL COMMENT '数据源ID',
  `scene` INT(11) DEFAULT NULL COMMENT '生成场景',
  `table_name` VARCHAR(100) NOT NULL COMMENT '表名',
  `table_comment` VARCHAR(500) DEFAULT NULL COMMENT '表描述',
  `module_name` VARCHAR(100) DEFAULT NULL COMMENT '模块名',
  `package_name` VARCHAR(200) DEFAULT NULL COMMENT '包路径',
  `business_name` VARCHAR(100) DEFAULT NULL COMMENT '业务名称',
  `class_name` VARCHAR(100) DEFAULT NULL COMMENT 'Java类名',
  `class_comment` VARCHAR(500) DEFAULT NULL COMMENT '类描述',
  `author` VARCHAR(50) DEFAULT NULL COMMENT '作者',
  `template_type` INT(11) DEFAULT NULL COMMENT '模板类型',
  `parent_menu_id` BIGINT(20) DEFAULT NULL COMMENT '父菜单ID',
  `master_table_id` BIGINT(20) DEFAULT NULL COMMENT '主表ID',
  `sub_join_column_id` BIGINT(20) DEFAULT NULL COMMENT '子表关联主表的字段ID',
  `sub_join_many` TINYINT(1) DEFAULT NULL COMMENT '是否一对多关系',
  `tree_parent_column_id` BIGINT(20) DEFAULT NULL COMMENT '树表父字段ID',
  `tree_name_column_id` BIGINT(20) DEFAULT NULL COMMENT '树表名称字段ID',
  `code_files` TEXT COMMENT '生成的代码文件集合',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成表配置';

-- 创建代码生成列配置表
CREATE TABLE IF NOT EXISTS `codegen_column` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `table_id` BIGINT(20) NOT NULL COMMENT '所属表ID',
  `column_name` VARCHAR(100) NOT NULL COMMENT '数据库列名',
  `data_type` VARCHAR(50) NOT NULL COMMENT '数据库数据类型',
  `column_comment` VARCHAR(500) DEFAULT NULL COMMENT '列注释',
  `java_type` VARCHAR(50) NOT NULL COMMENT 'Java数据类型',
  `java_field` VARCHAR(100) NOT NULL COMMENT 'Java字段名',
  `primary_key` TINYINT(1) DEFAULT 0 COMMENT '是否主键',
  `auto_increment` TINYINT(1) DEFAULT 0 COMMENT '是否自增',
  `nullable` TINYINT(1) DEFAULT 1 COMMENT '是否可为空',
  `create_operation` TINYINT(1) DEFAULT 1 COMMENT '是否用于创建操作',
  `update_operation` TINYINT(1) DEFAULT 1 COMMENT '是否用于更新操作',
  `list_operation` TINYINT(1) DEFAULT 1 COMMENT '是否用于列表查询',
  `list_result_show` TINYINT(1) DEFAULT 1 COMMENT '是否在列表结果中展示',
  `list_query_condition` VARCHAR(50) DEFAULT NULL COMMENT '列表查询条件类型',
  `html_type` VARCHAR(50) DEFAULT 'input' COMMENT 'HTML表单控件类型',
  `dict_type` VARCHAR(100) DEFAULT NULL COMMENT '字典类型编码',
  `relation_table_name` VARCHAR(100) DEFAULT NULL COMMENT '关联表名',
  `relation_show_field` VARCHAR(100) DEFAULT NULL COMMENT '关联表展示字段',
  `relation_query_field` VARCHAR(100) DEFAULT NULL COMMENT '关联表查询字段',
  `extra_attrs` TEXT COMMENT '扩展属性，JSON格式',
  PRIMARY KEY (`id`),
  KEY `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成列配置';

-- 插入测试数据
-- 插入数据源
INSERT INTO `codegen_datasource` (`id`, `name`, `url`, `username`, `password`, `driver_class_name`) 
VALUES (1, '测试数据源', 'jdbc:mysql://localhost:3306/bone', 'root', 'mysql123', 'com.mysql.cj.jdbc.Driver');

-- 插入代码生成表配置
INSERT INTO `codegen_table` (`id`, `datasource_id`, `scene`, `table_name`, `table_comment`, `module_name`, `package_name`, `business_name`, `class_name`, `class_comment`, `author`, `template_type`) 
VALUES (1, 1, 1, 'user', '用户表', 'system', 'com.bone.system', '用户管理', 'User', '用户实体类', '测试用户', 1);

-- 插入代码生成列配置
INSERT INTO `codegen_column` (`id`, `table_id`, `column_name`, `data_type`, `column_comment`, `java_type`, `java_field`, `primary_key`, `auto_increment`, `nullable`, `create_operation`, `update_operation`, `list_operation`, `list_result_show`, `list_query_condition`, `html_type`)
VALUES 
(1, 1, 'id', 'bigint', '用户ID', 'Long', 'id', 1, 1, 0, 0, 0, 1, 1, 'EQ', 'hidden'),
(2, 1, 'username', 'varchar', '用户名', 'String', 'username', 0, 0, 0, 1, 1, 1, 1, 'LIKE', 'input'),
(3, 1, 'password', 'varchar', '密码', 'String', 'password', 0, 0, 0, 1, 1, 0, 0, 'EQ', 'password'),
(4, 1, 'nickname', 'varchar', '昵称', 'String', 'nickname', 0, 0, 1, 1, 1, 1, 1, 'LIKE', 'input'),
(5, 1, 'email', 'varchar', '邮箱', 'String', 'email', 0, 0, 1, 1, 1, 1, 1, 'LIKE', 'input'),
(6, 1, 'status', 'tinyint', '状态', 'Integer', 'status', 0, 0, 0, 1, 1, 1, 1, 'EQ', 'select'),
(7, 1, 'create_time', 'datetime', '创建时间', 'Date', 'createTime', 0, 0, 1, 1, 0, 1, 1, 'BETWEEN', 'datetime');