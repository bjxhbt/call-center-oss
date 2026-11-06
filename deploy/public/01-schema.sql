-- KingbaseES public schema only; no rows, owners, grants or comments.
-- Source backup SHA-256: 36c4ec9774b2215b9c4df914a465f48673e782dd4df94220e60c26649c05237d
-- ID columns and sequences widened to bigint for the current Java entities.
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', 'public', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;


SET escape = off;
CREATE TABLE public.call_display (
    id bigint NOT NULL,
    phone character varying(18 char),
    type smallint NOT NULL,
    area character varying(64 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_display_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_display_id_seq OWNED BY public.call_display.id;



SET escape = off;
CREATE TABLE public.call_display_pool (
    id bigint NOT NULL,
    name character varying(18 char),
    type smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    tenant_id bigint NOT NULL,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_display_pool_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_display_pool_id_seq OWNED BY public.call_display_pool.id;



SET escape = off;
CREATE TABLE public.call_display_pool_rel (
    id bigint NOT NULL,
    pool_id bigint NOT NULL,
    display_id bigint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_display_pool_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_display_pool_rel_id_seq OWNED BY public.call_display_pool_rel.id;



SET escape = off;
CREATE TABLE public.call_engine (
    id bigint NOT NULL,
    name character varying(50 char),
    profile character varying(128 char),
    type smallint NOT NULL,
    providers smallint NOT NULL,
    timbre character varying(200 char),
    grammar character varying(200 char),
    param character varying(200 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_engine_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_engine_id_seq OWNED BY public.call_engine.id;



SET escape = off;
CREATE TABLE public.call_holiday (
    id bigint NOT NULL,
    holiday_date character varying(10 char) NOT NULL,
    type integer NOT NULL,
    name character varying(100 char),
    create_by integer,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_by integer,
    update_time timestamp without time zone,
    del_flag integer DEFAULT 0
);



CREATE SEQUENCE public.call_holiday_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_holiday_id_seq OWNED BY public.call_holiday.id;



SET escape = off;
CREATE TABLE public.call_in_phone (
    id bigint NOT NULL,
    name character varying(128 char),
    phone character varying(32 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_in_phone_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_in_phone_id_seq OWNED BY public.call_in_phone.id;



SET escape = off;
CREATE TABLE public.call_in_phone_rel (
    id bigint NOT NULL,
    call_in_id bigint NOT NULL,
    schedule_id bigint NOT NULL,
    route_type smallint,
    route_value character varying(50 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_in_phone_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_in_phone_rel_id_seq OWNED BY public.call_in_phone_rel.id;



SET escape = off;
CREATE TABLE public.call_record (
    id bigint NOT NULL,
    call_id character varying(64 char),
    task_id bigint,
    customer_id bigint,
    caller_number character varying(32 char),
    caller_display_number character varying(32 char),
    callee_number character varying(32 char),
    callee_display_number character varying(32 char),
    number_location character varying(128 char),
    agent_id bigint,
    agent_number character varying(32 char),
    agent_name character varying(128 char),
    call_state smallint NOT NULL,
    direction smallint,
    call_start_time timestamp without time zone,
    call_end_time timestamp without time zone,
    answer_flag smallint NOT NULL,
    answer_time timestamp without time zone,
    ringing_time timestamp without time zone,
    hangup_dir smallint,
    hangup_cause_code smallint,
    file_path character varying(500 char),
    ringing_path character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL,
    record_url character varying(500 char),
    record_duration smallint,
    record_status smallint
);



CREATE SEQUENCE public.call_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_record_id_seq OWNED BY public.call_record.id;



SET escape = off;
CREATE TABLE public.call_route (
    id bigint NOT NULL,
    name character varying(64 char),
    route_num character varying(32 char),
    type smallint,
    "level" smallint,
    status smallint,
    schedule_id bigint,
    route_type smallint,
    route_value character varying(200 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_route_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_route_id_seq OWNED BY public.call_route.id;



SET escape = off;
CREATE TABLE public.call_schedule (
    id bigint NOT NULL,
    name character varying(128 char),
    priority_level smallint NOT NULL,
    type smallint NOT NULL,
    start_day character varying(50 char),
    end_day character varying(50 char),
    start_time character varying(50 char),
    end_time character varying(50 char),
    work_cycle character varying(20 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL,
    voice_text character varying(500 char),
    time_type integer DEFAULT 1,
    voice_file_id bigint,
    flow_id bigint
);



CREATE SEQUENCE public.call_schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_schedule_id_seq OWNED BY public.call_schedule.id;



SET escape = off;
CREATE TABLE public.call_skill (
    id bigint NOT NULL,
    group_id bigint,
    name character varying(128 char),
    priority smallint NOT NULL,
    describe character varying(128 char),
    strategy_type smallint NOT NULL,
    full_busy_type smallint,
    overflow_type smallint,
    overflow_value character varying(32 char),
    time_out smallint,
    queue_length smallint,
    queue_voice smallint,
    agent_voice smallint,
    caller_phone_pool smallint,
    callee_phone_pool smallint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.call_skill_agent_rel (
    id bigint NOT NULL,
    skill_id bigint,
    agent_id character varying(128 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_skill_agent_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_skill_agent_rel_id_seq OWNED BY public.call_skill_agent_rel.id;



CREATE SEQUENCE public.call_skill_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_skill_id_seq OWNED BY public.call_skill.id;



SET escape = off;
CREATE TABLE public.call_task (
    id bigint NOT NULL,
    name character varying(50 char),
    type smallint NOT NULL,
    status smallint NOT NULL,
    priority smallint,
    start_day timestamp without time zone NOT NULL,
    end_day timestamp without time zone NOT NULL,
    s_time character varying(50 char),
    e_time character varying(50 char),
    work_cycle character varying(20 char),
    assignment_type smallint,
    is_priority smallint,
    receive_limit smallint,
    agent_list json,
    complete_type smallint NOT NULL,
    phone_pool_id bigint NOT NULL,
    transfer_type smallint NOT NULL,
    transfer_value character varying(120 char),
    recall smallint NOT NULL,
    recall_num smallint,
    recall_time smallint,
    remark character varying(200 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.call_task_assignment (
    id bigint NOT NULL,
    task_id bigint NOT NULL,
    phone character varying(20 char),
    name character varying(50 char),
    sex smallint NOT NULL,
    ext json,
    source smallint NOT NULL,
    crowd_id bigint,
    template_id bigint,
    status smallint NOT NULL,
    agent_id bigint,
    assignment_time timestamp without time zone,
    call_status smallint NOT NULL,
    attempt_count smallint,
    scheduled_time timestamp without time zone,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.call_task_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_task_assignment_id_seq OWNED BY public.call_task_assignment.id;



CREATE SEQUENCE public.call_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.call_task_id_seq OWNED BY public.call_task.id;



SET escape = off;
CREATE TABLE public.customer_crowd (
    id bigint NOT NULL,
    name character varying(50 char),
    status smallint NOT NULL,
    type smallint,
    progress smallint,
    reason character varying(200 char),
    swipe json NOT NULL,
    crowd_num smallint,
    remark character varying(200 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.customer_crowd_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_crowd_id_seq OWNED BY public.customer_crowd.id;



SET escape = off;
CREATE TABLE public.customer_crowd_rel (
    id bigint NOT NULL,
    crowd_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.customer_crowd_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_crowd_rel_id_seq OWNED BY public.customer_crowd_rel.id;



SET escape = off;
CREATE TABLE public.customer_field (
    id bigint NOT NULL,
    field_label character varying(100 char),
    field_name character varying(100 char),
    status smallint NOT NULL,
    field_type smallint NOT NULL,
    required smallint,
    options json,
    sys_type smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.customer_field_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_field_id_seq OWNED BY public.customer_field.id;



SET escape = off;
CREATE TABLE public.customer_seas (
    id bigint NOT NULL,
    template_id bigint NOT NULL,
    third_id character varying(64 char),
    customer_info json,
    phone character varying(20 char),
    name character varying(50 char),
    sex smallint,
    source smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.customer_seas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_seas_id_seq OWNED BY public.customer_seas.id;



SET escape = off;
CREATE TABLE public.customer_template (
    id bigint NOT NULL,
    name character varying(200 char),
    status smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.customer_template_field_rel (
    id bigint NOT NULL,
    template_id bigint NOT NULL,
    field_id bigint NOT NULL,
    hidden smallint NOT NULL,
    sort smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.customer_template_field_rel_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_template_field_rel_id_seq OWNED BY public.customer_template_field_rel.id;



CREATE SEQUENCE public.customer_template_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.customer_template_id_seq OWNED BY public.customer_template.id;



SET escape = off;
CREATE TABLE public.flow_info (
    id bigint NOT NULL,
    group_id bigint NOT NULL,
    name character varying(255 char),
    flow_desc character varying(255 char),
    status smallint,
    flow_data text,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.flow_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.flow_info_id_seq OWNED BY public.flow_info.id;



SET escape = off;
CREATE TABLE public.flow_instances (
    id bigint NOT NULL,
    call_id bigint NOT NULL,
    flow_id bigint NOT NULL,
    status smallint,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    current_node_id character varying(64 char),
    variables text,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.flow_instances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.flow_instances_id_seq OWNED BY public.flow_instances.id;



SET escape = off;
CREATE TABLE public.flow_node_execution_history (
    id bigint NOT NULL,
    instance_id bigint NOT NULL,
    node_id character varying(64 char),
    status smallint NOT NULL,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    duration smallint,
    description character varying(1000 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.flow_node_execution_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.flow_node_execution_history_id_seq OWNED BY public.flow_node_execution_history.id;



SET escape = off;
CREATE TABLE public.fs_acl (
    id bigint NOT NULL,
    name character varying(50 char),
    default_type character varying(20 char),
    list_id bigint,
    node_type character varying(20 char),
    cidr character varying(64 char),
    domain character varying(64 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.fs_acl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.fs_acl_id_seq OWNED BY public.fs_acl.id;



SET escape = off;
CREATE TABLE public.fs_cdr (
    call_id bigint NOT NULL,
    uuid character varying(64 char),
    direction character varying(16 char),
    sip_local_network_addr character varying(32 char),
    sip_network_ip character varying(32 char),
    caller_id_number character varying(64 char),
    caller_display character varying(64 char),
    destination_number character varying(64 char),
    destination_display character varying(64 char),
    start_stamp timestamp without time zone,
    answer_stamp timestamp without time zone,
    end_stamp timestamp without time zone,
    bridge_stamp timestamp without time zone,
    progress_stamp timestamp without time zone,
    duration smallint,
    answer_sec smallint,
    bill_sec smallint,
    hangup_cause character varying(64 char),
    record_start_time timestamp without time zone,
    record_end_time timestamp without time zone,
    record_sec smallint,
    record character varying(128 char),
    read_codec character varying(64 char),
    write_codec character varying(64 char),
    sip_hangup_disposition character varying(64 char),
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.fs_config (
    id bigint NOT NULL,
    name character varying(128 char),
    group_name character varying(64 char),
    ip character varying(64 char),
    port smallint,
    user_name character varying(64 char),
    password character varying(64 char),
    status smallint,
    out_time smallint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.fs_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.fs_config_id_seq OWNED BY public.fs_config.id;



SET escape = off;
CREATE TABLE public.fs_dialplan (
    id bigint NOT NULL,
    group_id bigint NOT NULL,
    name character varying(128 char),
    type character varying(64 char),
    expression character varying(100 char),
    context_name character varying(32 char),
    content character varying(1000 char),
    describe character varying(256 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.fs_dialplan_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.fs_dialplan_id_seq OWNED BY public.fs_dialplan.id;



SET escape = off;
CREATE TABLE public.fs_modules (
    id bigint NOT NULL,
    fs_name character varying(128 char),
    name character varying(128 char),
    type character varying(64 char),
    content character varying(1000 char),
    describe character varying(256 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.fs_modules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.fs_modules_id_seq OWNED BY public.fs_modules.id;



SET escape = off;
CREATE TABLE public.fs_sip_gateway (
    id bigint NOT NULL,
    name character varying(50 char),
    user_name character varying(50 char),
    password character varying(64 char),
    realm character varying(32 char),
    proxy character varying(32 char),
    register smallint,
    transport smallint,
    caller_id_in_from smallint,
    from_domain character varying(32 char),
    retry_time smallint,
    ping_time smallint,
    expire_time smallint,
    type smallint,
    gateway_type smallint,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.fs_sip_gateway_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.fs_sip_gateway_id_seq OWNED BY public.fs_sip_gateway.id;



SET escape = off;
CREATE TABLE public.kb_category (
    id bigint NOT NULL,
    name character varying(100 char) NOT NULL,
    parent_id bigint DEFAULT 0,
    sort integer DEFAULT 0,
    flag integer DEFAULT 0,
    create_by bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_by bigint,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    del_flag integer DEFAULT 0
);



CREATE SEQUENCE public.kb_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.kb_category_id_seq OWNED BY public.kb_category.id;



SET escape = off;
CREATE TABLE public.kb_entry (
    id bigint NOT NULL,
    category_id bigint,
    title character varying(255 char) NOT NULL,
    content text,
    keywords character varying(500 char),
    status integer DEFAULT 1,
    create_by bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_by bigint,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    del_flag integer DEFAULT 0
);



CREATE SEQUENCE public.kb_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.kb_entry_id_seq OWNED BY public.kb_entry.id;



SET escape = off;
CREATE TABLE public.ko_acc (
    id bigint NOT NULL,
    method character varying(16 char),
    from_tag character varying(128 char),
    to_tag character varying(128 char),
    callid character varying(255 char),
    sip_code character varying(3 char),
    sip_reason character varying(128 char),
    "time" timestamp without time zone NOT NULL,
    src_user character varying(64 char),
    src_domain character varying(128 char),
    src_ip character varying(64 char),
    dst_ouser character varying(64 char),
    dst_user character varying(64 char),
    dst_domain character varying(128 char)
);



SET escape = off;
CREATE TABLE public.ko_address (
    id bigint NOT NULL,
    grp smallint NOT NULL,
    ip_addr character varying(50 char),
    mask smallint NOT NULL,
    port smallint NOT NULL,
    tag character varying(64 char)
);



SET escape = off;
CREATE TABLE public.ko_dispatcher (
    id bigint NOT NULL,
    setid smallint NOT NULL,
    destination character varying(192 char),
    flags smallint NOT NULL,
    priority smallint NOT NULL,
    attrs character varying(128 char),
    description character varying(64 char),
    status smallint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.ko_dispatcher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.ko_dispatcher_id_seq OWNED BY public.ko_dispatcher.id;



SET escape = off;
CREATE TABLE public.ko_location (
    id bigint NOT NULL,
    ruid character varying(64 char),
    username character varying(64 char),
    domain character varying(64 char),
    contact character varying(512 char),
    received character varying(128 char),
    path character varying(512 char),
    expires timestamp without time zone NOT NULL,
    q real NOT NULL,
    callid character varying(255 char),
    cseq smallint NOT NULL,
    last_modified timestamp without time zone NOT NULL,
    flags smallint NOT NULL,
    cflags smallint NOT NULL,
    user_agent character varying(255 char),
    socket character varying(64 char),
    methods smallint,
    instance character varying(255 char),
    reg_id bigint NOT NULL,
    server_id bigint NOT NULL,
    connection_id bigint NOT NULL,
    keepalive smallint NOT NULL,
    partition smallint NOT NULL
);



SET escape = off;
CREATE TABLE public.ko_subscriber (
    id bigint NOT NULL,
    username character varying(64 char),
    domain character varying(64 char),
    password character varying(64 char),
    ha1 character varying(128 char),
    ha1b character varying(128 char),
    vmpin character varying(8 char),
    status smallint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone
);



CREATE SEQUENCE public.ko_subscriber_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.ko_subscriber_id_seq OWNED BY public.ko_subscriber.id;



SET escape = off;
CREATE TABLE public.och_area_code (
    id bigint NOT NULL,
    province_name character varying(100 char),
    city_name character varying(100 char),
    province_adcode smallint,
    province_center character varying(100 char),
    city_code character varying(6 char),
    city_adcode smallint,
    city_center character varying(100 char),
    districts character varying(1000 char),
    iso_code character varying(20 char)
);



CREATE SEQUENCE public.och_area_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.och_area_code_id_seq OWNED BY public.och_area_code.id;



SET escape = off;
CREATE TABLE public.och_region (
    region_id character varying(11 char),
    region_name character varying(255 char),
    parent_id character varying(255 char),
    short_name character varying(255 char),
    "level" character varying(255 char),
    city_code character varying(255 char),
    zip_code character varying(255 char),
    merger_name character varying(255 char),
    lng character varying(255 char),
    lat character varying(255 char),
    pinyin character varying(255 char)
);



SET escape = off;
CREATE TABLE public.phone_location (
    id bigint NOT NULL,
    pref character varying(10 char),
    phone character varying(20 char),
    province character varying(45 char),
    city character varying(45 char),
    isp character varying(45 char),
    isp_type smallint NOT NULL,
    post_code character varying(100 char),
    city_code character varying(10 char),
    area_code character varying(100 char),
    create_time timestamp without time zone NOT NULL
);



CREATE SEQUENCE public.phone_location_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.phone_location_id_seq OWNED BY public.phone_location.id;



SET escape = off;
CREATE TABLE public.qrtz_blob_triggers (
    sched_name character varying(120 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    blob_data blob
);



SET escape = off;
CREATE TABLE public.qrtz_calendars (
    sched_name character varying(120 char),
    calendar_name character varying(190 char),
    calendar blob NOT NULL
);



SET escape = off;
CREATE TABLE public.qrtz_cron_triggers (
    sched_name character varying(120 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    cron_expression character varying(120 char),
    time_zone_id character varying(80 char)
);



SET escape = off;
CREATE TABLE public.qrtz_fired_triggers (
    sched_name character varying(120 char),
    entry_id character varying(95 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    instance_name character varying(190 char),
    fired_time bigint NOT NULL,
    sched_time bigint NOT NULL,
    priority bigint NOT NULL,
    state character varying(16 char),
    job_name character varying(190 char),
    job_group character varying(190 char),
    is_nonconcurrent character varying(1 char),
    requests_recovery character varying(1 char)
);



SET escape = off;
CREATE TABLE public.qrtz_job_details (
    sched_name character varying(120 char),
    job_name character varying(190 char),
    job_group character varying(190 char),
    description character varying(250 char),
    job_class_name character varying(250 char),
    is_durable character varying(1 char),
    is_nonconcurrent character varying(1 char),
    is_update_data character varying(1 char),
    requests_recovery character varying(1 char),
    job_data blob
);



SET escape = off;
CREATE TABLE public.qrtz_locks (
    sched_name character varying(120 char),
    lock_name character varying(40 char)
);



SET escape = off;
CREATE TABLE public.qrtz_paused_trigger_grps (
    sched_name character varying(120 char),
    trigger_group character varying(190 char)
);



SET escape = off;
CREATE TABLE public.qrtz_scheduler_state (
    sched_name character varying(120 char),
    instance_name character varying(190 char),
    last_checkin_time bigint NOT NULL,
    checkin_interval bigint NOT NULL
);



SET escape = off;
CREATE TABLE public.qrtz_simple_triggers (
    sched_name character varying(120 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    repeat_count bigint NOT NULL,
    repeat_interval bigint NOT NULL,
    times_triggered bigint NOT NULL
);



SET escape = off;
CREATE TABLE public.qrtz_simprop_triggers (
    sched_name character varying(120 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    str_prop_1 character varying(512 char),
    str_prop_2 character varying(512 char),
    str_prop_3 character varying(512 char),
    int_prop_1 bigint,
    int_prop_2 bigint,
    long_prop_1 bigint,
    long_prop_2 bigint,
    dec_prop_1 real,
    dec_prop_2 real,
    bool_prop_1 character varying(1 char),
    bool_prop_2 character varying(1 char)
);



SET escape = off;
CREATE TABLE public.qrtz_triggers (
    sched_name character varying(120 char),
    trigger_name character varying(190 char),
    trigger_group character varying(190 char),
    job_name character varying(190 char),
    job_group character varying(190 char),
    description character varying(250 char),
    next_fire_time bigint,
    prev_fire_time bigint,
    priority bigint,
    trigger_state character varying(16 char),
    trigger_type character varying(8 char),
    start_time bigint NOT NULL,
    end_time bigint,
    calendar_name character varying(190 char),
    misfire_instr bigint,
    job_data blob
);



SET escape = off;
CREATE TABLE public.sip_agent (
    id bigint NOT NULL,
    name character varying(128 char),
    user_id bigint NOT NULL,
    agent_number character varying(64 char),
    status smallint NOT NULL,
    online_status smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sip_agent_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sip_agent_id_seq OWNED BY public.sip_agent.id;



SET escape = off;
CREATE TABLE public.sys_app_role (
    role_id bigint NOT NULL,
    role_name character varying(30 char),
    role_key character varying(100 char),
    role_sort smallint NOT NULL,
    data_scope smallint,
    status smallint,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint NOT NULL
);



CREATE SEQUENCE public.sys_app_role_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_app_role_role_id_seq OWNED BY public.sys_app_role.role_id;



SET escape = off;
CREATE TABLE public.sys_app_user (
    user_id bigint NOT NULL,
    user_name character varying(50 char),
    nick_name character varying(50 char),
    password character varying(64 char),
    avatar character varying(128 char),
    sex smallint,
    phone character varying(32 char),
    email character varying(32 char),
    status smallint,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint NOT NULL
);



CREATE SEQUENCE public.sys_app_user_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_app_user_user_id_seq OWNED BY public.sys_app_user.user_id;



SET escape = off;
CREATE TABLE public.sys_category (
    id bigint NOT NULL,
    type smallint NOT NULL,
    name character varying(50 char),
    parent_id bigint,
    flag smallint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_category_id_seq OWNED BY public.sys_category.id;



SET escape = off;
CREATE TABLE public.sys_dept (
    dept_id bigint NOT NULL,
    dept_name character varying(50 char),
    parent_id bigint NOT NULL,
    "order" smallint NOT NULL,
    status smallint,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.sys_file (
    id bigint NOT NULL,
    cos_id character varying(64 char),
    file_name character varying(128 char),
    file_suffix character varying(20 char),
    file_type smallint NOT NULL,
    file_path character varying(128 char),
    file_size character varying(64 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_file_id_seq OWNED BY public.sys_file.id;



SET escape = off;
CREATE TABLE public.sys_menu (
    menu_id bigint NOT NULL,
    menu_name character varying(50 char),
    parent_id bigint,
    order_num smallint,
    path character varying(200 char),
    component character varying(255 char),
    is_frame smallint,
    menu_type character(1 char),
    visible smallint,
    status smallint,
    perms character varying(100 char),
    icon character varying(100 char),
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_menu_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_menu_menu_id_seq OWNED BY public.sys_menu.menu_id;



SET escape = off;
CREATE TABLE public.sys_oper_log (
    id bigint NOT NULL,
    title character varying(50 char),
    business_type smallint,
    method character varying(100 char),
    request_method character varying(10 char),
    operator_type smallint,
    oper_name character varying(50 char),
    oper_url character varying(255 char),
    oper_ip character varying(128 char),
    oper_location character varying(255 char),
    oper_param text,
    json_result text,
    status smallint,
    error_msg text,
    oper_time timestamp without time zone,
    cost_time smallint,
    create_by character varying(255 char),
    create_time timestamp without time zone NOT NULL,
    update_by character varying(255 char),
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_oper_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_oper_log_id_seq OWNED BY public.sys_oper_log.id;



SET escape = off;
CREATE TABLE public.sys_role (
    role_id bigint NOT NULL,
    role_name character varying(30 char),
    role_key character varying(100 char),
    role_sort tinyint NOT NULL,
    data_scope tinyint,
    status tinyint,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag tinyint NOT NULL
);



SET escape = off;
CREATE TABLE public.sys_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_role_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_role_menu_id_seq OWNED BY public.sys_role_menu.id;



SET escape = off;
CREATE TABLE public.sys_user (
    user_id bigint NOT NULL,
    user_name character varying(50 char),
    nick_name character varying(50 char),
    password character varying(64 char),
    avatar character varying(128 char),
    sex tinyint,
    phone character varying(32 char),
    email character varying(32 char),
    status tinyint,
    remark character varying(500 char),
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag tinyint NOT NULL
);



SET escape = off;
CREATE TABLE public.sys_user_dept (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    dept_id bigint NOT NULL,
    leader smallint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



SET escape = off;
CREATE TABLE public.sys_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    create_by bigint,
    create_time timestamp without time zone,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.sys_user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.sys_user_role_id_seq OWNED BY public.sys_user_role.id;



SET escape = off;
CREATE TABLE public.version (
    id bigint NOT NULL,
    table_name character varying(32 char),
    table_version smallint NOT NULL
);



SET escape = off;
CREATE TABLE public.voice_file (
    id bigint NOT NULL,
    name character varying(50 char),
    type smallint NOT NULL,
    tts smallint,
    speech_text character varying(500 char),
    file_id bigint,
    create_by bigint,
    create_time timestamp without time zone NOT NULL,
    update_by bigint,
    update_time timestamp without time zone,
    del_flag smallint DEFAULT 0 NOT NULL
);



CREATE SEQUENCE public.voice_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.voice_file_id_seq OWNED BY public.voice_file.id;



SET escape = off;
CREATE TABLE public.work_order (
    id bigint NOT NULL,
    title character varying(255 char) NOT NULL,
    description text,
    call_record_id bigint,
    call_id character varying(64 char),
    caller_number character varying(32 char),
    callee_number character varying(32 char),
    agent_id bigint,
    agent_name character varying(64 char),
    priority integer DEFAULT 2,
    status integer DEFAULT 0,
    handler_id bigint,
    handler_name character varying(64 char),
    handle_remark text,
    finish_time timestamp without time zone,
    create_by bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_by bigint,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    del_flag integer DEFAULT 0
);



CREATE SEQUENCE public.work_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



ALTER SEQUENCE public.work_order_id_seq OWNED BY public.work_order.id;



ALTER TABLE ONLY public.call_display ALTER COLUMN id SET DEFAULT nextval('public.call_display_id_seq'::regclass);



ALTER TABLE ONLY public.call_display_pool ALTER COLUMN id SET DEFAULT nextval('public.call_display_pool_id_seq'::regclass);



ALTER TABLE ONLY public.call_display_pool_rel ALTER COLUMN id SET DEFAULT nextval('public.call_display_pool_rel_id_seq'::regclass);



ALTER TABLE ONLY public.call_engine ALTER COLUMN id SET DEFAULT nextval('public.call_engine_id_seq'::regclass);



ALTER TABLE ONLY public.call_holiday ALTER COLUMN id SET DEFAULT nextval('public.call_holiday_id_seq'::regclass);



ALTER TABLE ONLY public.call_in_phone ALTER COLUMN id SET DEFAULT nextval('public.call_in_phone_id_seq'::regclass);



ALTER TABLE ONLY public.call_in_phone_rel ALTER COLUMN id SET DEFAULT nextval('public.call_in_phone_rel_id_seq'::regclass);



ALTER TABLE ONLY public.call_record ALTER COLUMN id SET DEFAULT nextval('public.call_record_id_seq'::regclass);



ALTER TABLE ONLY public.call_route ALTER COLUMN id SET DEFAULT nextval('public.call_route_id_seq'::regclass);



ALTER TABLE ONLY public.call_schedule ALTER COLUMN id SET DEFAULT nextval('public.call_schedule_id_seq'::regclass);



ALTER TABLE ONLY public.call_skill ALTER COLUMN id SET DEFAULT nextval('public.call_skill_id_seq'::regclass);



ALTER TABLE ONLY public.call_skill_agent_rel ALTER COLUMN id SET DEFAULT nextval('public.call_skill_agent_rel_id_seq'::regclass);



ALTER TABLE ONLY public.call_task ALTER COLUMN id SET DEFAULT nextval('public.call_task_id_seq'::regclass);



ALTER TABLE ONLY public.call_task_assignment ALTER COLUMN id SET DEFAULT nextval('public.call_task_assignment_id_seq'::regclass);



ALTER TABLE ONLY public.customer_crowd ALTER COLUMN id SET DEFAULT nextval('public.customer_crowd_id_seq'::regclass);



ALTER TABLE ONLY public.customer_crowd_rel ALTER COLUMN id SET DEFAULT nextval('public.customer_crowd_rel_id_seq'::regclass);



ALTER TABLE ONLY public.customer_field ALTER COLUMN id SET DEFAULT nextval('public.customer_field_id_seq'::regclass);



ALTER TABLE ONLY public.customer_seas ALTER COLUMN id SET DEFAULT nextval('public.customer_seas_id_seq'::regclass);



ALTER TABLE ONLY public.customer_template ALTER COLUMN id SET DEFAULT nextval('public.customer_template_id_seq'::regclass);



ALTER TABLE ONLY public.customer_template_field_rel ALTER COLUMN id SET DEFAULT nextval('public.customer_template_field_rel_id_seq'::regclass);



ALTER TABLE ONLY public.flow_info ALTER COLUMN id SET DEFAULT nextval('public.flow_info_id_seq'::regclass);



ALTER TABLE ONLY public.flow_instances ALTER COLUMN id SET DEFAULT nextval('public.flow_instances_id_seq'::regclass);



ALTER TABLE ONLY public.flow_node_execution_history ALTER COLUMN id SET DEFAULT nextval('public.flow_node_execution_history_id_seq'::regclass);



ALTER TABLE ONLY public.fs_acl ALTER COLUMN id SET DEFAULT nextval('public.fs_acl_id_seq'::regclass);



ALTER TABLE ONLY public.fs_config ALTER COLUMN id SET DEFAULT nextval('public.fs_config_id_seq'::regclass);



ALTER TABLE ONLY public.fs_dialplan ALTER COLUMN id SET DEFAULT nextval('public.fs_dialplan_id_seq'::regclass);



ALTER TABLE ONLY public.fs_modules ALTER COLUMN id SET DEFAULT nextval('public.fs_modules_id_seq'::regclass);



ALTER TABLE ONLY public.fs_sip_gateway ALTER COLUMN id SET DEFAULT nextval('public.fs_sip_gateway_id_seq'::regclass);



ALTER TABLE ONLY public.kb_category ALTER COLUMN id SET DEFAULT nextval('public.kb_category_id_seq'::regclass);



ALTER TABLE ONLY public.kb_entry ALTER COLUMN id SET DEFAULT nextval('public.kb_entry_id_seq'::regclass);



ALTER TABLE ONLY public.ko_dispatcher ALTER COLUMN id SET DEFAULT nextval('public.ko_dispatcher_id_seq'::regclass);



ALTER TABLE ONLY public.ko_subscriber ALTER COLUMN id SET DEFAULT nextval('public.ko_subscriber_id_seq'::regclass);



ALTER TABLE ONLY public.och_area_code ALTER COLUMN id SET DEFAULT nextval('public.och_area_code_id_seq'::regclass);



ALTER TABLE ONLY public.phone_location ALTER COLUMN id SET DEFAULT nextval('public.phone_location_id_seq'::regclass);



ALTER TABLE ONLY public.sip_agent ALTER COLUMN id SET DEFAULT nextval('public.sip_agent_id_seq'::regclass);



ALTER TABLE ONLY public.sys_app_role ALTER COLUMN role_id SET DEFAULT nextval('public.sys_app_role_role_id_seq'::regclass);



ALTER TABLE ONLY public.sys_app_user ALTER COLUMN user_id SET DEFAULT nextval('public.sys_app_user_user_id_seq'::regclass);



ALTER TABLE ONLY public.sys_category ALTER COLUMN id SET DEFAULT nextval('public.sys_category_id_seq'::regclass);



ALTER TABLE ONLY public.sys_file ALTER COLUMN id SET DEFAULT nextval('public.sys_file_id_seq'::regclass);



ALTER TABLE ONLY public.sys_menu ALTER COLUMN menu_id SET DEFAULT nextval('public.sys_menu_menu_id_seq'::regclass);



ALTER TABLE ONLY public.sys_oper_log ALTER COLUMN id SET DEFAULT nextval('public.sys_oper_log_id_seq'::regclass);



ALTER TABLE ONLY public.sys_role_menu ALTER COLUMN id SET DEFAULT nextval('public.sys_role_menu_id_seq'::regclass);



ALTER TABLE ONLY public.sys_user_role ALTER COLUMN id SET DEFAULT nextval('public.sys_user_role_id_seq'::regclass);



ALTER TABLE ONLY public.voice_file ALTER COLUMN id SET DEFAULT nextval('public.voice_file_id_seq'::regclass);



ALTER TABLE ONLY public.work_order ALTER COLUMN id SET DEFAULT nextval('public.work_order_id_seq'::regclass);



ALTER TABLE ONLY public.call_holiday
    ADD CONSTRAINT call_holiday_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.kb_category
    ADD CONSTRAINT kb_category_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.kb_entry
    ADD CONSTRAINT kb_entry_pkey PRIMARY KEY (id);



ALTER TABLE ONLY public.work_order
    ADD CONSTRAINT work_order_pkey PRIMARY KEY (id);



CREATE INDEX idx_call_holiday_date ON public.call_holiday USING btree (holiday_date);



CREATE INDEX idx_kb_category_parent_id ON public.kb_category USING btree (parent_id);



CREATE INDEX idx_kb_entry_category_id ON public.kb_entry USING btree (category_id);



CREATE INDEX idx_kb_entry_title ON public.kb_entry USING btree (title);
