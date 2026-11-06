-- Source: docs/sql/release_schema_20260813.sql
ALTER TABLE call_record ALTER COLUMN agent_id DROP NOT NULL;
ALTER TABLE call_record ALTER COLUMN agent_number DROP NOT NULL;
ALTER TABLE call_record ALTER COLUMN agent_name DROP NOT NULL;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS record_url VARCHAR(500);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS record_duration INTEGER;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS record_status SMALLINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS gateway_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS gateway_name VARCHAR(128);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS from_gateway_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS from_gateway_name VARCHAR(128);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS did VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS original_callee VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS converted_callee VARCHAR(64);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS route_id BIGINT;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS route_name VARCHAR(128);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS record_start_time TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS record_end_time TIMESTAMP;
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS sip_status VARCHAR(32);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(512);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS satisfaction VARCHAR(20);
ALTER TABLE call_record ADD COLUMN IF NOT EXISTS satisfaction_time TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_call_record_gateway ON call_record(gateway_id);
CREATE INDEX IF NOT EXISTS idx_call_record_from_gateway ON call_record(from_gateway_id);
CREATE INDEX IF NOT EXISTS idx_call_record_did ON call_record(did);
CREATE INDEX IF NOT EXISTS idx_call_record_satisfaction ON call_record(satisfaction, satisfaction_time);

ALTER TABLE call_schedule ADD COLUMN IF NOT EXISTS voice_text VARCHAR(1000);
ALTER TABLE call_schedule ADD COLUMN IF NOT EXISTS time_type INTEGER DEFAULT 1;
ALTER TABLE call_schedule ADD COLUMN IF NOT EXISTS voice_file_id BIGINT;
ALTER TABLE call_schedule ADD COLUMN IF NOT EXISTS flow_id BIGINT;
UPDATE call_schedule SET time_type = 1 WHERE time_type IS NULL;

