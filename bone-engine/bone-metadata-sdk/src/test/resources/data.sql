-- 插入测试数据
INSERT INTO roles(id, name, code) VALUES (1, '管理员', 'ADMIN');
INSERT INTO roles(id, name, code) VALUES (2, '普通用户', 'USER');

INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (1, 'admin', '123456', 'admin@test.com', 1, 30, 1);
INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (2, 'user1', '123456', 'user1@test.com', 2, 25, 1);
INSERT INTO users(id, username, password, email, role_id, age, status) VALUES (3, 'user2', '123456', 'user2@test.com', 2, 28, 0);