-- MySQL 8.4 / InnoDB / utf8mb4. Fresh EMPTY database only.
-- Generated from sanitized deploy/public schema + upgrades; no business data.

CREATE TABLE `call_display` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(18) NULL,
  `type` SMALLINT NOT NULL,
  `area` VARCHAR(64) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_display_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(18) NULL,
  `type` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `tenant_id` BIGINT NOT NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_display_pool_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pool_id` BIGINT NOT NULL,
  `display_id` BIGINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_engine` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `profile` VARCHAR(128) NULL,
  `type` SMALLINT NOT NULL,
  `providers` SMALLINT NOT NULL,
  `timbre` VARCHAR(200) NULL,
  `grammar` VARCHAR(200) NULL,
  `param` VARCHAR(200) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_holiday` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `holiday_date` VARCHAR(10) NOT NULL,
  `type` INT NOT NULL,
  `name` VARCHAR(100) NULL,
  `create_by` INT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` INT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` INT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_call_holiday_date` (`holiday_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_in_phone` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NULL,
  `phone` VARCHAR(32) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_in_phone_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `call_in_id` BIGINT NOT NULL,
  `schedule_id` BIGINT NOT NULL,
  `route_type` SMALLINT NULL,
  `route_value` VARCHAR(50) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `call_id` VARCHAR(64) NULL,
  `task_id` BIGINT NULL,
  `customer_id` BIGINT NULL,
  `caller_number` VARCHAR(32) NULL,
  `caller_display_number` VARCHAR(32) NULL,
  `callee_number` VARCHAR(32) NULL,
  `callee_display_number` VARCHAR(32) NULL,
  `number_location` VARCHAR(128) NULL,
  `agent_id` BIGINT NULL,
  `agent_number` VARCHAR(32) NULL,
  `agent_name` VARCHAR(128) NULL,
  `call_state` SMALLINT NOT NULL,
  `direction` SMALLINT NULL,
  `call_start_time` DATETIME(6) NULL,
  `call_end_time` DATETIME(6) NULL,
  `answer_flag` SMALLINT NOT NULL,
  `answer_time` DATETIME(6) NULL,
  `ringing_time` DATETIME(6) NULL,
  `hangup_dir` SMALLINT NULL,
  `hangup_cause_code` SMALLINT NULL,
  `file_path` VARCHAR(500) NULL,
  `ringing_path` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  `record_url` VARCHAR(500) NULL,
  `record_duration` SMALLINT NULL,
  `record_status` SMALLINT NULL,
  `gateway_id` BIGINT NULL,
  `gateway_name` VARCHAR(128) NULL,
  `from_gateway_id` BIGINT NULL,
  `from_gateway_name` VARCHAR(128) NULL,
  `did` VARCHAR(64) NULL,
  `original_callee` VARCHAR(64) NULL,
  `converted_callee` VARCHAR(64) NULL,
  `route_id` BIGINT NULL,
  `route_name` VARCHAR(128) NULL,
  `record_start_time` DATETIME(6) NULL,
  `record_end_time` DATETIME(6) NULL,
  `sip_status` VARCHAR(32) NULL,
  `failure_reason` VARCHAR(512) NULL,
  `satisfaction` VARCHAR(20) NULL,
  `satisfaction_time` DATETIME(6) NULL,
  KEY `idx_call_record_gateway` (`gateway_id`),
  KEY `idx_call_record_from_gateway` (`from_gateway_id`),
  KEY `idx_call_record_did` (`did`),
  KEY `idx_call_record_satisfaction` (`satisfaction`, `satisfaction_time`),
  UNIQUE KEY `uk_call_record_call_id` (`call_id`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NULL,
  `route_num` VARCHAR(32) NULL,
  `type` SMALLINT NULL,
  `level` SMALLINT NULL,
  `status` SMALLINT NULL,
  `schedule_id` BIGINT NULL,
  `route_type` SMALLINT NULL,
  `route_value` VARCHAR(200) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NULL,
  `priority_level` SMALLINT NOT NULL,
  `type` SMALLINT NOT NULL,
  `start_day` VARCHAR(50) NULL,
  `end_day` VARCHAR(50) NULL,
  `start_time` VARCHAR(50) NULL,
  `end_time` VARCHAR(50) NULL,
  `work_cycle` VARCHAR(20) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  `voice_text` VARCHAR(500) NULL,
  `time_type` INT NULL DEFAULT 1,
  `voice_file_id` BIGINT NULL,
  `flow_id` BIGINT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_skill` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_id` BIGINT NULL,
  `name` VARCHAR(128) NULL,
  `priority` SMALLINT NOT NULL,
  `describe` VARCHAR(128) NULL,
  `strategy_type` SMALLINT NOT NULL,
  `full_busy_type` SMALLINT NULL,
  `overflow_type` SMALLINT NULL,
  `overflow_value` VARCHAR(32) NULL,
  `time_out` SMALLINT NULL,
  `queue_length` SMALLINT NULL,
  `queue_voice` SMALLINT NULL,
  `agent_voice` SMALLINT NULL,
  `caller_phone_pool` SMALLINT NULL,
  `callee_phone_pool` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_skill_agent_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `skill_id` BIGINT NULL,
  `agent_id` VARCHAR(128) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `type` SMALLINT NOT NULL,
  `status` SMALLINT NOT NULL,
  `priority` SMALLINT NULL,
  `start_day` DATETIME(6) NOT NULL,
  `end_day` DATETIME(6) NOT NULL,
  `s_time` VARCHAR(50) NULL,
  `e_time` VARCHAR(50) NULL,
  `work_cycle` VARCHAR(20) NULL,
  `assignment_type` SMALLINT NULL,
  `is_priority` SMALLINT NULL,
  `receive_limit` SMALLINT NULL,
  `agent_list` JSON NULL,
  `complete_type` SMALLINT NOT NULL,
  `phone_pool_id` BIGINT NOT NULL,
  `transfer_type` SMALLINT NOT NULL,
  `transfer_value` VARCHAR(120) NULL,
  `recall` SMALLINT NOT NULL,
  `recall_num` SMALLINT NULL,
  `recall_time` SMALLINT NULL,
  `remark` VARCHAR(200) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_task_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `phone` VARCHAR(20) NULL,
  `name` VARCHAR(50) NULL,
  `sex` SMALLINT NOT NULL,
  `ext` JSON NULL,
  `source` SMALLINT NOT NULL,
  `crowd_id` BIGINT NULL,
  `template_id` BIGINT NULL,
  `status` SMALLINT NOT NULL,
  `agent_id` BIGINT NULL,
  `assignment_time` DATETIME(6) NULL,
  `call_status` SMALLINT NOT NULL,
  `attempt_count` SMALLINT NULL,
  `scheduled_time` DATETIME(6) NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_transcript` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `call_id` VARCHAR(64) NOT NULL,
  `unique_id` VARCHAR(128) NULL,
  `caller_number` VARCHAR(64) NULL,
  `callee_number` VARCHAR(64) NULL,
  `agent_id` BIGINT NULL,
  `agent_number` VARCHAR(64) NULL,
  `agent_name` VARCHAR(128) NULL,
  `direction` INT NULL,
  `provider` VARCHAR(32) NULL,
  `sample_rate` INT NULL,
  `full_text` LONGTEXT NULL,
  `segment_count` INT NULL DEFAULT 0,
  `char_count` INT NULL DEFAULT 0,
  `start_time` DATETIME(6) NULL,
  `end_time` DATETIME(6) NULL,
  `duration_ms` BIGINT NULL,
  `status` VARCHAR(32) NULL,
  `error_code` VARCHAR(32) NULL,
  `error_msg` VARCHAR(500) NULL,
  `record_url` VARCHAR(500) NULL,
  `first_text_latency_ms` BIGINT NULL,
  `final_latency_ms` BIGINT NULL,
  `reconnect_count` INT NULL DEFAULT 0,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` SMALLINT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_call_transcript_call_id` (`call_id`),
  KEY `idx_call_transcript_agent` (`agent_id`),
  KEY `idx_call_transcript_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `call_transcript_segment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `transcript_id` BIGINT NULL,
  `call_id` VARCHAR(64) NOT NULL,
  `segment_id` VARCHAR(128) NOT NULL,
  `seq` INT NULL,
  `speaker` VARCHAR(16) NULL,
  `text` LONGTEXT NOT NULL,
  `is_final` SMALLINT NULL DEFAULT 1,
  `begin_ms` BIGINT NULL,
  `end_ms` BIGINT NULL,
  `confidence` DOUBLE NULL,
  `provider` VARCHAR(32) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cts_transcript_id` (`transcript_id`),
  KEY `idx_cts_call_id` (`call_id`),
  UNIQUE KEY `uq_cts_call_segment` (`call_id`, `segment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `customer_no` VARCHAR(64) NULL,
  `name` VARCHAR(64) NOT NULL,
  `phone` VARCHAR(32) NOT NULL,
  `backup_phone` VARCHAR(32) NULL,
  `gender` SMALLINT NULL DEFAULT 0,
  `source` SMALLINT NULL DEFAULT 0,
  `status` SMALLINT NULL DEFAULT 1,
  `remark` VARCHAR(500) NULL,
  `last_call_record_id` BIGINT NULL,
  `last_call_time` DATETIME(6) NULL,
  `call_count` INT NULL DEFAULT 0,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` SMALLINT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_customer_phone` (`phone`),
  KEY `idx_customer_backup_phone` (`backup_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_crowd` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `status` SMALLINT NOT NULL,
  `type` SMALLINT NULL,
  `progress` SMALLINT NULL,
  `reason` VARCHAR(200) NULL,
  `swipe` JSON NOT NULL,
  `crowd_num` SMALLINT NULL,
  `remark` VARCHAR(200) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_crowd_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `crowd_id` BIGINT NOT NULL,
  `customer_id` BIGINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crowd_customer` (`crowd_id`, `customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_field` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `field_label` VARCHAR(100) NULL,
  `field_name` VARCHAR(100) NULL,
  `status` SMALLINT NOT NULL,
  `field_type` SMALLINT NOT NULL,
  `required` SMALLINT NULL,
  `options` JSON NULL,
  `sys_type` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_seas` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `third_id` VARCHAR(64) NULL,
  `customer_info` JSON NULL,
  `phone` VARCHAR(20) NULL,
  `name` VARCHAR(50) NULL,
  `sex` SMALLINT NULL,
  `source` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NULL,
  `status` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `customer_template_field_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT NOT NULL,
  `field_id` BIGINT NOT NULL,
  `hidden` SMALLINT NOT NULL,
  `sort` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `flow_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `name` VARCHAR(255) NULL,
  `flow_desc` VARCHAR(255) NULL,
  `status` SMALLINT NULL,
  `flow_data` LONGTEXT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `flow_instances` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `call_id` BIGINT NOT NULL,
  `flow_id` BIGINT NOT NULL,
  `status` SMALLINT NULL,
  `start_time` DATETIME(6) NULL,
  `end_time` DATETIME(6) NULL,
  `current_node_id` VARCHAR(64) NULL,
  `variables` LONGTEXT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `flow_node_execution_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT NOT NULL,
  `node_id` VARCHAR(64) NULL,
  `status` SMALLINT NOT NULL,
  `start_time` DATETIME(6) NULL,
  `end_time` DATETIME(6) NULL,
  `duration` SMALLINT NULL,
  `description` VARCHAR(1000) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_acl` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `default_type` VARCHAR(20) NULL,
  `list_id` BIGINT NULL,
  `node_type` VARCHAR(20) NULL,
  `cidr` VARCHAR(64) NULL,
  `domain` VARCHAR(64) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_cdr` (
  `call_id` BIGINT NOT NULL,
  `uuid` VARCHAR(64) NULL,
  `direction` VARCHAR(16) NULL,
  `sip_local_network_addr` VARCHAR(32) NULL,
  `sip_network_ip` VARCHAR(32) NULL,
  `caller_id_number` VARCHAR(64) NULL,
  `caller_display` VARCHAR(64) NULL,
  `destination_number` VARCHAR(64) NULL,
  `destination_display` VARCHAR(64) NULL,
  `start_stamp` DATETIME(6) NULL,
  `answer_stamp` DATETIME(6) NULL,
  `end_stamp` DATETIME(6) NULL,
  `bridge_stamp` DATETIME(6) NULL,
  `progress_stamp` DATETIME(6) NULL,
  `duration` SMALLINT NULL,
  `answer_sec` SMALLINT NULL,
  `bill_sec` SMALLINT NULL,
  `hangup_cause` VARCHAR(64) NULL,
  `record_start_time` DATETIME(6) NULL,
  `record_end_time` DATETIME(6) NULL,
  `record_sec` SMALLINT NULL,
  `record` VARCHAR(128) NULL,
  `read_codec` VARCHAR(64) NULL,
  `write_codec` VARCHAR(64) NULL,
  `sip_hangup_disposition` VARCHAR(64) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NULL,
  `group_name` VARCHAR(64) NULL,
  `ip` VARCHAR(64) NULL,
  `port` SMALLINT NULL,
  `user_name` VARCHAR(64) NULL,
  `password` VARCHAR(64) NULL,
  `status` SMALLINT NULL,
  `out_time` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_dialplan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `group_id` BIGINT NOT NULL,
  `name` VARCHAR(128) NULL,
  `type` VARCHAR(64) NULL,
  `expression` VARCHAR(100) NULL,
  `context_name` VARCHAR(32) NULL,
  `content` VARCHAR(1000) NULL,
  `describe` VARCHAR(256) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_modules` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `fs_name` VARCHAR(128) NULL,
  `name` VARCHAR(128) NULL,
  `type` VARCHAR(64) NULL,
  `content` VARCHAR(1000) NULL,
  `describe` VARCHAR(256) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `fs_sip_gateway` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `user_name` VARCHAR(50) NULL,
  `password` VARCHAR(64) NULL,
  `realm` VARCHAR(32) NULL,
  `proxy` VARCHAR(32) NULL,
  `register` SMALLINT NULL,
  `transport` SMALLINT NULL,
  `caller_id_in_from` SMALLINT NULL,
  `from_domain` VARCHAR(32) NULL,
  `retry_time` SMALLINT NULL,
  `ping_time` SMALLINT NULL,
  `expire_time` SMALLINT NULL,
  `type` SMALLINT NULL,
  `gateway_type` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `kb_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `parent_id` BIGINT NULL DEFAULT 0,
  `sort` INT NULL DEFAULT 0,
  `flag` INT NULL DEFAULT 0,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` INT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb_category_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `kb_entry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `category_id` BIGINT NULL,
  `title` VARCHAR(255) NOT NULL,
  `content` LONGTEXT NULL,
  `keywords` VARCHAR(500) NULL,
  `status` INT NULL DEFAULT 1,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` INT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_kb_entry_category_id` (`category_id`),
  KEY `idx_kb_entry_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `ko_acc` (
  `id` BIGINT NOT NULL,
  `method` VARCHAR(16) NULL,
  `from_tag` VARCHAR(128) NULL,
  `to_tag` VARCHAR(128) NULL,
  `callid` VARCHAR(255) NULL,
  `sip_code` VARCHAR(3) NULL,
  `sip_reason` VARCHAR(128) NULL,
  `time` DATETIME(6) NOT NULL,
  `src_user` VARCHAR(64) NULL,
  `src_domain` VARCHAR(128) NULL,
  `src_ip` VARCHAR(64) NULL,
  `dst_ouser` VARCHAR(64) NULL,
  `dst_user` VARCHAR(64) NULL,
  `dst_domain` VARCHAR(128) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `ko_address` (
  `id` BIGINT NOT NULL,
  `grp` SMALLINT NOT NULL,
  `ip_addr` VARCHAR(50) NULL,
  `mask` SMALLINT NOT NULL,
  `port` SMALLINT NOT NULL,
  `tag` VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `ko_dispatcher` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `setid` SMALLINT NOT NULL,
  `destination` VARCHAR(192) NULL,
  `flags` SMALLINT NOT NULL,
  `priority` SMALLINT NOT NULL,
  `attrs` VARCHAR(128) NULL,
  `description` VARCHAR(64) NULL,
  `status` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `ko_location` (
  `id` BIGINT NOT NULL,
  `ruid` VARCHAR(64) NULL,
  `username` VARCHAR(64) NULL,
  `domain` VARCHAR(64) NULL,
  `contact` VARCHAR(512) NULL,
  `received` VARCHAR(128) NULL,
  `path` VARCHAR(512) NULL,
  `expires` DATETIME(6) NOT NULL,
  `q` FLOAT NOT NULL,
  `callid` VARCHAR(255) NULL,
  `cseq` SMALLINT NOT NULL,
  `last_modified` DATETIME(6) NOT NULL,
  `flags` SMALLINT NOT NULL,
  `cflags` SMALLINT NOT NULL,
  `user_agent` VARCHAR(255) NULL,
  `socket` VARCHAR(64) NULL,
  `methods` SMALLINT NULL,
  `instance` VARCHAR(255) NULL,
  `reg_id` BIGINT NOT NULL,
  `server_id` BIGINT NOT NULL,
  `connection_id` BIGINT NOT NULL,
  `keepalive` SMALLINT NOT NULL,
  `partition` SMALLINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `ko_subscriber` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NULL,
  `domain` VARCHAR(64) NULL,
  `password` VARCHAR(64) NULL,
  `ha1` VARCHAR(128) NULL,
  `ha1b` VARCHAR(128) NULL,
  `vmpin` VARCHAR(8) NULL,
  `status` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `number_transform_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `direction` INT NOT NULL,
  `gateway_id` BIGINT NULL,
  `match_type` INT NOT NULL,
  `match_value` VARCHAR(255) NULL,
  `strip_prefix` VARCHAR(64) NULL,
  `add_prefix` VARCHAR(64) NULL,
  `replace_to` VARCHAR(64) NULL,
  `caller_replace` VARCHAR(64) NULL,
  `min_length` INT NULL,
  `max_length` INT NULL,
  `priority` INT NULL DEFAULT 100,
  `status` INT NULL DEFAULT 0,
  `remark` VARCHAR(255) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` INT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_number_transform_rule_dir` (`direction`, `status`, `del_flag`),
  KEY `idx_number_transform_rule_gw` (`gateway_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `och_area_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `province_name` VARCHAR(100) NULL,
  `city_name` VARCHAR(100) NULL,
  `province_adcode` SMALLINT NULL,
  `province_center` VARCHAR(100) NULL,
  `city_code` VARCHAR(6) NULL,
  `city_adcode` SMALLINT NULL,
  `city_center` VARCHAR(100) NULL,
  `districts` VARCHAR(1000) NULL,
  `iso_code` VARCHAR(20) NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `och_region` (
  `region_id` VARCHAR(11) NULL,
  `region_name` VARCHAR(255) NULL,
  `parent_id` VARCHAR(255) NULL,
  `short_name` VARCHAR(255) NULL,
  `level` VARCHAR(255) NULL,
  `city_code` VARCHAR(255) NULL,
  `zip_code` VARCHAR(255) NULL,
  `merger_name` VARCHAR(255) NULL,
  `lng` VARCHAR(255) NULL,
  `lat` VARCHAR(255) NULL,
  `pinyin` VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `phone_location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pref` VARCHAR(10) NULL,
  `phone` VARCHAR(20) NULL,
  `province` VARCHAR(45) NULL,
  `city` VARCHAR(45) NULL,
  `isp` VARCHAR(45) NULL,
  `isp_type` SMALLINT NOT NULL,
  `post_code` VARCHAR(100) NULL,
  `city_code` VARCHAR(10) NULL,
  `area_code` VARCHAR(100) NULL,
  `create_time` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `blob_data` LONGBLOB NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_calendars` (
  `sched_name` VARCHAR(120) NULL,
  `calendar_name` VARCHAR(190) NULL,
  `calendar` LONGBLOB NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `cron_expression` VARCHAR(120) NULL,
  `time_zone_id` VARCHAR(80) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `entry_id` VARCHAR(95) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `instance_name` VARCHAR(190) NULL,
  `fired_time` BIGINT NOT NULL,
  `sched_time` BIGINT NOT NULL,
  `priority` BIGINT NOT NULL,
  `state` VARCHAR(16) NULL,
  `job_name` VARCHAR(190) NULL,
  `job_group` VARCHAR(190) NULL,
  `is_nonconcurrent` VARCHAR(1) NULL,
  `requests_recovery` VARCHAR(1) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_job_details` (
  `sched_name` VARCHAR(120) NULL,
  `job_name` VARCHAR(190) NULL,
  `job_group` VARCHAR(190) NULL,
  `description` VARCHAR(250) NULL,
  `job_class_name` VARCHAR(250) NULL,
  `is_durable` VARCHAR(1) NULL,
  `is_nonconcurrent` VARCHAR(1) NULL,
  `is_update_data` VARCHAR(1) NULL,
  `requests_recovery` VARCHAR(1) NULL,
  `job_data` LONGBLOB NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_locks` (
  `sched_name` VARCHAR(120) NULL,
  `lock_name` VARCHAR(40) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_group` VARCHAR(190) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` VARCHAR(120) NULL,
  `instance_name` VARCHAR(190) NULL,
  `last_checkin_time` BIGINT NOT NULL,
  `checkin_interval` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `repeat_count` BIGINT NOT NULL,
  `repeat_interval` BIGINT NOT NULL,
  `times_triggered` BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `str_prop_1` VARCHAR(512) NULL,
  `str_prop_2` VARCHAR(512) NULL,
  `str_prop_3` VARCHAR(512) NULL,
  `int_prop_1` BIGINT NULL,
  `int_prop_2` BIGINT NULL,
  `long_prop_1` BIGINT NULL,
  `long_prop_2` BIGINT NULL,
  `dec_prop_1` FLOAT NULL,
  `dec_prop_2` FLOAT NULL,
  `bool_prop_1` VARCHAR(1) NULL,
  `bool_prop_2` VARCHAR(1) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `qrtz_triggers` (
  `sched_name` VARCHAR(120) NULL,
  `trigger_name` VARCHAR(190) NULL,
  `trigger_group` VARCHAR(190) NULL,
  `job_name` VARCHAR(190) NULL,
  `job_group` VARCHAR(190) NULL,
  `description` VARCHAR(250) NULL,
  `next_fire_time` BIGINT NULL,
  `prev_fire_time` BIGINT NULL,
  `priority` BIGINT NULL,
  `trigger_state` VARCHAR(16) NULL,
  `trigger_type` VARCHAR(8) NULL,
  `start_time` BIGINT NOT NULL,
  `end_time` BIGINT NULL,
  `calendar_name` VARCHAR(190) NULL,
  `misfire_instr` BIGINT NULL,
  `job_data` LONGBLOB NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sip_agent` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NULL,
  `user_id` BIGINT NOT NULL,
  `agent_number` VARCHAR(64) NULL,
  `status` SMALLINT NOT NULL,
  `online_status` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_app_role` (
  `role_id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(30) NULL,
  `role_key` VARCHAR(100) NULL,
  `role_sort` SMALLINT NOT NULL,
  `data_scope` SMALLINT NULL,
  `status` SMALLINT NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL,
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_app_user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(50) NULL,
  `nick_name` VARCHAR(50) NULL,
  `password` VARCHAR(64) NULL,
  `avatar` VARCHAR(128) NULL,
  `sex` SMALLINT NULL,
  `phone` VARCHAR(32) NULL,
  `email` VARCHAR(32) NULL,
  `status` SMALLINT NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` SMALLINT NOT NULL,
  `name` VARCHAR(50) NULL,
  `parent_id` BIGINT NULL,
  `flag` SMALLINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_dept` (
  `dept_id` BIGINT NOT NULL,
  `dept_name` VARCHAR(50) NULL,
  `parent_id` BIGINT NOT NULL,
  `order` SMALLINT NOT NULL,
  `status` SMALLINT NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cos_id` VARCHAR(64) NULL,
  `file_name` VARCHAR(128) NULL,
  `file_suffix` VARCHAR(20) NULL,
  `file_type` SMALLINT NOT NULL,
  `file_path` VARCHAR(128) NULL,
  `file_size` VARCHAR(64) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_menu` (
  `menu_id` BIGINT NOT NULL AUTO_INCREMENT,
  `menu_name` VARCHAR(50) NULL,
  `parent_id` BIGINT NULL,
  `order_num` SMALLINT NULL,
  `path` VARCHAR(200) NULL,
  `component` VARCHAR(255) NULL,
  `is_frame` SMALLINT NULL,
  `menu_type` CHAR(1) NULL,
  `visible` SMALLINT NULL,
  `status` SMALLINT NULL,
  `perms` VARCHAR(100) NULL,
  `icon` VARCHAR(100) NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_oper_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(50) NULL,
  `business_type` SMALLINT NULL,
  `method` VARCHAR(100) NULL,
  `request_method` VARCHAR(10) NULL,
  `operator_type` SMALLINT NULL,
  `oper_name` VARCHAR(50) NULL,
  `oper_url` VARCHAR(255) NULL,
  `oper_ip` VARCHAR(128) NULL,
  `oper_location` VARCHAR(255) NULL,
  `oper_param` LONGTEXT NULL,
  `json_result` LONGTEXT NULL,
  `status` SMALLINT NULL,
  `error_msg` LONGTEXT NULL,
  `oper_time` DATETIME(6) NULL,
  `cost_time` SMALLINT NULL,
  `create_by` VARCHAR(255) NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` VARCHAR(255) NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_role` (
  `role_id` BIGINT NOT NULL,
  `role_name` VARCHAR(30) NULL,
  `role_key` VARCHAR(100) NULL,
  `role_sort` SMALLINT NOT NULL,
  `data_scope` SMALLINT NULL,
  `status` SMALLINT NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `menu_id` BIGINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_user` (
  `user_id` BIGINT NOT NULL,
  `user_name` VARCHAR(50) NULL,
  `nick_name` VARCHAR(50) NULL,
  `password` VARCHAR(64) NULL,
  `avatar` VARCHAR(128) NULL,
  `sex` SMALLINT NULL,
  `phone` VARCHAR(32) NULL,
  `email` VARCHAR(32) NULL,
  `status` SMALLINT NULL,
  `remark` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_user_dept` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `dept_id` BIGINT NOT NULL,
  `leader` SMALLINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `tts_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cache_key` VARCHAR(128) NULL,
  `text_digest` VARCHAR(128) NULL,
  `speech_text` LONGTEXT NULL,
  `provider` VARCHAR(32) NULL,
  `voice` VARCHAR(64) NULL,
  `params_json` LONGTEXT NULL,
  `file_name` VARCHAR(255) NULL,
  `file_path` VARCHAR(500) NULL,
  `listen_url` VARCHAR(500) NULL,
  `file_size` BIGINT NULL,
  `duration_ms` BIGINT NULL,
  `sample_rate` INT NULL,
  `format` VARCHAR(16) NULL,
  `status` VARCHAR(32) NULL,
  `error_code` VARCHAR(32) NULL,
  `error_msg` VARCHAR(500) NULL,
  `ref_count` INT NULL DEFAULT 0,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` SMALLINT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tts_file_cache_key` (`cache_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `tts_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` VARCHAR(128) NULL,
  `cache_key` VARCHAR(128) NULL,
  `speech_text` LONGTEXT NULL,
  `provider` VARCHAR(32) NULL,
  `voice` VARCHAR(64) NULL,
  `params_json` LONGTEXT NULL,
  `file_id` BIGINT NULL,
  `status` VARCHAR(32) NULL,
  `call_id` VARCHAR(64) NULL,
  `channel_uuid` VARCHAR(128) NULL,
  `error_code` VARCHAR(32) NULL,
  `error_msg` VARCHAR(500) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` SMALLINT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tts_task_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `version` (
  `id` BIGINT NOT NULL,
  `table_name` VARCHAR(32) NULL,
  `table_version` SMALLINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `voice_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NULL,
  `type` SMALLINT NOT NULL,
  `tts` SMALLINT NULL,
  `speech_text` VARCHAR(500) NULL,
  `file_id` BIGINT NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NOT NULL,
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL,
  `del_flag` SMALLINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `work_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(255) NOT NULL,
  `description` LONGTEXT NULL,
  `call_record_id` BIGINT NULL,
  `call_id` VARCHAR(64) NULL,
  `caller_number` VARCHAR(32) NULL,
  `callee_number` VARCHAR(32) NULL,
  `agent_id` BIGINT NULL,
  `agent_name` VARCHAR(64) NULL,
  `priority` INT NULL DEFAULT 2,
  `status` INT NULL DEFAULT 0,
  `handler_id` BIGINT NULL,
  `handler_name` VARCHAR(64) NULL,
  `handle_remark` LONGTEXT NULL,
  `finish_time` DATETIME(6) NULL,
  `create_by` BIGINT NULL,
  `create_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_by` BIGINT NULL,
  `update_time` DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  `del_flag` INT NULL DEFAULT 0,
  `customer_id` BIGINT NULL,
  `customer_name` VARCHAR(128) NULL,
  `mobile_1` VARCHAR(32) NULL,
  `mobile_2` VARCHAR(32) NULL,
  `fixed_phone` VARCHAR(32) NULL,
  `customer_address` VARCHAR(255) NULL,
  `customer_type` VARCHAR(64) NULL,
  `company_name` VARCHAR(255) NULL,
  `work_order_type` VARCHAR(64) NULL,
  `number_location` VARCHAR(128) NULL,
  `business_type` VARCHAR(128) NULL,
  `issue_type` VARCHAR(128) NULL,
  `acceptance_content` LONGTEXT NULL,
  `attachments_json` LONGTEXT NULL,
  `satisfaction` VARCHAR(32) NULL,
  `satisfaction_time` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_work_order_call_id` (`call_id`),
  KEY `idx_work_order_caller_number` (`caller_number`),
  KEY `idx_work_order_mobile_1` (`mobile_1`),
  KEY `idx_work_order_customer_name` (`customer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