CREATE TABLE IF NOT EXISTS call_holiday (
    id BIGSERIAL PRIMARY KEY,
    holiday_date VARCHAR(10) NOT NULL,
    type INTEGER NOT NULL,
    name VARCHAR(100),
    create_by INTEGER,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by INTEGER,
    update_time TIMESTAMP,
    del_flag INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_call_holiday_date ON call_holiday(holiday_date);

CREATE TABLE IF NOT EXISTS customer (
    id BIGSERIAL PRIMARY KEY,
    customer_no VARCHAR(64),
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    backup_phone VARCHAR(32),
    gender SMALLINT DEFAULT 0,
    source SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 1,
    remark VARCHAR(500),
    last_call_record_id BIGINT,
    last_call_time TIMESTAMP,
    call_count INTEGER DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_customer_phone ON customer(phone);
CREATE INDEX IF NOT EXISTS idx_customer_backup_phone ON customer(backup_phone);

CREATE TABLE IF NOT EXISTS work_order (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    call_record_id BIGINT,
    call_id VARCHAR(64),
    caller_number VARCHAR(32),
    callee_number VARCHAR(32),
    agent_id BIGINT,
    agent_name VARCHAR(64),
    priority INTEGER DEFAULT 2,
    status INTEGER DEFAULT 0,
    handler_id BIGINT,
    handler_name VARCHAR(64),
    handle_remark TEXT,
    finish_time TIMESTAMP,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INTEGER DEFAULT 0
);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS customer_name VARCHAR(128);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS mobile_1 VARCHAR(32);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS mobile_2 VARCHAR(32);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS fixed_phone VARCHAR(32);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS customer_address VARCHAR(255);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS customer_type VARCHAR(64);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS company_name VARCHAR(255);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS work_order_type VARCHAR(64);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS number_location VARCHAR(128);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS business_type VARCHAR(128);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS issue_type VARCHAR(128);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS acceptance_content TEXT;
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS attachments_json TEXT;
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS satisfaction VARCHAR(32);
ALTER TABLE work_order ADD COLUMN IF NOT EXISTS satisfaction_time TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_work_order_call_id ON work_order(call_id);
CREATE INDEX IF NOT EXISTS idx_work_order_caller_number ON work_order(caller_number);
CREATE INDEX IF NOT EXISTS idx_work_order_mobile_1 ON work_order(mobile_1);
CREATE INDEX IF NOT EXISTS idx_work_order_customer_name ON work_order(customer_name);

UPDATE work_order AS work_order_target
SET satisfaction = call_record_source.satisfaction,
    satisfaction_time = call_record_source.satisfaction_time,
    update_time = CURRENT_TIMESTAMP
FROM call_record AS call_record_source
WHERE work_order_target.call_id = call_record_source.call_id
  AND call_record_source.satisfaction IS NOT NULL;

CREATE TABLE IF NOT EXISTS call_transcript (
    id BIGSERIAL PRIMARY KEY,
    call_id VARCHAR(64) NOT NULL,
    unique_id VARCHAR(128),
    caller_number VARCHAR(64),
    callee_number VARCHAR(64),
    agent_id BIGINT,
    agent_number VARCHAR(64),
    agent_name VARCHAR(128),
    direction INTEGER,
    provider VARCHAR(32),
    sample_rate INTEGER,
    full_text TEXT,
    segment_count INTEGER DEFAULT 0,
    char_count INTEGER DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    status VARCHAR(32),
    error_code VARCHAR(32),
    error_msg VARCHAR(500),
    record_url VARCHAR(500),
    first_text_latency_ms BIGINT,
    final_latency_ms BIGINT,
    reconnect_count INTEGER DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_call_transcript_call_id ON call_transcript(call_id);
CREATE INDEX IF NOT EXISTS idx_call_transcript_agent ON call_transcript(agent_id);
CREATE INDEX IF NOT EXISTS idx_call_transcript_status ON call_transcript(status);

CREATE TABLE IF NOT EXISTS call_transcript_segment (
    id BIGSERIAL PRIMARY KEY,
    transcript_id BIGINT,
    call_id VARCHAR(64) NOT NULL,
    segment_id VARCHAR(128) NOT NULL,
    seq INTEGER,
    speaker VARCHAR(16),
    text TEXT NOT NULL,
    is_final SMALLINT DEFAULT 1,
    begin_ms BIGINT,
    end_ms BIGINT,
    confidence DOUBLE PRECISION,
    provider VARCHAR(32)
);
CREATE INDEX IF NOT EXISTS idx_cts_transcript_id ON call_transcript_segment(transcript_id);
CREATE INDEX IF NOT EXISTS idx_cts_call_id ON call_transcript_segment(call_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cts_call_segment ON call_transcript_segment(call_id, segment_id);

CREATE TABLE IF NOT EXISTS tts_file (
    id BIGSERIAL PRIMARY KEY,
    cache_key VARCHAR(128),
    text_digest VARCHAR(128),
    speech_text TEXT,
    provider VARCHAR(32),
    voice VARCHAR(64),
    params_json TEXT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    listen_url VARCHAR(500),
    file_size BIGINT,
    duration_ms BIGINT,
    sample_rate INTEGER,
    format VARCHAR(16),
    status VARCHAR(32),
    error_code VARCHAR(32),
    error_msg VARCHAR(500),
    ref_count INTEGER DEFAULT 0,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag SMALLINT DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tts_file_cache_key ON tts_file(cache_key);

CREATE TABLE IF NOT EXISTS tts_task (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(128),
    cache_key VARCHAR(128),
    speech_text TEXT,
    provider VARCHAR(32),
    voice VARCHAR(64),
    params_json TEXT,
    file_id BIGINT,
    status VARCHAR(32),
    call_id VARCHAR(64),
    channel_uuid VARCHAR(128),
    error_code VARCHAR(32),
    error_msg VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag SMALLINT DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_tts_task_task_id ON tts_task(task_id);

-- Source: docs/sql/create_number_transform_rule.sql
-- 号码转换规则表
-- 用于呼出/呼入路由阶段对被叫/主叫号码进行去前缀、加前缀、整体替换与主叫替换。
-- 所有具体号码规则由数据库配置驱动，不在 Java 代码中硬编码。
-- "priority" 为 KingbaseES/PostgreSQL 保留字，必须带双引号。
CREATE TABLE IF NOT EXISTS number_transform_rule (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    direction       INT             NOT NULL,
    gateway_id      BIGINT,
    match_type      INT             NOT NULL,
    match_value     VARCHAR(255),
    strip_prefix    VARCHAR(64),
    add_prefix      VARCHAR(64),
    replace_to      VARCHAR(64),
    caller_replace  VARCHAR(64),
    min_length      INT,
    max_length      INT,
    "priority"      INT             DEFAULT 100,
    status          INT             DEFAULT 0,
    remark          VARCHAR(255),
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag        INT             DEFAULT 0
);

COMMENT ON TABLE number_transform_rule IS '号码转换规则(去前缀/加前缀/替换/主叫替换)';
COMMENT ON COLUMN number_transform_rule.id IS '主键ID';
COMMENT ON COLUMN number_transform_rule.name IS '规则名称';
COMMENT ON COLUMN number_transform_rule.direction IS '方向 1-呼入 2-呼出(与 DirectionEnum 一致)';
COMMENT ON COLUMN number_transform_rule.gateway_id IS '绑定网关ID(空=适用于所有网关)';
COMMENT ON COLUMN number_transform_rule.match_type IS '匹配类型 1-前缀 2-正则 3-长度 4-全匹配';
COMMENT ON COLUMN number_transform_rule.match_value IS '匹配值(前缀/正则/长度/完整号码)';
COMMENT ON COLUMN number_transform_rule.strip_prefix IS '去前缀';
COMMENT ON COLUMN number_transform_rule.add_prefix IS '加前缀';
COMMENT ON COLUMN number_transform_rule.replace_to IS '整体替换为(非空时直接替换结果)';
COMMENT ON COLUMN number_transform_rule.caller_replace IS '主叫替换为(非空时替换主叫)';
COMMENT ON COLUMN number_transform_rule.min_length IS '号码最小长度约束';
COMMENT ON COLUMN number_transform_rule.max_length IS '号码最大长度约束';
COMMENT ON COLUMN number_transform_rule.priority IS '优先级(数值小的优先匹配)';
COMMENT ON COLUMN number_transform_rule.status IS '状态 0-禁用 1-启用';
COMMENT ON COLUMN number_transform_rule.remark IS '备注';

CREATE INDEX idx_number_transform_rule_dir ON number_transform_rule(direction, status, del_flag);
CREATE INDEX idx_number_transform_rule_gw ON number_transform_rule(gateway_id);

-- ============ 示例规则(按实际 E1/运营商要求调整后再启用，默认不自动插入) ============
-- 注意: direction 与 DirectionEnum 一致 —— 1=呼入(INBOUND), 2=呼出(OUTBOUND)。
-- 需求: 将坐席拨打的 918xxxxxxxxx 转换为 E1 要求的 018xxxxxxxxx 目标格式。
-- 规则说明:
--   direction=2(呼出), match_type=1(前缀), match_value='9'  -> 命中以 9 开头的号码
--   strip_prefix='9'                                      -> 去掉接入码 9，保留 18xxxxxxxxx
--   add_prefix='0'                                        -> 前置国内长途 0，得到 018xxxxxxxxx
--   caller_replace 可设为运营商分配的外显号码              -> 主叫替换(按需)
-- INSERT INTO number_transform_rule
--   (name, direction, match_type, match_value, strip_prefix, add_prefix, caller_replace, "priority", status, remark)
-- VALUES
--   ('呼出-9转国内长途', 2, 1, '9', '9', '0', NULL, 10, 1, '918xxxxxxxxx -> 018xxxxxxxxx');
--
-- 另一常见场景: 仅去接入码 9，不加前缀(原样送出 18xxxxxxxxx)
-- INSERT INTO number_transform_rule
--   (name, direction, match_type, match_value, strip_prefix, add_prefix, "priority", status, remark)
-- VALUES
--   ('呼出-去接入码9', 2, 1, '9', '9', '', 20, 1, '918xxxxxxxxx -> 18xxxxxxxxx');

-- Source: docs/sql/fix_call_schedule_level_keyword.sql
-- ============================================================
-- Fix call_schedule table: rename "level" column to "priority_level"
-- Reason: "level" is a reserved keyword in KingbaseES (Oracle mode)
-- used in CONNECT BY hierarchical queries, causing SQL errors:
-- "ERROR: CONNECT BY clause required in this query block"
-- ============================================================

DO $$
BEGIN
    -- Check if old column "level" exists and new column doesn't
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'call_schedule' AND column_name = 'level'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'call_schedule' AND column_name = 'priority_level'
    ) THEN
        ALTER TABLE call_schedule RENAME COLUMN "level" TO priority_level;
        RAISE NOTICE 'Renamed call_schedule.level to priority_level';
    ELSE
        RAISE NOTICE 'Skip: column already renamed or does not exist';
    END IF;
END $$;

ALTER TABLE call_record ADD CONSTRAINT uk_call_record_call_id UNIQUE (call_id);
