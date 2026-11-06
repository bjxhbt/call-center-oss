CREATE TEMP TABLE och_release_menu (
    menu_id BIGINT,
    menu_name VARCHAR(64),
    parent_id BIGINT,
    order_num INTEGER,
    path VARCHAR(255),
    component VARCHAR(255),
    menu_type CHAR(1),
    visible INTEGER,
    status INTEGER,
    perms VARCHAR(255),
    icon VARCHAR(255),
    remark VARCHAR(500)
) ON COMMIT DROP;

INSERT INTO och_release_menu VALUES
    (6, '通信资源', 0, 4, '#', NULL, 'M', 0, 0, NULL, 'DatabaseOutlined', '号码、语音、录音和存储资源'),
    (504, '录音管理', 6, 5, '/recordingManagement', 'CallingConfigure/RecordingManagement/index', 'C', 0, 0, NULL, NULL, '集中录音查询与播放'),
    (505, '存储管理', 6, 6, '/storageManagement', 'CallingConfigure/StorageManagement/index', 'C', 0, 0, 'storage:list', NULL, '录音集中存储配置与状态'),
    (506, '实时监控', 3, 2, '/realtimeMonitor', 'CallingConfigure/RealtimeMonitor/index', 'C', 0, 0, 'monitor:view', NULL, '坐席实时状态与通话监控'),
    (507, '工单管理', 3, 5, '/workOrder', 'CallingConfigure/WorkOrder/index', 'C', 0, 0, 'workorder:list', NULL, '来电受理工单与满意度'),
    (508, '节假日管理', 3, 8, '/holidayManagement', 'CallingConfigure/HolidayManagement/index', 'C', 0, 0, 'call:holiday:page:list', NULL, '节假日与调休维护'),
    (509, '坐席工作台', 3, 1, '/agentWorkspace', 'AgentWorkspace/index', 'C', 0, 0, 'call:workspace:view', NULL, '坐席当前通话、客户查询与工单工作台'),
    (510, '实时转写记录', 5, 1, '/transcriptManagement', 'TranscriptManagement/index', 'C', 0, 0, 'ai:transcript:list', NULL, 'ASR 转写摘要、分段文本与时延查询'),
    (511, 'TTS 配置与试听', 5, 2, '/ttsConfig', 'TtsConfig/index', 'C', 0, 0, 'ai:tts:use', NULL, '统一 TTS 参数配置、合成与试听'),
    (512, 'TTS 文件管理', 5, 3, '/ttsFileManagement', 'TtsFileManagement/index', 'C', 0, 0, 'ai:tts:file:list', NULL, 'TTS 合成文件、缓存和引用管理'),
    (513, '语音服务状态', 5, 4, '/aiServiceStatus', 'AiServiceStatus/index', 'C', 0, 0, 'ai:asr:status', NULL, 'ASR/TTS Provider 状态与模拟链路'),
    (514, '查看转写详情', 510, 1, '', NULL, 'F', 1, 0, 'ai:transcript:get', NULL, '查看转写摘要与分段明细'),
    (515, '删除 TTS 文件', 512, 1, '', NULL, 'F', 1, 0, 'ai:tts:file:delete', NULL, '删除无业务引用的 TTS 文件'),
    (516, '执行 ASR 模拟', 513, 1, '', NULL, 'F', 1, 0, 'ai:asr:simulate', NULL, '执行本地 WAV ASR 模拟链路');

UPDATE sys_menu target
SET menu_name = source.menu_name,
    parent_id = source.parent_id,
    order_num = source.order_num,
    path = source.path,
    component = source.component,
    menu_type = source.menu_type,
    visible = source.visible,
    status = source.status,
    perms = source.perms,
    icon = source.icon,
    remark = source.remark,
    update_by = 1,
    update_time = CURRENT_TIMESTAMP,
    del_flag = 0
FROM och_release_menu source
WHERE target.menu_id = source.menu_id;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, is_frame,
    menu_type, visible, status, perms, icon, remark,
    create_by, create_time, update_by, update_time, del_flag
)
SELECT source.menu_id, source.menu_name, source.parent_id, source.order_num,
       source.path, source.component, 1, source.menu_type, source.visible,
       source.status, source.perms, source.icon, source.remark,
       1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0
FROM och_release_menu source
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu target WHERE target.menu_id = source.menu_id
);

WITH admin_roles AS (
    SELECT role_id FROM sys_app_role WHERE role_key = 'ROLE_ADMIN' AND status = 0 AND del_flag = 0
), active_menus AS (
    SELECT menu_id FROM sys_menu WHERE del_flag = 0
)
UPDATE sys_role_menu target
SET del_flag = 0, update_by = 1, update_time = CURRENT_TIMESTAMP
FROM admin_roles role_item CROSS JOIN active_menus menu_item
WHERE target.role_id = role_item.role_id
  AND target.menu_id = menu_item.menu_id
  AND target.del_flag <> 0;

WITH admin_roles AS (
    SELECT role_id FROM sys_app_role WHERE role_key = 'ROLE_ADMIN' AND status = 0 AND del_flag = 0
), active_menus AS (
    SELECT menu_id FROM sys_menu WHERE del_flag = 0
)
INSERT INTO sys_role_menu (role_id, menu_id, create_by, create_time, update_by, update_time, del_flag)
SELECT role_item.role_id, menu_item.menu_id, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0
FROM admin_roles role_item CROSS JOIN active_menus menu_item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu target
    WHERE target.role_id = role_item.role_id AND target.menu_id = menu_item.menu_id
);

DO $$
DECLARE
    v_menu_max BIGINT;
    v_role_menu_max BIGINT;
BEGIN
    SELECT COALESCE(MAX(menu_id), 0) INTO v_menu_max FROM sys_menu;
    SELECT COALESCE(MAX(id), 0) INTO v_role_menu_max FROM sys_role_menu;
    PERFORM setval('sys_menu_menu_id_seq', v_menu_max + 1, false);
    PERFORM setval('sys_role_menu_id_seq', v_role_menu_max + 1, false);
END $$;
