-- 联系人角色改为可配置字典，新增联系人时可继续添加角色
INSERT INTO sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
VALUES ('联系人角色', 'support_contact_role', '0', 'admin', NOW(), '现场融合联系人角色')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), status=VALUES(status), remark=VALUES(remark);

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '技术', 'TECH', 'support_contact_role', '', 'primary', 'Y', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'TECH');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '管理', 'MANAGER', 'support_contact_role', '', 'success', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'MANAGER');

INSERT INTO sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 3, '商务', 'BIZ', 'support_contact_role', '', 'warning', 'N', '0', 'admin', NOW(), '内置联系人角色'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'support_contact_role' AND dict_value = 'BIZ');
