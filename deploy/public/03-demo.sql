-- Synthetic data only. admin_hash is supplied by the operator, never exported from a database.
INSERT INTO sys_app_user
    (user_id, user_name, nick_name, password, status, create_time, del_flag)
VALUES (1, 'admin', 'Demo administrator', :'admin_hash', 1, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_app_role
    (role_id, role_name, role_key, role_sort, data_scope, status, create_time, del_flag)
VALUES (1, 'Demo administrator', 'ROLE_ADMIN', 1, 1, 0, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_user_role (id, user_id, role_id, create_time, del_flag)
VALUES (1, 1, 1, CURRENT_TIMESTAMP, 0);

INSERT INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, menu_type, visible, status, create_time, del_flag)
VALUES
    (1, 'Overview', 0, 1, '/overview', 'OverView/index', 'C', 0, 0, CURRENT_TIMESTAMP, 0),
    (3, 'Call operations', 0, 2, '#', NULL, 'M', 0, 0, CURRENT_TIMESTAMP, 0),
    (5, 'Voice services', 0, 3, '#', NULL, 'M', 0, 0, CURRENT_TIMESTAMP, 0),
    (6, 'Communication', 0, 4, '#', NULL, 'M', 0, 0, CURRENT_TIMESTAMP, 0),
    (501, 'IVR', 6, 1, '/ivrManagement', 'IVRManagement/index', 'C', 0, 0, CURRENT_TIMESTAMP, 0);

-- No SIP users, FreeSWITCH servers, customers, recordings or cloud credentials are seeded.
INSERT INTO sys_category (id, type, name, parent_id, flag, create_time, del_flag)
VALUES (1, 3, 'Public IVR demo', 0, 0, CURRENT_TIMESTAMP, 0);

INSERT INTO call_skill
    (id, name, priority, strategy_type, full_busy_type, time_out, queue_length, create_time, del_flag)
VALUES (1, 'demo-support', 1, 1, 1, 30, 10, CURRENT_TIMESTAMP, 0);

SELECT setval(pg_get_serial_sequence('sys_app_user', 'user_id'), 1);
SELECT setval(pg_get_serial_sequence('sys_app_role', 'role_id'), 1);
SELECT setval(pg_get_serial_sequence('sys_user_role', 'id'), 1);
SELECT setval(pg_get_serial_sequence('sys_menu', 'menu_id'), 501);
SELECT setval(pg_get_serial_sequence('sys_category', 'id'), 1);
SELECT setval(pg_get_serial_sequence('call_skill', 'id'), 1);
