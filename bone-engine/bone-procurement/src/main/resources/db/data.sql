-- 供应商表初始数据
INSERT INTO procurement_supplier (code, name, phone_number, contact_person, email, address, 
                                 business_license, register_date, supplier_level, credit_score, 
                                 cooperation_status, last_cooperation_date, total_order_amount, 
                                 order_count, average_delivery_rate, average_quality_rate, complaint_count)
VALUES 
('SUP000001', '创新科技有限公司', '13800138001', '张经理', 'contact@innovtech.com', '北京市海淀区科技园路1号', 
 '91110108MA00123456', '2015-05-15', 'A级', 95, '合作中', DATE_SUB(CURDATE(), INTERVAL 10 DAY), 5000000.00, 
 120, 0.98, 0.99, 2),

('SUP000002', '诚信贸易公司', '13900139002', '李总', 'sales@chengxin.com', '上海市浦东新区贸易大道88号', 
 '91310115MA00654321', '2018-03-22', 'B级', 82, '合作中', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 3500000.00, 
 85, 0.95, 0.96, 5),

('SUP000003', '快速配送中心', '13700137003', '王主管', 'delivery@expresscenter.com', '广州市天河区物流园B区12栋', 
 '91440106MA00789012', '2020-07-10', 'C级', 75, '合作中', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1800000.00, 
 200, 0.99, 0.93, 8);

-- 插入测试数据，避免重复插入
INSERT INTO procurement_supplier (code, name, phone_number, contact_person, email, address, 
                                 business_license, register_date, supplier_level, credit_score, 
                                 cooperation_status, last_cooperation_date, total_order_amount, 
                                 order_count, average_delivery_rate, average_quality_rate, complaint_count)
SELECT 'SUP000004', '优质材料厂', '13600136004', '刘厂长', 'info@qualitymaterials.com', '深圳市龙岗区工业区25号', 
       '91440300MA5G432109', '2017-09-01', 'A级', 92, '合作中', DATE_SUB(CURDATE(), INTERVAL 15 DAY), 4200000.00, 
       95, 0.97, 0.98, 3
WHERE NOT EXISTS (SELECT 1 FROM procurement_supplier WHERE code = 'SUP000004');

INSERT INTO procurement_supplier (code, name, phone_number, contact_person, email, address, 
                                 business_license, register_date, supplier_level, credit_score, 
                                 cooperation_status, last_cooperation_date, total_order_amount, 
                                 order_count, average_delivery_rate, average_quality_rate, complaint_count)
SELECT 'SUP000005', '绿色包装公司', '13500135005', '陈经理', 'green@eco-packaging.com', '杭州市西湖区环保科技园8号楼', 
       '91330106MA2B1C2D3E', '2019-11-15', 'B级', 88, '合作中', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2800000.00, 
       150, 0.96, 0.97, 4
WHERE NOT EXISTS (SELECT 1 FROM procurement_supplier WHERE code = 'SUP000005');