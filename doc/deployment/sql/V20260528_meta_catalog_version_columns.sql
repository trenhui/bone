-- 元数据 catalog：meta_field / meta_entity_relation 增加乐观锁 version 列
-- 适用已部署库增量升级；新库请直接使用 bone-init.sql

ALTER TABLE meta_field
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER deleted;

ALTER TABLE meta_entity_relation
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER deleted;
